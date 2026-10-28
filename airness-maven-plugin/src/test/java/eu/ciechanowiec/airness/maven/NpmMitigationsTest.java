package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.ciechanowiec.airness.governance.ReportAdvisory;
import eu.ciechanowiec.airness.governance.ReportMitigation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Project declarations mitigate only the named package and advisory, with complete, current evidence.
 */
class NpmMitigationsTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ID = "GHSA-0000-0000-0001";
    private static final String ALIAS = "CVE-2026-12345";
    private static final String PACKAGE = "@scope/kit";
    private static final String PURL = "pkg:npm/%40scope/kit@2.0.0";
    private static final Map<String, String> DECLARATION = Map.of(
        "package", PACKAGE, "advisory", ALIAS, "reason", "Only the isolated build loads this parser.",
        "mitigation", "Upgrade the build plugin when its compatible fix is published.", "addedOn", "2026-10-28"
    );

    @TempDir
    private Path directory;

    @Test
    void retainsTheFindingWithItsProjectMitigationAndMatchesTheDatabaseAlias() {
        NpmMitigations policy = this.policy(List.of(DECLARATION));
        ReportAdvisory report = policy.apply(List.of(finding(PURL))).getFirst();
        assertEquals(ID, report.identity());
        assertEquals(PURL, report.artifact());
        assertFalse(report.open());
        ReportMitigation mitigation = report.mitigation().orElseThrow();
        assertEquals(DECLARATION.get("reason"), mitigation.reason());
        assertEquals(DECLARATION.get("mitigation"), mitigation.plan());
        assertEquals("2026-10-28", mitigation.addedOn());
        assertFalse(mitigation.harness());
    }

    @Test
    void leavesAnUnrelatedPackageOpenAndRefusesUnusedEntries() {
        NpmMitigation entry = NpmMitigation.of(JSON.valueToTree(DECLARATION));
        assertFalse(entry.matches(finding("pkg:npm/%40scope/kitchen@2.0.0")));
        assertTrue(new NpmMitigations(this.directory, List.of()).apply(List.of(finding(PURL))).getFirst().open());
        IllegalStateException unused = assertThrows(
            IllegalStateException.class, () -> this.policy(List.of(DECLARATION)).apply(List.of())
        );
        String message = Objects.requireNonNull(unused.getMessage());
        assertTrue(message.contains("unused airness.vulnerabilityAllowlist"));
        assertTrue(message.contains(PACKAGE + ' ' + ALIAS));
    }

    @Test
    void acceptsACanonicalIdentifierButRefusesOverlappingAliasDeclarations() {
        Map<String, String> canonical = changed("advisory", ID);
        assertFalse(this.policy(List.of(canonical)).apply(List.of(finding(PURL))).getFirst().open());
        assertThrows(
            IllegalStateException.class, () -> this.policy(List.of(DECLARATION, canonical)).apply(
                List.of(finding(PURL))
            )
        );
    }

    @Test
    void refusesMissingFieldsAndNonLiteralSelectors() {
        for (String field : DECLARATION.keySet()) {
            assertThrows(IllegalStateException.class, () -> this.policy(List.of(changed(field, " "))), field);
        }
    }

    @Test
    void refusesNonLiteralSelectorsAndInvalidDates() {
        for (
            Map<String, String> value : List.of(
                changed("package", "*"), changed("advisory", "GHSA-.*"), changed("addedOn", "2026-02-29"),
                changed("addedOn", "+12026-01-01")
            )
        ) {
            assertThrows(IllegalStateException.class, () -> this.policy(List.of(value)));
        }
        assertThrows(IllegalStateException.class, () -> this.policy(Map.of()));
        assertThrows(IllegalStateException.class, () -> this.policy(List.of(false)));
    }

    @Test
    @SneakyThrows
    void readsNoExceptionWhenNoneIsDeclaredAndMatchesUnscopedPackages() {
        Files.writeString(this.directory.resolve("package.json"), "{}");
        assertEquals(List.of(), NpmMitigations.read(this.directory).apply(List.of()));
        NpmMitigation entry = NpmMitigation.of(JSON.valueToTree(changed("package", "kit")));
        assertTrue(entry.matches(finding("pkg:npm/kit@2.0.0")));
        assertFalse(entry.matches(finding(PURL)));
    }

    private static Map<String, String> changed(String key, String value) {
        return DECLARATION.entrySet().stream().collect(
            Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> entry.getKey().equals(key) ? value : entry.getValue()
            )
        );
    }

    @SneakyThrows
    private NpmMitigations policy(Object entries) {
        Files.writeString(
            this.directory.resolve("package.json"), ReportJson.encode(
                Map.of("airness", Map.of("vulnerabilityAllowlist", entries))
            )
        );
        return NpmMitigations.read(this.directory);
    }

    private static OsvAdvisory finding(String purl) {
        JsonNode document = JSON.valueToTree(Map.of("id", ID, "aliases", List.of(ALIAS), "summary", "A finding"));
        return OsvAdvisory.of(document, purl, "web");
    }
}
