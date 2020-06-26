package io.curiousoft.ijudi.ordermanagement.utils;

import io.curiousoft.ijudi.ordermanagement.model.GeoDistance;
import io.curiousoft.ijudi.ordermanagement.model.GeoPointImpl;
import org.junit.Assert;
import org.junit.Test;

public class GeoDistanceTest {

    @Test
    public void testGetAngleInDegreesBetweenTwoGeoPointsIs_45_degrees() throws Exception {

        GeoPointImpl a = new GeoPointImpl(0, 0);
        GeoPointImpl b = new GeoPointImpl(1, 1);
        double angle = GeoDistance.getAngleInDegreesBetweenTwoGeoPoints(a, b);
        Assert.assertEquals(45, angle, 0.1);
    }

    @Test
    public void testGetAngleInDegreesBetweenTwoGeoPointsIs_30_degrees() throws Exception {

        GeoPointImpl a = new GeoPointImpl(0, 0);
        GeoPointImpl b = new GeoPointImpl(1, Math.sqrt(3));
        double angle = GeoDistance.getAngleInDegreesBetweenTwoGeoPoints(a, b);
        Assert.assertEquals(30, angle, 0.1);
    }

    @Test
    public void testGetAngleInDegreesBetweenTwoGeoPointsIs_60_degrees() throws Exception {

        GeoPointImpl a = new GeoPointImpl(1 , 1);
        GeoPointImpl b = new GeoPointImpl(1 + Math.sqrt(3), 2);
        double angle = GeoDistance.getAngleInDegreesBetweenTwoGeoPoints(a, b);
        Assert.assertEquals(60, angle, 0.1);
    }

    @Test
    public void testGetAngleInDegreesBetweenTwoGeoPointsIs_180_degrees() throws Exception {

        GeoPointImpl a = new GeoPointImpl(0 , 0);
        GeoPointImpl b = new GeoPointImpl(0, -1);
        double angle = GeoDistance.getAngleInDegreesBetweenTwoGeoPoints(a, b);
        Assert.assertEquals(180, angle, 0.1);
    }

    @Test
    public void testGetAngleInDegreesBetweenTwoGeoPointsIs_120_degrees() throws Exception {

        GeoPointImpl a = new GeoPointImpl(0 , 0);
        GeoPointImpl b = new GeoPointImpl(Math.sqrt(3), -1);
        double angle = GeoDistance.getAngleInDegreesBetweenTwoGeoPoints(a, b);
        Assert.assertEquals(120, angle, 0.1);
    }

    @Test
    public void testGetAngleInDegreesBetweenTwoGeoPointsIs_240_degrees() throws Exception {

        GeoPointImpl a = new GeoPointImpl(0 , 0);
        GeoPointImpl b = new GeoPointImpl(- Math.sqrt(3), -1);
        double angle = GeoDistance.getAngleInDegreesBetweenTwoGeoPoints(a, b);
        Assert.assertEquals(240, angle, 0.1);
    }

    @Test
    public void testGetDistanceBetweenTwoGeoPoints() throws Exception {

        GeoPointImpl a = new GeoPointImpl(0 , 0);
        GeoPointImpl b = new GeoPointImpl(1, 1);
        double distance = GeoDistance.getDistanceInKiloMetersBetweenTwoGeoPoints(a, b);
        Assert.assertEquals(156.9, distance, 0.9);
    }
}
