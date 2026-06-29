package io.curiousoft.izinga.documentmanagement.type

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode

object JsonSchemaUtil {
    private val mapper: ObjectMapper = ObjectMapper()

    fun createPropertiesNode(fields: List<FieldSpec>): ObjectNode {
        val props = mapper.createObjectNode()
        for (f in fields) {
            val prop = mapper.createObjectNode()
            when (f.dataType) {
                FieldDataType.STRING -> prop.put("type", "string")
                FieldDataType.NUMBER -> prop.put("type", "number")
                FieldDataType.BOOLEAN -> prop.put("type", "boolean")
                FieldDataType.DATE -> {
                    prop.put("type", "string")
                    prop.put("format", "date-time")
                }
                FieldDataType.ENUM -> prop.put("type", "string")
                FieldDataType.DOCUMENT_URL -> {
                    prop.put("type", "string")
                    prop.put("description", "The absolute URI of the driver's license document.")
                }
            }
            prop.put("title", f.label)
            props.set<ObjectNode>(f.name, prop)
        }
        return props
    }

    fun createRequiredNode(mandatoryFields: List<FieldSpec>): ArrayNode? {
        if (mandatoryFields.isEmpty()) return null
        val arr = mapper.createArrayNode()
        for (f in mandatoryFields) arr.add(f.name)
        return arr
    }

    fun createJsonSchemaNode(properties: ObjectNode, required: ArrayNode?, title: String? = null): ObjectNode {
        val root = mapper.createObjectNode()
        root.put("\$schema", "https://json-schema.org/draft/2020-12/schema")
        if (!title.isNullOrBlank()) root.put("title", title)
        root.put("type", "object")
        root.set<ObjectNode>("properties", properties)
        if (required != null) root.set<ArrayNode>("required", required)
        root.put("additionalProperties", false)
        return root
    }

    // convenience overload for callers that don't want to provide a title
    fun createJsonSchemaNode(properties: ObjectNode, required: ArrayNode?): ObjectNode =
        createJsonSchemaNode(properties, required, null)
}

