package com.github.redepicness.neurosdk

import io.ktor.websocket.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject


@Serializable
internal data class NeuroMessage(
    val command: String,
    var game: String? = null, // Will be filled in automatically when sending
    val data: JsonObject? = null,
) {

    fun toFrame(): Frame.Text {
        return Frame.Text(json.encodeToString(this))
    }

    @Serializable
    data class Context(
        val message: String,
        val silent: Boolean,
    )

    @Serializable
    data class Action(
        val name: String,
        val description: String,
        val schema: JsonSchemaProperty? = null,
    )

    @Serializable
    data class ActionResult(
        val id: String,
        val success: Boolean,
        val message: String? = null,
    )

    @Serializable
    data class RegisterActions(
        val actions: List<Action>,
    )

    @Serializable
    data class UnregisterActions(
        @SerialName("action_names") val actionNames: List<String>,
    )

    @Serializable
    data class ForceActions(
        val state: String?,
        val query: String,
        @SerialName("ephemeral_context") val ephemeralContext: Boolean,
        @SerialName("action_names") val actionNames: List<String>,
    )

    @Serializable
    data class ActionExecute(
        val id: String,
        val name: String,
        val data: String? = null,
    )

    companion object {
        private val json = Json

        inline fun <reified T> message(command: String, name: String? = null, data: T) =
            NeuroMessage(command, name, json.encodeToJsonElement(data).jsonObject)

        fun startup(name: String? = null) = NeuroMessage("startup", name)

        fun context(message: String, silent: Boolean, name: String? = null) =
            message("context", name, Context(message, silent))

        fun registerActions(actions: List<Action>, name: String? = null) =
            message("actions/register", name, RegisterActions(actions))

        fun unregisterActions(actions: List<String>, name: String? = null) =
            message("actions/unregister", name, UnregisterActions(actions))

        fun forceActions(state: String?, query: String, ephemeralContext: Boolean, actionNames: List<String>, name: String? = null) =
            message("actions/force", name, ForceActions(state, query, ephemeralContext, actionNames))

        fun actionResult(id: String, success: Boolean, message: String? = null, name: String? = null) =
            message("action/result", name, ActionResult(id, success, message))

    }
}

