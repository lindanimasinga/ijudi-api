@file:Suppress("unused")
package io.curiousoft.izinga.documentmanagement.type

import kotlin.reflect.KClass

interface DocType {
}

class LicenseDisc(
    val documentType: String,
    val country: String,
    val licenceNumber: String,
    val vehicleRegisterNumber: String,
    val documentNumber: String,
    val vin: String,
    val engineNumber: String,
    val make: String,
    val description: String,
    val fee: String,
    val gvw: String,
    val tare: String,
    val numberOfPersons: String,
    val seated: String,
    val standing: String,
    val expiryDate: String
) : DocType

class ProofOfAddress(
    val documentType: String,
    val country: String,
    val name: String,
    val addressLine1: String,
    val addressLine2: String,
    val city: String,
    val postalCode: String,
    val issueDate: String,
    val expiryDate: String
) : DocType

class DriverID(
    val documentType: String,         // GREEN_ID_BOOK | SMART_ID_CARD | DRIVERS_LICENCE | UNKNOWN
    val country: String,              // ISO3 e.g. ZAF
    val nationality: String,          // ISO3 e.g. ZAF
    val idNumber: String,             // 13-digit SA ID number
    val surname: String,
    val firstNames: String,
    val dateOfBirth: String,          // YYYY-MM-DD
    val citizenshipStatus: String,    // CITIZEN | PERMANENT_RESIDENT | UNKNOWN
    val licenceNumber: String?,       // driver's licence only
    val licenceCodes: List<String>?,  // driver's licence only e.g. [B, EB]
    val validUntil: String?,          // expiry date YYYY-MM-DD (smart ID or licence)
    val pdpPresent: Boolean?,         // professional driving permit
    val idNumberValid: Boolean,       // 13-digit Luhn check passed
    val idNumberMatchesDob: Boolean,  // digits 1-6 match dateOfBirth
    val isExpired: Boolean,           // validUntil is in the past
    val validationErrors: List<String> // list of specific failures
) : DocType

class InsuranceCertificate(
    val documentType: String,
    val country: String,
    val insuredEntity: String,
    val policyNumber: String,
    val insurerName: String,
    val issueDate: String,
    val expiryDate: String,
    val documentUrl: String,
    val description: String? = null
) : DocType

// DPD specific document structure — includes company and contact details plus insurance doc URL
class DpdDocument(
    val documentType: String,
    val country: String,
    val companyName: String,
    val registrationNumber: String,
    val contactName: String,
    val contactEmail: String,
    val contactPhone: String,
    val insurancePolicyNumber: String?,
    val insurerName: String?,
    val insuranceIssueDate: String?,
    val insuranceExpiryDate: String?,
    val insuranceDocumentUrl: String,
    val description: String? = null
) : DocType

// Trade certificate required for tradespeople (plumbers, electricians, etc.)
class TradeCertificate(
    val documentType: String,
    val country: String,
    val holderName: String,
    val tradeType: String, // e.g. PLUMBER, ELECTRICIAN
    val registrationNumber: String,
    val issuingAuthority: String,
    val issueDate: String,
    val expiryDate: String?,
    val documentUrl: String,
    val description: String? = null
) : DocType

enum class DocTypesEnum(val klass: KClass<out DocType>) {
    ID(DriverID::class),
    ADDRESS_PROOF(ProofOfAddress::class),
    LICENSE_DISC(LicenseDisc::class),
    DRIVER_ID(DriverID::class),
    INSURANCE_CERTIFICATE(InsuranceCertificate::class),
    DPD(DpdDocument::class),
    TRADE_CERTIFICATE(TradeCertificate::class)
}