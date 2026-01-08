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
    val documentType: String,
    val country: String,
    val name: String,
    val idNumber: String,
    val dateOfBirth: String,
    val expiryDate: String,
    val addressLine1: String,
    val addressLine2: String,
    val city: String,
    val postalCode: String,
    val licenceNumber: String
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