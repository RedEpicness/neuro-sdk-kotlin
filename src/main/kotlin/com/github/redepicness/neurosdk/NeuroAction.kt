package com.github.redepicness.neurosdk

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class NeuroAction<T>(
    val name: String,
    val description: String,
    val serializer: KSerializer<T>? = null,
) {

    protected val logger: Logger = LoggerFactory.getLogger("Action: $name")

    internal val schema by lazy { serializer?.descriptor?.jsonSchemaProperty(limitedResponseResolver = ::limitedResponseResolver) }

    abstract fun validate(data: T): String?

    abstract fun successMessage(data: T): String

    abstract suspend fun process(data: T)

    open fun limitedResponseResolver(serialName: String): List<String> {
        return emptyList()
    }

    internal fun deserialize(string: String): T? {
        return try {
            Json.decodeFromString(serializer!!, string)
        } catch (e: Exception) {
            logger.warn("Could not deserialize: $string", e)
            null
        }
    }

    internal operator fun component1() = name

    internal operator fun component2() = description

    internal operator fun component3() = schema

}
