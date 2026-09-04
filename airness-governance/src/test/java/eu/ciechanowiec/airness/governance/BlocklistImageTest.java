package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The image half of the refusal rule, exercised on the spellings a repository writes: a tag below a
 * floor that must stay allowed, a tag that floats past the floor and must not, an edition a suffix
 * alone selects, and the order in which one reference is judged.
 */
class BlocklistImageTest {

    private static final String SSPL = "SSPL";
    private static final String NOT_REFUSED = "is not refused";

    private static Optional<String> reason(Optional<Refusal> refusal) {
        return refusal.map(Refusal::reason);
    }

    @Test
    void refusesMongoHoweverItIsSpelled() {
        assertTrue(reason(Blocklist.image("docker.io/library/mongo:7.0.14")).orElse("").contains(SSPL));
        assertTrue(reason(Blocklist.image("mongodb/mongodb-community-server:7.0-ubi8")).orElse("").contains(SSPL));
    }

    @Test
    void leavesRedisBelowTheFloorToTheLicenceAllowlist() {
        assertEquals(Optional.empty(), Blocklist.image("redis:7.2.4"), "7.2 is the open release line");
        assertEquals(Optional.empty(), Blocklist.image("redis:7.2.4-alpine"), "whatever suffix it carries");
    }

    @Test
    void refusesRedisAtAndPastTheFloor() {
        assertTrue(Blocklist.image("redis:7.4.0").map(Refusal::replacement).orElse("").contains("valkey"));
        assertTrue(Blocklist.image("redis:8.0.1").isPresent(), "a later major is refused");
        assertTrue(Blocklist.image("redis:7.4-alpine").isPresent(), "and the floor itself with a suffix");
    }

    // redis:7 resolves to 7.4.x today and redis:latest to whatever is newest, and neither proves it sits
    // below the floor, so both are refused rather than passed on a guess.
    @Test
    void refusesATagThatCannotBePlacedAgainstTheFloor() {
        List<String> floating = List.of("redis:7", "redis:alpine", "redis:latest", "redis");
        assertTrue(
            floating.stream().map(Blocklist::image).allMatch(Optional::isPresent), "every floating tag is refused"
        );
    }

    @Test
    void appliesTheFloorUnderAForeignRegistryPrefix() {
        assertEquals(Optional.empty(), Blocklist.image("docker.elastic.co/elasticsearch/elasticsearch:7.10.2"));
        assertTrue(Blocklist.image("docker.elastic.co/elasticsearch/elasticsearch:8.15.0").isPresent());
    }

    @Test
    void allowsTheOpenSourceTimescaleVariantAndRefusesTheDefault() {
        assertEquals(Optional.empty(), Blocklist.image("timescale/timescaledb-ha:pg16-ts2.14-oss-latest"));
        assertEquals(Optional.empty(), Blocklist.image("timescale/timescaledb:2.14.2-pg16-oss"));
        assertTrue(
            Blocklist.image("timescale/timescaledb:2.14.2-pg16").isPresent(), "the default tag carries TSL code"
        );
    }

    @Test
    void refusesOnlyTheEditionASuffixSelects() {
        assertTrue(Blocklist.image("neo4j:5.26.0-enterprise").isPresent(), "the enterprise edition is proprietary");
        assertEquals(Optional.empty(), Blocklist.image("neo4j:5.26.0"), "the community edition is GPL and open");
        assertTrue(Blocklist.image("sonarqube:2025.1-developer").isPresent(), "a commercial SonarQube edition");
        assertEquals(Optional.empty(), Blocklist.image("sonarqube:25.1.0.102122-community"), "not the community build");
    }

    @Test
    void refusesAWholeNamespace() {
        assertTrue(reason(Blocklist.image("bitnami/postgresql:16.3.0")).orElse("").contains("Bitnami"));
        assertTrue(Blocklist.image("bitnamilegacy/minio:2025.7.23").isPresent());
    }

    @Test
    void passesTheImagesAJavaProjectPulls() {
        List<String> allowed = List.of(
            "postgres:18", "valkey/valkey:8.1", "eclipse-temurin:25-jre", "confluentinc/cp-kafka:7.8.0",
            "apache/kafka:3.9.0", "opensearchproject/opensearch:2.19.0", "axllent/mailpit:v1.24.0",
            "ghcr.io/graalvm/native-image-community:25", "grafana/alloy:v1.5.0"
        );
        assertTrue(allowed.stream().map(Blocklist::image).allMatch(Optional::isEmpty), "none of these " + NOT_REFUSED);
    }

    @Test
    void judgesAReferenceInTheOrderAReaderRepairsIt() {
        assertTrue(reason(Blocklist.judgeImage("redis:${TAG}")).orElse("").contains("cannot be judged"));
        assertTrue(reason(Blocklist.judgeImage("mongo")).orElse("").contains(SSPL), "a refused image is named first");
        assertTrue(
            reason(Blocklist.judgeImage("postgres")).orElse("").contains("nothing pins"), "then the missing pin"
        );
        assertEquals(Optional.empty(), Blocklist.judgeImage("postgres:18"), "and a pinned open image passes");
    }

    @Test
    void printsARefusalBesideItsLocation() {
        Refusal refusal = new Refusal("mongo:7", "the reason", "the replacement");
        assertEquals("Dockerfile:1: mongo:7 - the reason; use the replacement", refusal.at("Dockerfile:1"));
    }
}
