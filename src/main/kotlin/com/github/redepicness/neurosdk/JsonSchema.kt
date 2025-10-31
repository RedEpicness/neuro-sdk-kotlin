package com.github.redepicness.neurosdk

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
@Serializable
internal sealed interface JsonSchemaProperty {

    val type: Type

    val description: String?

    @Serializable
    @SerialName("object")
    open class Object(
        override val description: String? = null,
        val required: List<String> = emptyList(),
        val properties: Map<String, JsonSchemaProperty> = emptyMap(),
    ) : JsonSchemaProperty {

        @Transient
        override val type: Type = Type.OBJECT

    }

    @Serializable
    @SerialName("string")
    open class Str(
        override val description: String? = null,
        val enum: List<String>? = null, // This is kind of a hack, but it works and is up to spec
    ) : JsonSchemaProperty {

        @Transient
        override val type: Type = Type.STRING

    }

    @Serializable
    @SerialName("number")
    open class Number(
        override val description: String? = null,
    ) : JsonSchemaProperty {

        @Transient
        override val type: Type = Type.NUMBER

    }

    @Serializable
    @SerialName("integer")
    open class Integer(
        override val description: String? = null,
    ) : JsonSchemaProperty {

        @Transient
        override val type: Type = Type.INTEGER

    }

    @Serializable
    @SerialName("array")
    open class Array(
        override val description: String? = null,
        val items: JsonSchemaProperty? = null,
    ) : JsonSchemaProperty {

        @Transient
        override val type: Type = Type.ARRAY

    }

    @Serializable
    @SerialName("boolean")
    open class Boolean(
        override val description: String? = null,
    ) : JsonSchemaProperty {

        @Transient
        override val type: Type = Type.BOOLEAN

    }

    @Serializable
    @SerialName("null")
    open class Null(
        override val description: String? = null,
    ) : JsonSchemaProperty {

        @Transient
        override val type: Type = Type.NULL

    }

    enum class Type {
        @SerialName("object")
        OBJECT,

        @SerialName("string")
        STRING,

        @SerialName("number")
        NUMBER,

        @SerialName("integer")
        INTEGER,

        @SerialName("array")
        ARRAY,

        @SerialName("boolean")
        BOOLEAN,

        @SerialName("null")
        NULL,
    }

}

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class JsonSchemaDescription(val description: String)

internal fun SerialDescriptor.jsonSchemaProperty(
    providedName: String? = null,
    annotations: List<Annotation> = emptyList(),
    limitedResponseResolver: (String) -> List<String> = { emptyList() }
): JsonSchemaProperty {
    val name = providedName ?: this.serialName
    val description = annotations.description()
    val limitedResponses = limitedResponseResolver.invoke(name)
    return when (this.kind) {
        PrimitiveKind.BOOLEAN -> JsonSchemaProperty.Boolean(description)
        PrimitiveKind.BYTE -> JsonSchemaProperty.Integer(description)
        PrimitiveKind.CHAR -> JsonSchemaProperty.Str(description)
        PrimitiveKind.DOUBLE -> JsonSchemaProperty.Number(description)
        PrimitiveKind.FLOAT -> JsonSchemaProperty.Number(description)
        PrimitiveKind.INT -> JsonSchemaProperty.Integer(description)
        PrimitiveKind.LONG -> JsonSchemaProperty.Integer(description)
        PrimitiveKind.SHORT -> JsonSchemaProperty.Integer(description)
        PrimitiveKind.STRING -> JsonSchemaProperty.Str(description, limitedResponses.ifEmpty { null })
        SerialKind.ENUM -> JsonSchemaProperty.Str(description, elementNames.toList())
        StructureKind.LIST -> JsonSchemaProperty.Array(description, elementDescriptors.first().jsonSchemaProperty("[$name]", limitedResponseResolver = limitedResponseResolver))
        StructureKind.CLASS, StructureKind.MAP, StructureKind.OBJECT -> {
            JsonSchemaProperty.Object(
                description,
                elementDescriptors.mapIndexedNotNull { index, _ -> if (isElementOptional(index)) null else getElementName(index) },
                elementDescriptors.mapIndexed { index, descriptor ->
                    getElementName(index) to descriptor.jsonSchemaProperty(
                        getElementName(index),
                        getElementAnnotations(index),
                        limitedResponseResolver
                    )
                }
                    .toMap()
            )
        }

        else -> throw UnsupportedOperationException("$serialName has unsupported serial descriptor of of kind $kind.")
    }
}

private fun List<Annotation>.description(): String? {
    return filterIsInstance<JsonSchemaDescription>().firstOrNull()?.description
}
