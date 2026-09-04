package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The name half of the refusal rule: a coordinate whose own licence is fine while the server it reaches
 * is not, a version floor, a system package, a JDK distribution, and the JDK the build runs on.
 */
class BlocklistCoordinateTest {

    private static final String NOT_REFUSED = "is not refused";

    private static Optional<String> reason(Optional<Refusal> refusal) {
        return refusal.map(Refusal::reason);
    }

    private static DeclaredCoordinate coordinate(String group, String artifact, String version) {
        return new DeclaredCoordinate(group, artifact, version);
    }

    @Test
    void refusesADriverWhoseOnlyServerIsNotOpenSource() {
        Optional<Refusal> driver = Blocklist.coordinate(coordinate("org.mongodb", "mongodb-driver-sync", "5.3.0"));
        assertTrue(
            driver.map(Refusal::replacement).orElse("").contains("postgres"), "the refusal names the replacement"
        );
        assertTrue(Blocklist.coordinate(coordinate("org.mongodb.kafka", "mongo-kafka-connect", "1.14.0")).isPresent());
        assertTrue(Blocklist.coordinate(coordinate("org.testcontainers", "mongodb", "1.20.4")).isPresent());
    }

    @Test
    void refusesAStarterByArtifactPrefix() {
        String group = "org.springframework.boot";
        assertTrue(
            Blocklist.coordinate(coordinate(group, "spring-boot-starter-data-mongodb-reactive", "3.4.0")).isPresent()
        );
        assertEquals(
            Optional.empty(), Blocklist.coordinate(coordinate(group, "spring-boot-starter-data-jpa", "3.4.0"))
        );
    }

    @Test
    void leavesTheHighLevelClientBelowTheFloorAndRefusesItFromSevenEleven() {
        String group = "org.elasticsearch.client";
        String artifact = "elasticsearch-rest-high-level-client";
        assertEquals(
            Optional.empty(), Blocklist.coordinate(coordinate(group, artifact, "7.10.2")), "Apache 2.0 at 7.10"
        );
        assertTrue(Blocklist.coordinate(coordinate(group, artifact, "7.11.0")).isPresent(), "Elastic License at 7.11");
        assertTrue(Blocklist.coordinate(coordinate(group, artifact, "${elastic.version}")).isPresent(), "unplaceable");
        assertEquals(Optional.empty(), Blocklist.coordinate(coordinate(group, "elasticsearch-rest-client", "8.17.0")));
    }

    @Test
    void refusesABuildExtensionWithNoVersionToPlace() {
        assertTrue(Blocklist.coordinate(coordinate("com.gradle", "develocity-maven-extension", "")).isPresent());
    }

    @Test
    void refusesASystemPackageByName() {
        assertTrue(Blocklist.systemPackage("ghostscript").map(Refusal::replacement).orElse("").contains("pdfbox"));
        assertEquals(Optional.empty(), Blocklist.systemPackage("curl"), "curl " + NOT_REFUSED);
    }

    @Test
    void refusesTheOracleDistributionsAndAllowsTheOpenOnes() {
        assertTrue(reason(Blocklist.distribution("oracle")).orElse("").contains("No-Fee"));
        assertTrue(reason(Blocklist.distribution("GraalVM")).orElse("").contains("GraalVM Free Terms"));
        assertEquals(Optional.empty(), Blocklist.distribution("temurin"));
        assertEquals(Optional.empty(), Blocklist.distribution("graalvm-community"));
    }

    @Test
    void refusesTheOracleVendorsInASdkmanEntry() {
        assertTrue(Blocklist.sdkmanVendor("oracle").isPresent());
        assertTrue(Blocklist.sdkmanVendor("graal").isPresent(), "graal is Oracle GraalVM in sdkman");
        assertEquals(Optional.empty(), Blocklist.sdkmanVendor("graalce"), "graalce is the community edition");
        assertEquals(Optional.empty(), Blocklist.sdkmanVendor("tem"));
    }

    @Test
    void refusesEveryRuntimeThatIsNotAnOpenJdkBuild() {
        assertEquals(Optional.empty(), Blocklist.runtime("OpenJDK Runtime Environment"));
        assertTrue(Blocklist.runtime("Java(TM) SE Runtime Environment").isPresent(), "Oracle names its runtime so");
        assertEquals(
            "an unnamed runtime", Blocklist.runtime("").map(Refusal::subject).orElse(""), "and none is no proof"
        );
    }
}
