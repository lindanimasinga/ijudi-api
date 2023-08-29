package io.curiousoft.izinga.commons.utils

import io.curiousoft.izinga.messaging.domain.directions.Leg
import io.curiousoft.izinga.messaging.domain.directions.Route
import io.curiousoft.izinga.messaging.firebase.GoogleServices.GoogleMaps
import io.curiousoft.izinga.commons.model.Order
import io.curiousoft.izinga.commons.model.StoreProfile
import io.curiousoft.izinga.commons.model.StoreType
import org.hibernate.validator.internal.constraintvalidators.hv.LuhnCheckValidator
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*

object IjudiUtils

private val LOGGER = LoggerFactory.getLogger(IjudiUtils::class.java)
fun isIdNumber(id: String): Boolean {
    val idNumberRegex =
        "(((\\d{2}((0[13578]|1[02])(0[1-9]|[12]\\d|3[01])|(0[13456789]|1[012])(0[1-9]|[12]\\d|30)|02(0[1-9]|1\\d|2[0-8])))|([02468][048]|[13579][26])0229))(( |-)(\\d{4})( |-)(\\d{3})|(\\d{7}))"
    if (Objects.isNull(id) || !id.matches(idNumberRegex.toRegex())) {
        return false
    }
    val digits: MutableList<Int> = ArrayList()
    id.chars().forEachOrdered { item: Int -> digits.add(("" + item.toChar()).toInt()) }
    val checkSome = id[id.length - 1]
    digits.removeAt(id.length - 1)
    val luhnCheckValidator = LuhnCheckValidator()
    return luhnCheckValidator.isCheckDigitValid(digits, checkSome)
}

fun isSAMobileNumber(number: String): Boolean {
    val idNumberRegex = "(\\+27|27|0)[1-9]\\d{8}"
    return number.matches(idNumberRegex.toRegex())
}

/**
 * Calculates markup price and keep original decimals or cents
 * @param storePrice
 * @param markPercentage
 * @return markup price
 */
fun calculateMarkupPrice(storePrice: Double, markPercentage: Double): Double {
    val cents = storePrice - storePrice.toInt()
    val markupPrice = storePrice + storePrice * markPercentage
    return markupPrice.toInt() + if (cents > 0.45) cents else 1 + cents
}

@Throws(IOException::class)
fun calculateDrivingDirectionKM(apiKey: String?, order: Order, store: StoreProfile): Double {
    val googleMapsInstance = GoogleMaps.instance
    // from lat long
    val fromLatLong = if (store.storeType == StoreType.MOVERS)
        googleMapsInstance.geocodeAddress(apiKey, order.shippingData?.fromAddress, 100.0).execute().body()
            .let { it.results[0].geometry.location }
            .let { it.lat.toString() + "," + it.lng }
        else store.latitude.toString() + "," + store.longitude

    //to lat long
    val geoCode = googleMapsInstance.geocodeAddress(apiKey, order.shippingData?.toAddress, 100.0).execute().body()
    val location = geoCode.results[0].geometry.location
    val toLatLong = location.lat.toString() + "," + location.lng
    val directions = googleMapsInstance.findDirections(apiKey, fromLatLong, toLatLong).execute().body()
    return directions.routes.stream()
        .flatMap { route: Route -> route.legs.stream() }
        .min(Comparator.comparingInt { leg1: Leg -> leg1.distance.value })
        .map { value: Leg -> value.distance.value / 1000 }.orElse(0)
        .toDouble() // kilometers
}

fun calculateDeliveryFee(
    standardFee: Double,
    standardDistance: Double,
    ratePerKM: Double,
    distance: Double
): Double {
    return if (distance > standardDistance) standardFee + ratePerKM * (distance - standardDistance) else standardFee
}

fun isNullOrEmpty(collection: Collection<Any>?) = collection?.isEmpty() ?: true