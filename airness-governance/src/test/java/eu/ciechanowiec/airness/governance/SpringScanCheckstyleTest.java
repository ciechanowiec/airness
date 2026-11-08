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

    @Test
    void reportsEverySpellingOfABasePackageOnTheScanAnnotations(@TempDir Path directory) {
        String source = """
            @ComponentScan(basePackageClasses = Marker.class)
            class ByMarker {}
            @EntityScan("com.acme")
            class Positional {}
            @EnableJpaRepositories({"com.acme.one", "com.acme.two"})
            class PositionalArray {}
            @ComponentScan(value = "com.acme")
            class NamedValue {}
            @EnableJpaRepositories(value = {"com.acme"}, repositoryImplementationPostfix = "Impl")
            class NamedValueArray {}
            @org.springframework.context.annotation.ComponentScan("com.acme")
            class Qualified {}
            @org.springframework.boot.autoconfigure.SpringBootApplication(scanBasePackages = "com.acme")
            class QualifiedApplication {}
            @ComponentScans(@ComponentScan("com.acme"))
            class Repeated {}
            """;

        assertEquals(8, findings(directory, source), "value is an alias of basePackages, however it is written");
    }

    @Test
    void acceptsScanAnnotationsThatNameNoBasePackage(@TempDir Path directory) {
        String source = """
            @ComponentScan
            class Bare {}
            @ComponentScan(excludeFilters = @ComponentScan.Filter(value = Marker.class))
            class Filtered {}
            @ComponentScan(excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, value = Marker.class))
            class ShortFiltered {}
            @EnableJpaRepositories(repositoryImplementationPostfix = "Impl")
            class Postfixed {}
            @Service("named")
            class Named {}
            """;

        assertEquals(
            0, findings(directory, source), "a filter's value names a filtered type, and other members name no package"
        );
    }

    private static int findings(Path directory, String source) {
        return CheckstyleRule.findings(directory, source, RULE);
    }
}
