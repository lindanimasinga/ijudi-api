package io.curiousoft.izinga.commons.order

data class DeliveryPriceEstimateDto(
    val category: String,
    val fromAddress: String,
    val toAddress: String,
    val distanceKm: Double,
    val standardFee: Double,
    val standardKm: Double,
    val ratePerKm: Double,
    val estimatedDeliveryFee: Double
)