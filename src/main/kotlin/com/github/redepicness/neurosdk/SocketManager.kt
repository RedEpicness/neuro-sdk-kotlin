package com.github.redepicness.neurosdk

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import com.github.redepicness.neurosdk.SocketManager.CloseReason.ERROR
import com.github.redepicness.neurosdk.SocketManager.CloseReason.INVALID_URL
import com.github.redepicness.neurosdk.SocketManager.CloseReason.NORMAL
import com.github.redepicness.neurosdk.SocketManager.CloseReason.RECONNECT
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds
import io.ktor.websocket.CloseReason as WSCloseReason

internal class SocketManager(
    val sdk: NeuroGameSDK,
    webSocketConfig: WebSockets.Config.() -> Unit = {},
    httpClientConfig: HttpClientConfig<CIOEngineConfig>.() -> Unit = {},
) {

    private val client = HttpClient(CIO) {
        install(WebSockets) {
            pingInterval = 5.seconds
            maxFrameSize = Long.MAX_VALUE
            webSocketConfig()
        }
        httpClientConfig()
    }

    private val json = Json
    private val logger = LoggerFactory.getLogger("Socket Manager (${sdk.game})")
    private val outgoingChannel = Channel<NeuroMessage>(Channel.UNLIMITED)
    private var websocket: DefaultClientWebSocketSession? = null
    private var sendJob: Job? = null
    private var receiveJob: Job? = null

    var closeInfo: CloseInfo? = null

    val closeMessage: String
        get() = closeInfo?.message ?: "No close reason provided."

    val isErrored: Boolean
        get() = closeInfo?.reason == ERROR

    val isClosed: Boolean
        get() = closeInfo != null

    suspend fun start() {
        while (true) {
            while (!sdk.url.startsWith("ws://")) {
                closeInfo = CloseInfo(INVALID_URL, "Invalid URL: ${sdk.url}")
                delay(1.seconds)
            }
            closeInfo = null
            try {
                client.webSocket(sdk.url) {
                    websocket = this
                    // Startup message
                    logger.info("Sending startup message for '${sdk.game}'.")
                    send(NeuroMessage.startup(sdk.game).toFrame())
                    val actions = sdk.getRegisteredActions().values.map { (name, description, schema) ->
                        NeuroMessage.Action(name, description, schema)
                    }
                    // Resend previously registered actions
                    if (actions.isNotEmpty()) {
                        logger.info("Previously registered actions found, re-registering: ${actions.joinToString(", ") { it.name }}")
                        send(NeuroMessage.registerActions(actions, sdk.game).toFrame())
                    }
                    // TODO Resend forced action?
                    // Job for receiving
                    val receiveJobLocal = launchWithErrorHandling("Receive") {
                        incoming.receiveAsFlow().cancellable().filterIsInstance<Frame.Text>().collect {
                            val text = it.text().also { txt -> logger.debug("Received: $txt") } ?: return@collect
                            try {
                                val message = json.decodeFromString<NeuroMessage>(text)
                                val response = sdk.processCommand(message) ?: return@collect
                                outgoingChannel.send(response)
                            } catch (e: Exception) {
                                logger.warn("Error while processing message: $text", e)
                            }
                        }
                    }
                    receiveJob = receiveJobLocal
                    val sendJobLocal = launchWithErrorHandling("Send") {
                        outgoingChannel.receiveAsFlow().cancellable().collect { msg -> send(msg.copy(game = sdk.game).toFrame()) }
                    }
                    sendJob = sendJobLocal

                    joinAll(sendJobLocal, receiveJobLocal)
                }
            } catch (e: Exception) {
                closeWithError("Websocket errored!", e)
            } finally {
                receiveJob = null
                sendJob = null
                websocket = null
            }
            val msg = when (closeInfo?.reason) {
                NORMAL -> {
                    logger.info("Websocket closed normally")
                    break
                }

                RECONNECT -> "Websocket closed for reconnect."
                INVALID_URL -> "Websocket could not connect due to invalid url!"
                ERROR -> "Websocket closed due to error!"
                null -> "Websocket without any information?"
            }
            logger.warn("$msg Reconnecting in 2 seconds...")
            delay(2.seconds)
        }
    }

    suspend fun send(msg: NeuroMessage) {
        outgoingChannel.send(msg)
    }

    suspend fun closeNormally(msg: String = "Shutting down!") = closeWebsocket(CloseInfo(NORMAL, msg))

    suspend fun reconnect(msg: String = "Reconnecting!") = closeWebsocket(CloseInfo(RECONNECT, msg))

    suspend fun closeWithError(msg: String, cause: Throwable? = null) = closeWebsocket(CloseInfo(ERROR, msg, cause))

    private suspend fun closeWebsocket(info: CloseInfo) {
        if (isClosed) return
        closeInfo = info
        val (reason, message, cause) = info
        logger.info("Closing websocket: $reason - $message")
        if (cause != null) {
            logger.warn("Websocket close cause:", cause)
        }
        sendJob?.cancel()
        receiveJob?.cancel()
        websocket?.close(WSCloseReason(reason.code, message))
    }

    private fun DefaultClientWebSocketSession.launchWithErrorHandling(name: String, block: suspend () -> Unit) = launch {
        try {
            block()
            if (closeInfo != null) {
                closeWithError("$name job stopped unexpectedly...")
            }
        } catch (e: Exception) {
            closeWithError("$name job errored!", e)
        }
    }

    data class CloseInfo(
        val reason: CloseReason,
        val message: String,
        val cause: Throwable? = null
    )

    enum class CloseReason(val code: Short = 0) {
        NORMAL(WSCloseReason.Codes.NORMAL.code),
        RECONNECT(WSCloseReason.Codes.SERVICE_RESTART.code),
        INVALID_URL,
        ERROR(WSCloseReason.Codes.INTERNAL_ERROR.code),
    }

    companion object {
        private val NO_ERROR = Throwable("No error")

        private fun Frame.text(): String? {
            return (this as? Frame.Text)?.readText()?.trim()
        }
    }

}
