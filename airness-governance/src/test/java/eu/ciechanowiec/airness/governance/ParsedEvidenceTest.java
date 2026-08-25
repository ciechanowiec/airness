package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exercises the small parsers and validated values that carry governance evidence between checks.
 */
class ParsedEvidenceTest {

    @Test
    void parsesTextBinaryAndMalformedDiffStatistics() {
        DiffStat parsed = DiffStats.parse("2\t3\ta.txt\n-\t-\timage.png\ninvalid\n\t4\tempty.txt\n");
        assertEquals(new DiffStat(3, 9), parsed);
        assertEquals(new DiffStat(0, 0), DiffStats.parse("\n"));
    }

    @Test
    void metadataDropsEmptyVersionElements() {
        assertEquals(
            List.of("1.0.0"),
            MavenMetadata.versions("<metadata><version> </version><version>1.0.0</version></metadata>")
        );
    }

    @Test
    void rejectsEveryNonCanonicalManagedAssetShape() {
        // The bare root is here because it has no name at index zero. The canonical test used to ask for
        // one before the unsafe test had ruled the path out, so the refusal arrived as a bare exception
        // carrying none of the explanation. The message is asserted for the same reason: the type alone
        // cannot tell the intended refusal from an accident on the way to it.
        List<String> paths = List.of("", "nested\\file", "/absolute", "nested/../file", "/");
        assertAll(
            paths.stream().map(
                path -> () -> assertEquals(
                    "Managed asset path must be canonical and repository-relative: " + path,
                    assertThrows(
                        IllegalArgumentException.class,
                        () -> new ManagedAsset(path, AssetPolicy.PINNED)
                    ).getMessage()
                )
            )
        );
    }
}
