package io.curiousoft.ijudi.ordermanagement.model;

public abstract class GeoDistance implements Comparable<GeoDistance> {

    public static final double EARTH_RADIUS_IN_KM = 6378.137;
    public static final double TO_RADIANS = Math.PI / 180;
    private double distance;
    private double direction;


    /**
     * Uses Harvesine formula to calculate the distance between two points in a sphere
     *
     * @return The distance between points
     */
    public static double getDistanceInKiloMetersBetweenTwoGeoPoints(GeoPoint a, GeoPoint b) {

        double latitude = a.getLatitude() * TO_RADIANS;
        double longitude = a.getLongitude() * TO_RADIANS;
        double latitude1 = b.getLatitude() * TO_RADIANS;
        double longitude1 = b.getLongitude() * TO_RADIANS;

        double results = Math.pow(Math.sin((latitude1 - latitude) / 2), 2) + (Math.cos(latitude) * Math.cos(latitude1) * Math
                .pow(Math.sin((longitude1 - longitude) / 2), 2));
        double dist = 2 * Math.atan2(Math.sqrt(results), Math.sqrt(1 - results));
        return dist * EARTH_RADIUS_IN_KM;
    }

    public static double getAngleInDegreesBetweenTwoGeoPoints(GeoPoint a, GeoPoint b) {
        double angle = Math.toDegrees(
                Math.atan2(b.getLatitude() - a.getLatitude(), b.getLongitude() - a.getLongitude()));
        return angle >= 0 ? angle : 360 + angle;
    }

    public double getDistance() {
        return distance;
    }

    /**
     * @return an angle between A and B in degrees.
     */
    public double getDirection() {
        return direction;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    @Override
    public int compareTo(GeoDistance o) {
        return distance > o.getDistance() ? 1 : distance == o.getDistance() ? 0 : -1;
    }
}

