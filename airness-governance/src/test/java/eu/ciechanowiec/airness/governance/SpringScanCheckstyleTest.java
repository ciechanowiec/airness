package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpringScanCheckstyleTest {

    private static final String RULE = "AirnessSpringScanIsNotRedeclared";

    @Test
    void reportsAComponentScanThatNamesABasePackage(@TempDir Path directory) {
        String source = """
            @ComponentScan(basePackages = "com.acme")
            class Scanning {}
            """;

        assertEquals(1, findings(directory, source), "an explicit base package restates or overrides the root");
    }

    @Test
    void reportsAnApplicationThatNamesItsScanBasePackages(@TempDir Path directory) {
        String source = """
            @SpringBootApplication(scanBasePackages = "com.acme")
            class Application {}
            """;

        assertEquals(1, findings(directory, source), "the application annotation names the same scan");
    }

    @Test
    void reportsAnApplicationThatNamesItsScanBasePackageClasses(@TempDir Path directory) {
        String source = """
            @SpringBootApplication(scanBasePackageClasses = Marker.class)
            class Application {}
            """;

        assertEquals(1, findings(directory, source), "a marker class names a base package all the same");
    }

    @Test
    void acceptsAnApplicationThatLeavesTheScanToItsPackage(@TempDir Path directory) {
        String source = """
            @SpringBootApplication(proxyBeanMethods = false)
            class Application {}
            @SpringBootApplication
            class Other {}
            """;

        assertEquals(0, findings(directory, source), "an application without a base package scans its own root");
    }

    private static int findings(Path directory, String source) {
        return CheckstyleRule.findings(directory, source, RULE);
    }
}
