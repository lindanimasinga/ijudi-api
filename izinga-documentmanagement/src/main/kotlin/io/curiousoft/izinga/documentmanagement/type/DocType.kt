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

enum class DocTypesEnum(val klass: KClass<out DocType>) {
    ID(LicenseDisc::class),
    ADDRESS_PROOF(LicenseDisc::class),
    LICENSE_DISC(LicenseDisc::class);
}