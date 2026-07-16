package io.curiousoft.izinga.usermanagement.userconfig

import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "userTypeConfig")
data class UserConfig(
    @Id
    var name: String,
    var label: String,
    var userRole: ProfileRoles? = null,
    var mandatoryFields: List<FieldSpec> = emptyList(),
    var optionalFields: List<FieldSpec> = emptyList(),
    /** Fields that are never rendered on the onboarding form but still travel with the
     *  profile payload — e.g. set programmatically from document extraction rather than
     *  typed by the user. */
    var hiddenFields: List<FieldSpec> = emptyList(),
)

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

