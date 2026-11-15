package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A package URL and a distribution's source package are each asked the way OSV reads them, and a fixed version
 * counts only where it belongs to what was asked.
 */
class OsvQueryTest {

    private static final String ARTIFACT = "pkg:apk/alpine/libcrypto3@3.5.7-r0?distro=3.24.1";
    private static final OsvQuery OPENSSL = OsvQuery.system(ARTIFACT, "Alpine:v3.24", "openssl", "3.5.7-r0");

    @Test
    void asksAPackageUrlOrASourcePackageAtItsVersion() {
        assertAll(
            () -> assertEquals(
                Map.of("package", Map.of("purl", "pkg:npm/glob@13.0.6")), OsvQuery.purl("pkg:npm/glob@13.0.6").body("")
            ),
            () -> assertEquals(
                Map.of(
                    "package", Map.of("name", "openssl", "ecosystem", "Alpine:v3.24"), "version", "3.5.7-r0",
                    "page_token", "page-2"
                ),
                OPENSSL.body("page-2")
            ),
            () -> assertEquals(
                ARTIFACT, OPENSSL.artifact(), "the finding is reported against the package the image holds"
            )
        );
    }

    @Test
    void countsAFixedVersionOnlyInTheEntryOfTheReleaseAndSourceAsked() {
        JsonNode fixedHere = advisory("Alpine:v3.24", "openssl", "{\"fixed\": \"3.5.8-r0\"}");
        JsonNode fixedElsewhere = advisory("Alpine:v3.23", "openssl", "{\"fixed\": \"3.5.8-r0\"}");
        JsonNode otherSource = advisory("Alpine:v3.24", "zlib", "{\"fixed\": \"1.4-r0\"}");
        JsonNode unfixed = advisory("Alpine:v3.24", "openssl", "{\"introduced\": \"0\"}");
        assertAll(
            () -> assertTrue(OPENSSL.fixedIn(fixedHere)),
            () -> assertFalse(OPENSSL.fixedIn(fixedElsewhere)),
            () -> assertFalse(OPENSSL.fixedIn(otherSource)),
            () -> assertFalse(OPENSSL.fixedIn(unfixed)),
            () -> assertTrue(
                OsvQuery.purl("pkg:npm/glob@13.0.6").fixedIn(fixedElsewhere), "a package URL reads any range"
            )
        );
    }

    private static JsonNode advisory(String ecosystem, String name, String event) {
        return ReportJson.parse(
            "{\"id\": \"ALPINE-1\", \"affected\": [{\"package\": {\"ecosystem\": \"" + ecosystem + "\", \"name\": \""
                + name + "\"}, \"ranges\": [{\"type\": \"ECOSYSTEM\", \"events\": [{\"introduced\": \"0\"}, " + event
                + "]}]}]}"
        );
    }
}
