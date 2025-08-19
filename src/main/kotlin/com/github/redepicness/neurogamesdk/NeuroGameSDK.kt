package com.github.redepicness.neurogamesdk

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.util.collections.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class NeuroGameSDK(
    val game: String,
    webSocketURL: String,
    webSocketConfig: WebSockets.Config.() -> Unit = {},
    httpClientConfig: HttpClientConfig<CIOEngineConfig>.() -> Unit = {},
) {
    private val logger = LoggerFactory.getLogger("$game Neuro SDK")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json
    internal var url = webSocketURL

    private val registeredActions = ConcurrentHashMap<String, NeuroAction<*>>()
    private val forcedActions = ConcurrentSet<String>()
    private var forcedActionCallback: (suspend (NeuroAction<*>) -> Unit)? = null

    private val socketManager = SocketManager(this, webSocketConfig, httpClientConfig)

    fun getRegisteredActions(): Map<String, NeuroAction<*>> = registeredActions

    fun getForcedActions(): Set<String> = forcedActions

    fun sendContext(message: String, silent: Boolean) = scope.launch { sendContextSuspend(message, silent) }

    fun registerActions(vararg actions: NeuroAction<*>) = registerActions(actions.toList())

    fun registerActions(actions: Collection<NeuroAction<*>>) = scope.launch { registerActionsSuspend(actions) }

    fun unregisterActions(vararg actions: String) = unregisterActions(actions.toList())

    fun unregisterActions(actions: Collection<String>) = scope.launch { unregisterActionsSuspend(actions) }

    fun forceAction(
        state: String?,
        query: String,
        ephemeral: Boolean,
        actions: Collection<NeuroAction<*>>,
        callback: (suspend (NeuroAction<*>) -> Unit)? = null
    ) =
        scope.launch { forceActionSuspend(state, query, ephemeral, actions, callback) }

    suspend fun sendContextSuspend(message: String, silent: Boolean) {
        logger.info("Sending${if (silent) " silent " else " "}context: $message")
        socketManager.send(NeuroMessage.context(message, silent))
    }

    suspend fun registerActionsSuspend(actions: Collection<NeuroAction<*>>) {
        if (actions.isEmpty()) return
        // TODO prevent double registrations?
        logger.info("Registering actions: ${actions.joinToString(", ") { it.name }}")
        socketManager.send(
            NeuroMessage.registerActions(
                actions.map { NeuroMessage.Action(it.name, it.description, it.schema) }
            )
        )
        registeredActions.putAll(actions.associateBy { it.name })
    }

    suspend fun unregisterActionsSuspend(actions: Collection<String>) {
        logger.info("Unregistering actions: ${actions.joinToString(", ")}")
        socketManager.send(NeuroMessage.unregisterActions(actions.toList()))
        for (a in actions) {
            registeredActions.remove(a)
        }
    }

    suspend fun forceActionSuspend(
        state: String?,
        query: String,
        ephemeral: Boolean,
        actions: Collection<NeuroAction<*>>,
        callback: (suspend (NeuroAction<*>) -> Unit)? = null
    ) {
        if (actions.isEmpty()) return
        if (forcedActions.isNotEmpty()) {
            logger.warn("Attempted to send force action while already waiting on another force action! (${forcedActions.joinToString(", ")})")
            logger.warn("Requested force action: '$query': ${actions.joinToString(", ") { it.name }}")
            return
        }
        logger.info("Sending forced action: '$query': ${actions.joinToString(", ") { it.name }}")
        forcedActions.addAll(actions.map { it.name })
        forcedActionCallback = callback
        registerActionsSuspend(actions)
        socketManager.send(NeuroMessage.forceActions(state, query, ephemeral, actions.map { it.name }))
    }

    fun start() {
        scope.launch {
            startSuspend()
        }
    }

    suspend fun startSuspend() {
        socketManager.start()
    }

    fun reconnect() {
        scope.launch {
            socketManager.reconnect()
        }
    }

    fun shutdown() {
        runBlocking {
            socketManager.closeNormally()
        }
        scope.cancel()
    }

    internal suspend fun processCommand(msg: NeuroMessage): NeuroMessage? {
        val (command, _, msgData) = msg
        when (command) {
            "action" -> {
                if (msgData == null) return null
                return processAction(json.decodeFromJsonElement<NeuroMessage.ActionExecute>(msgData))
            }
        }
        return null
    }

    private suspend fun processAction(actionMessage: NeuroMessage.ActionExecute): NeuroMessage {
        val (id, name, data) = actionMessage
        logger.info("Processing action: $name ($id)")
        @Suppress("UNCHECKED_CAST")
        val action: NeuroAction<Any> = registeredActions[name] as? NeuroAction<Any>
            ?: return NeuroMessage.actionResult(id, false, "Action '$name' not found!")
                .also { logger.warn("Unsuccessful: Action '$name' not found!") }
        val obj: Any
        if (action is NeuroActionWithoutResponse) {
            obj = Unit
        } else {
            if (data == null) {
                logger.warn("Unsuccessful: Missing data field for action '$name'!")
                return NeuroMessage.actionResult(id, false, "Missing data field for action '$name'!")
            }
            obj = action.deserialize(data)
                ?: return NeuroMessage.actionResult(id, false, "Could not deserialize data!")
                    .also { logger.warn("Unsuccessful: Could not deserialize data!") }
        }
        val errorMessage = action.validate(obj)
        if (errorMessage != null) {
            logger.warn("Unsuccessful: Failed validation: $errorMessage")
            return NeuroMessage.actionResult(id, false, errorMessage)
        }
        scope.launch {
            action.process(obj)
        }
        if (forcedActions.contains(name)) {
            logger.info("Resolved forced action: $name")
            unregisterActionsSuspend(forcedActions)
            forcedActions.clear()
            forcedActionCallback?.invoke(action)
            forcedActionCallback = null
        }
        return NeuroMessage.actionResult(id, true, action.successMessage(obj))
    }

}
