package io.curiousoft.izinga.commons.model

class GeoPointImpl(override var latitude: Double, override var longitude: Double) : GeoPoint {

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val geoPoint = o as GeoPointImpl
        return if (java.lang.Double.compare(geoPoint.latitude, latitude) != 0) {
            false
        } else java.lang.Double.compare(
            geoPoint.longitude,
            longitude
        ) == 0
    }
}