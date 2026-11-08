package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * An npm package is refused by name, a scope or prefix by its asterisk, and a relicensed one only from the release
 * that changed its license.
 */
class NpmBlocklistTest {

    private static final String WEB = "web";

    @Test
    void refusesByNameByPrefixAndFromAFloor() {
        assertTrue(NpmBlocklist.refusal("mongodb", "6.0.0", WEB).orElseThrow().contains("use PostgreSQL"));
        assertTrue(NpmBlocklist.refusal("@ckeditor/ckeditor5-core", "41.0.0", WEB).isPresent());
        assertTrue(NpmBlocklist.refusal("n8n-nodes-base", "1.0.0", WEB).isPresent());
        assertTrue(NpmBlocklist.refusal("ua-parser-js", "2.0.1", WEB).isPresent());
        assertTrue(NpmBlocklist.refusal("ua-parser-js", "1.0.40", WEB).isEmpty(), "the open release line stays");
        assertTrue(NpmBlocklist.refusal("left-pad", "1.3.0", WEB).isEmpty());
    }

    @Test
    void statesAVendorsLicenseAsTheMavenBlocklistStatesIt() {
        assertTrue(NpmBlocklist.refusal("mongoose", "8.0.0", WEB).orElseThrow().contains(BlocklistEntries.MONGODB));
        assertTrue(
            NpmBlocklist.refusal("@elastic/elasticsearch", "8.0.0", WEB).orElseThrow()
                .contains(BlocklistEntries.ELASTIC)
        );
        assertTrue(
            NpmBlocklist.refusal("elastic-apm-node", "4.0.0", WEB).orElseThrow().contains(BlocklistEntries.ELASTIC_APM)
        );
        assertTrue(NpmBlocklist.refusal("pdf2pic", "3.0.0", WEB).orElseThrow().contains(BlocklistEntries.GHOSTSCRIPT));
    }
}
