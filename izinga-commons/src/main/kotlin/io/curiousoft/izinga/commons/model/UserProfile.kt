package io.curiousoft.izinga.commons.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.util.Date
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

class UserProfile(
    name: @NotBlank(message = "profile name not valid") String?,
    var signUpReason: @NotNull(message = "signupReason not valid") SignUpReason?,
    address: @NotBlank(message = "profile address not valid") String?,
    imageUrl: @NotBlank(message = "profile image url not valid") String?,
    mobileNumber: @NotBlank(message = "profile mobile number not valid") String?,
    role: @NotNull(message = "role not valid") ProfileRoles?) : Profile(name, address, imageUrl, mobileNumber, role) {
    var ambassadorId: String? = null
    var referralCode: String? = null
    /**
     * RP-004a: ID of the REFERRAL_PARTNER who referred this customer.
     * Set at registration by resolving the inbound `ref` query param via ReferralCodeService.
     * Never set directly by the client — the backend resolves the code to a partner ID.
     */
    var referredByPartnerId: String? = null
    var surname: String? = null
    var missingDocumentsReminderSent: Boolean? = null
    var welcomeMessageSent: Boolean = false

    @set:JsonIgnore
    var isPermanentEmployed: Boolean? = null

    var idNumber: String? = null
    var dateOfBirth: String? = null
    var termsAccepted: Boolean? = null
    var termsAcceptedDate: Date? = null
    var icaAccepted: Boolean? = null
    var icaAcceptedDate: Date? = null
    var icaVersion: String? = null
    var crminalCheckData: CriminalCheckData? = null

    enum class SignUpReason {
        DELIVERY_DRIVER, SELL, BUY, LICENSING
    }

    val vehicle: Vehicle
        get() = Vehicle().apply {
            this.vehicleMake = tag["vehicleMake"]
            this.vehicleModel = tag["vehicleModel"]
            this.loadCapacity = (tag["loadCapacity"] ?: tag["cargoCapacity"])?.toDoubleOrNull()
            this.ownerId = tag["ownerId"]
            this.driverId = id
            this.vehicleRegistration = tag["vehicleRegistration"]
    }
}


class CriminalCheckData {
    var criminalRecordCheckAccepted: Boolean? = null
    var criminalCheckMessageSent: Boolean = false
    var criminalRecordCheckDate: Date? = null
    var criminalRecordCheckDocument: DocumentAttachment? = null
    var criminalCheckPass: Boolean? = null
}