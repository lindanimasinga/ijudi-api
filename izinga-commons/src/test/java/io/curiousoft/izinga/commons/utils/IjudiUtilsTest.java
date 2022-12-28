package io.curiousoft.izinga.commons.utils;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static io.curiousoft.izinga.commons.utils.IjudiUtilsKt.isSAMobileNumber;

@RunWith(JUnit4.class)
public class IjudiUtilsTest {

    @Test
    public void test_isSAMobileNumber() {
        Assert.assertTrue(isSAMobileNumber("0812814701"));
        Assert.assertTrue(isSAMobileNumber("27812814701"));
        Assert.assertTrue(isSAMobileNumber("+27812814701"));
        Assert.assertFalse(isSAMobileNumber("+17812814701"));
        Assert.assertFalse(isSAMobileNumber("+278128147011"));
    }

    @Test
    public void isValidHashGenerated() {

    }
}