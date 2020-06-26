package io.curiousoft.ijudi.ordermanagement.model;

public class GeoPointImpl implements GeoPoint {

    private double latitude;
    private double longitude;

    public GeoPointImpl(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    @Override
    public double getLatitude() {
        return latitude;
    }

    @Override
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    @Override
    public double getLongitude() {
        return longitude;
    }

    @Override
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        GeoPointImpl geoPoint = (GeoPointImpl) o;

        if (Double.compare(geoPoint.latitude, latitude) != 0) {
            return false;
        }
        return Double.compare(geoPoint.longitude, longitude) == 0;
    }
}
