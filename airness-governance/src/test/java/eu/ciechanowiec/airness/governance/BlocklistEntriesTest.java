package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The table is well formed: every entry says why and what instead, every image repository is written
 * in the one spelling the parser produces, every floor is a version, and the source-rewriting engines are refused.
 */
class BlocklistEntriesTest {

    private static final String WILDCARD = "*";

    // Whether a row says both why it refuses and what to reach for instead.
    private static boolean stated(String reason, String replacement) {
        return !reason.isBlank() && !replacement.isBlank();
    }

    @Test
    void namesAReasonAndAReplacementForEveryEntry() {
        boolean images = BlocklistEntries.images().stream()
            .allMatch(entry -> stated(entry.reason(), entry.replacement()));
        boolean coordinates = BlocklistEntries.coordinates().stream()
            .allMatch(entry -> stated(entry.reason(), entry.replacement()));
        assertTrue(
            images && coordinates, "a refusal without a replacement sends the reader to the search this table did"
        );
    }

    @Test
    void writesEveryImageRepositoryNormalised() {
        List<String> written = BlocklistEntries.images().stream()
            .map(BlockedImage::repository)
            .map(repository -> repository.replace(WILDCARD, ""))
            .toList();
        assertTrue(
            written.stream().allMatch(repository -> ImageReference.parse(repository).repository().equals(repository)),
            "a repository written in a spelling the parser rewrites would never match"
        );
    }

    @Test
    void namesEveryEntryOnce() {
        long images = BlocklistEntries.images().stream().map(BlockedImage::repository).distinct().count();
        long coordinates = BlocklistEntries.coordinates().stream()
            .map(entry -> entry.group() + ':' + entry.artifact())
            .distinct()
            .count();
        assertEquals(BlocklistEntries.images().size(), images, "a repeated image entry is one that shadows another");
        assertEquals(BlocklistEntries.coordinates().size(), coordinates, "and so is a repeated coordinate");
    }

    @Test
    void placesEveryFloorAsAVersion() {
        Stream<String> floors = Stream.concat(
            BlocklistEntries.images().stream().map(BlockedImage::floor),
            BlocklistEntries.coordinates().stream().map(BlockedCoordinate::floor)
        ).flatMap(Optional::stream);
        assertTrue(
            floors.allMatch(floor -> floor.matches("\\d+(?:\\.\\d+)*")), "a floor nothing can place refuses everything"
        );
    }

    @Test
    void refusesEveryOpenRewriteAndModerneArtifact() {
        assertAll(
            Stream.of(
                List.of("org.openrewrite.maven", "rewrite-maven-plugin"),
                List.of("org.openrewrite.recipe", "rewrite-static-analysis"),
                List.of("org.openrewrite", "rewrite-java"),
                List.of("io.moderne", "moderne-maven-plugin")
            ).map(
                coordinate -> () -> assertTrue(
                    BlocklistEntries.coordinate(coordinate.getFirst(), coordinate.getLast()).isPresent(),
                    () -> coordinate + " is refused"
                )
            )
        );
        assertTrue(
            BlocklistEntries.coordinate("org.openrewritten", "tool").isEmpty(), "only the named groups are refused"
        );
    }

    @Test
    void findsTheSpecificEntryBeforeItsWildcard() {
        assertEquals(Optional.of("redis"), BlocklistEntries.image("redis").map(BlockedImage::repository));
        assertEquals(Optional.of("redis/*"), BlocklistEntries.image("redis/redis-stack").map(BlockedImage::repository));
    }

    @Test
    void answersEmptyForAnUnlistedName() {
        assertEquals(Optional.empty(), BlocklistEntries.image("postgres"));
        assertEquals(Optional.empty(), BlocklistEntries.coordinate("org.postgresql", "postgresql"));
        assertEquals(Optional.empty(), BlocklistEntries.systemPackage("curl"));
        assertEquals(Optional.empty(), BlocklistEntries.distribution("temurin"));
        assertEquals(Optional.empty(), BlocklistEntries.sdkmanVendor("tem"));
    }
}
