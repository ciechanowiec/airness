package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.ReportLicenseEvidence;
import eu.ciechanowiec.airness.governance.ReportLicensePolicy;
import eu.ciechanowiec.airness.governance.ReportPackage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LicenseMetadataTest {

    private static final String COORDINATE = "xalan:serializer:jar:2.7.3";
    private static final String APACHE = "Apache-2.0";
    private static final String CONTENT = "verified artifact";

    @TempDir
    private Path directory;

    @Test
    void matchesOnlyTheReviewedVersionTypeAndClassifier() {
        LicenseMetadata metadata = LicenseMetadata.find(COORDINATE).orElseThrow();
        assertEquals(APACHE, metadata.license());
        assertTrue(metadata.evidence().declared().isEmpty());
        for (
            String coordinate : List.of(
                "xalan:serializer:jar:2.7.2", "xalan:serializer:jar:tests:2.7.3", "xalan:serializer:pom:2.7.3",
                "example:serializer:jar:2.7.3"
            )
        ) {
            assertTrue(LicenseMetadata.find(coordinate).isEmpty());
        }
    }

    @Test
    @SneakyThrows
    void acceptsOnlyTheVerifiedBytesAndExpectedSupplierDeclaration() {
        Path archive = Files.writeString(this.directory.resolve("artifact.jar"), CONTENT);
        LicenseMetadata metadata = fixture(List.of());
        metadata.verify(archive, List.of());
        assertThrows(IllegalStateException.class, () -> metadata.verify(archive, List.of("Proprietary")));
        Files.writeString(archive, CONTENT + " changed");
        assertThrows(IllegalStateException.class, () -> metadata.verify(archive, List.of()));
    }

    @Test
    @SneakyThrows
    void refusesMissingArtifactsAndSymbolicLinks() {
        LicenseMetadata metadata = fixture(List.of());
        Path archive = this.directory.resolve("absent.jar");
        assertThrows(IllegalStateException.class, () -> metadata.verify(archive, List.of()));
        Files.writeString(archive, CONTENT);
        Path link = Files.createSymbolicLink(this.directory.resolve("linked.jar"), archive);
        assertThrows(IllegalStateException.class, () -> metadata.verify(link, List.of()));
    }

    @Test
    @SneakyThrows
    void neverOverwritesAChangedDeclarationEvenWhenTheNewLicenseIsAllowed() {
        Path archive = Files.writeString(this.directory.resolve("artifact.jar"), CONTENT);
        LicenseMetadata metadata = fixture(List.of("Java HTML Tidy License"));
        metadata.verify(archive, List.of("Java HTML Tidy License"));
        assertThrows(IllegalStateException.class, () -> metadata.verify(archive, List.of("MIT")));
        assertThrows(IllegalStateException.class, () -> metadata.verify(archive, List.of()));
    }

    @Test
    void retainsReleaseTextsAndExplicitCorrectionProvenance() {
        LicenseMetadata serializer = LicenseMetadata.find(COORDINATE).orElseThrow();
        ReportLicenseEvidence serializerEvidence = evidence(serializer);
        assertEquals(serializer.sha256(), serializerEvidence.sha256());
        assertEquals(serializer.license(), serializerEvidence.license());
        assertTrue(serializerEvidence.declared().isEmpty(), "the supplier declares nothing");
        assertTrue(String.join("\n", serializerEvidence.texts().values()).contains("Apache Xalan (Xalan serializer)"));
        LicenseMetadata tidy = LicenseMetadata.find("com.github.jtidy:jtidy:jar:1.0.5").orElseThrow();
        assertEquals("HTMLTIDY", tidy.license());
        assertTrue(String.join("\n", evidence(tidy).texts().values()).contains("commercial products"));
        assertEquals(List.of("Java HTML Tidy License"), evidence(tidy).declared());
        LicenseMetadata plexus = LicenseMetadata.find("org.codehaus.plexus:plexus-i18n:jar:1.0-beta-10").orElseThrow();
        assertTrue(
            String.join("\n", evidence(plexus).texts().values())
                .contains("Licensed under the Apache License, Version 2.0")
        );
        assertThrows(IllegalStateException.class, () -> LicenseMetadata.resource("absent-license.txt"));
    }

    @Test
    void keepsCorrectedLicensesSubjectToTheInheritedAllowlist() {
        LicenseMetadata metadata = LicenseMetadata.find(COORDINATE).orElseThrow();
        ReportPackage entry = new ReportPackage(COORDINATE, List.of(metadata.license()), List.of(), metadata.notices());
        assertTrue(new ReportLicensePolicy(Set.of("MIT"), Map.of()).refuses(entry));
        assertFalse(new ReportLicensePolicy(Set.of(APACHE), Map.of()).refuses(entry));
        assertTrue(
            new ReportLicensePolicy(Set.of(APACHE), Map.of()).refuses(
                new ReportPackage("unknown:artifact:jar:1", List.of(), List.of(), Map.of())
            )
        );
    }

    private static ReportLicenseEvidence evidence(LicenseMetadata metadata) {
        return ReportLicenseEvidence.decode(String.join("", metadata.notices().values())).orElseThrow();
    }

    private static LicenseMetadata fixture(List<String> declared) {
        return new LicenseMetadata(
            COORDINATE, ReportJson.hash(CONTENT), APACHE,
            new LicenseEvidence(declared, "https://example.test/release", "Missing supplier metadata", Map.of())
        );
    }
}
