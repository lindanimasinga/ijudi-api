package io.curiousoft.izinga.ordermanagement.cucumber;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(
        features = "src/test/resources/features",
        glue = "io.curiousoft.izinga.ordermanagement.cucumber",
        plugin = {"pretty", "html:target/cucumber-reports/cucumber.html"},
        monochrome = true
)
public class CucumberTestRunner {
}
