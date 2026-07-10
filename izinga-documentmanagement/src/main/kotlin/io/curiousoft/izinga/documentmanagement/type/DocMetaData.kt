package io.curiousoft.izinga.documentmanagement.type

import com.fasterxml.jackson.databind.JsonNode
import io.curiousoft.izinga.commons.model.ProfileRoles
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import io.curiousoft.izinga.documentmanagement.type.JsonSchemaUtil

@Document(collection = "userTypeConfig")
data class DocMetadata(
    @Id
    var name: String,
    var label: String,
    var userRole: ProfileRoles? = null,
    var mandatoryFields: List<FieldSpec> = emptyList(),
    var optionalFields: List<FieldSpec> = emptyList(),
    /** Fields that are still sent to the vision model for extraction, but stripped from
     *  the response before it reaches the frontend. */
    var hiddenFields: List<FieldSpec> = emptyList(),
) {
    fun toJsonSchema(): JsonNode {
        val allFields = mandatoryFields + optionalFields + hiddenFields
        val propertiesNode = JsonSchemaUtil.createPropertiesNode(allFields)
        val requiredNode = JsonSchemaUtil.createRequiredNode(mandatoryFields + optionalFields)
        return JsonSchemaUtil.createJsonSchemaNode(propertiesNode, requiredNode, label)
    }

    /** Names of the hidden fields — used to strip them from the extraction response
     *  before it reaches the frontend. */
    fun hiddenFieldNames(): Set<String> = hiddenFields.map { it.name }.toSet()
}

/**
 * Specification for a single field: its name, expected data type and optional constraints/options.
 */
data class FieldSpec(
    var name: String,
    var label: String,
    var dataType: FieldDataType,
)

enum class FieldDataType {
    STRING,
    NUMBER,
    BOOLEAN,
    DATE,
    ENUM,
    DOCUMENT_URL
}
