package io.curiousoft.izinga.documentmanagement.type

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import io.curiousoft.izinga.documentmanagement.type.JsonSchemaUtil

@Document(collection = "userTypeConfig")
data class DocMetadata(
    @Id
    var name: String,
    var label: String,
    var mandatoryFields: List<FieldSpec> = emptyList(),
    var optionalFields: List<FieldSpec> = emptyList()
) {
    fun toJsonSchema(): JsonNode {
        val allFields = mandatoryFields + optionalFields
        val propertiesNode = JsonSchemaUtil.createPropertiesNode(allFields)
        val requiredNode = JsonSchemaUtil.createRequiredNode(allFields)
        return JsonSchemaUtil.createJsonSchemaNode(propertiesNode, requiredNode, label)
    }
}

/**
 * Specification for a single field: its name, expected data type and optional constraints/options.
 */
data class FieldSpec(
    var name: String,
    var label: String,
    var dataType: FieldDataType
)

enum class FieldDataType {
    STRING,
    NUMBER,
    BOOLEAN,
    DATE,
    ENUM,
    DOCUMENT_URL
}
