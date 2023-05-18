package io.curiousoft.izinga.commons.model

abstract class GeoDistance : Comparable<GeoDistance> {
    var distance = 0.0

    /**
     * @return an angle between A and B in degrees.
     */
    val direction = 0.0
    override fun compareTo(o: GeoDistance): Int {
        return if (distance > o.distance) 1 else if (distance == o.distance) 0 else -1
    }

    companion object {
        const val EARTH_RADIUS_IN_KM = 6378.137
        const val TO_RADIANS = Math.PI / 180

        /**
         * Uses Harvesine formula to calculate the distance between two points in a sphere
         *
         * @return The distance between points
         */
        fun getDistanceInKiloMetersBetweenTwoGeoPoints(a: GeoPoint, b: GeoPoint): Double {
            val latitude = a.latitude * TO_RADIANS
            val longitude = a.longitude * TO_RADIANS
            val latitude1 = b.latitude * TO_RADIANS
            val longitude1 = b.longitude * TO_RADIANS
            val results =
                Math.pow(Math.sin((latitude1 - latitude) / 2), 2.0) + Math.cos(latitude) * Math.cos(latitude1) * Math
                    .pow(Math.sin((longitude1 - longitude) / 2), 2.0)
            val dist = 2 * Math.atan2(Math.sqrt(results), Math.sqrt(1 - results))
            return dist * EARTH_RADIUS_IN_KM
        }

        fun getAngleInDegreesBetweenTwoGeoPoints(a: GeoPoint, b: GeoPoint): Double {
            val angle = Math.toDegrees(
                Math.atan2(b.latitude - a.latitude, b.longitude - a.longitude)
            )
            return if (angle >= 0) angle else 360 + angle
        }
    }
}