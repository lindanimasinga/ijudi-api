package io.curiousoft.ijudi.ordermanagement.utils;

import org.junit.Assert;
import org.junit.Test;

class IjudiUtilsTest {

    @Test
    void isSAMobileNumber() {
        Assert.assertTrue(IjudiUtils.isSAMobileNumber("0812814701"));
        Assert.assertTrue(IjudiUtils.isSAMobileNumber("27812814701"));
        Assert.assertTrue(IjudiUtils.isSAMobileNumber("+27812814701"));
        Assert.assertFalse(IjudiUtils.isSAMobileNumber("+17812814701"));
        Assert.assertFalse(IjudiUtils.isSAMobileNumber("+278128147011"));
    }

    @Test
    void isValidHashGenerated() {

    }
}