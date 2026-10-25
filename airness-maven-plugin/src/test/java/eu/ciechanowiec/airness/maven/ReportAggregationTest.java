package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.ReportPackage;
import eu.ciechanowiec.airness.governance.ReportUse;
import java.util.List;
import java.util.Map;
import org.eclipse.aether.DefaultSessionData;
import org.eclipse.aether.SessionData;
import org.junit.jupiter.api.Test;

class ReportAggregationTest {

    private static final int SHA256_LENGTH = 64;

    @Test
    void rejectsConflictingNoticeBytesForTheSamePublishedArtifact() {
        ReportModule first = module("first", "compile", "MIT");
        ReportPackage original = first.packages().getFirst();
        ReportPackage changed = new ReportPackage(
            original.coordinate(), original.licenses(), original.uses(),
            Map.of("LICENSE", "different bytes")
        );
        ReportModule second = new ReportModule("sample:second:jar:1", List.of(changed), List.of(), Map.of());
        assertThrows(IllegalStateException.class, () -> ReportAggregation.packages(List.of(first, second)));
    }

    @Test
    void mergesSharedCoordinatesWithoutLosingModuleScopes() {
        ReportModule first = module("first", "compile", "MIT");
        ReportModule second = module("second", "test", "MIT");
        List<ReportPackage> packages = ReportAggregation.packages(List.of(first, second));
        assertEquals(1, packages.size());
        assertEquals(List.of("compile", "test"), packages.getFirst().uses().stream().map(ReportUse::scope).toList());
        assertTrue(packages.getFirst().production());
    }

    @Test
    void refusesConflictingSupplierDeclarationsForTheSameCoordinate() {
        assertThrows(
            IllegalStateException.class,
            () -> ReportAggregation.packages(
                List.of(module("first", "compile", "MIT"), module("second", "test", "BSD"))
            )
        );
    }

    @Test
    void closesOnlyWhenEveryAwaitedModuleCompletesAndOnlyOncePerSession() {
        SessionData data = new DefaultSessionData();
        List<String> awaited = List.of("sample:first:jar:1", "sample:second:jar:1");
        assertFalse(CompletionSession.complete(data, "sample:first:jar:1", awaited));
        assertTrue(CompletionSession.complete(data, "sample:second:jar:1", awaited));
        assertFalse(CompletionSession.complete(data, "sample:second:jar:1", awaited));
        assertThrows(IllegalStateException.class, () -> ReportSession.source(data));
    }

    @Test
    void embedsAllResolvedVersionsAndSupplierLicenseNamesInTheAggregateSbom() {
        List<ReportModule> modules = List.of(module("first", "compile", "MIT License"));
        String sbom = ReportSbom.assemble(ReportAggregation.packages(modules), modules);
        assertTrue(sbom.contains("\"bomFormat\":\"CycloneDX\""));
        assertTrue(sbom.contains("\"name\":\"MIT License\""));
        assertTrue(sbom.contains("example:library:jar:1.0"));
    }

    @Test
    void mergesTheNpmDocumentsAndTheToolchainIntoTheOneSbom() {
        ReportPackage npm = new ReportPackage(
            "pkg:npm/left-pad@1.3.0", List.of("SSPL-1.0"), List.of(new ReportUse("web", "npm", "direct")), Map.of()
        );
        ReportPackage node = new ReportPackage(
            "pkg:generic/node@26.11.1?checksum=sha256:" + "b".repeat(SHA256_LENGTH), List.of("MIT"),
            List.of(new ReportUse("web", "build", "direct")), Map.of()
        );
        ReportModule web = new ReportModule(
            "sample:web:pom:1", List.of(npm, node), List.of(), Map.of(
                "Graph", "{\"sample:web:pom:1\": [\"pkg:npm/web@1.0.0\"]}",
                NpmReportInputs.DOCUMENTS, "[" + NpmFixture.SBOM + "]"
            )
        );
        List<ReportModule> modules = List.of(module("first", "compile", "MIT"), web);
        String sbom = ReportSbom.assemble(ReportAggregation.packages(modules), modules);
        assertTrue(sbom.contains("\"purl\":\"pkg:npm/%40scope/kit@2.0.0\""), "the npm document's packages");
        assertTrue(sbom.contains("\"bom-ref\":\"pkg:npm/web@1.0.0\""), "the node project itself");
        assertTrue(sbom.contains("\"content\":\"" + "b".repeat(SHA256_LENGTH) + "\""), "the pinned Node and its hash");
        assertTrue(sbom.contains("example:library:jar:1.0"), "beside the Maven artifacts");
        assertEquals(1, sbom.split("\"bom-ref\":\"pkg:npm/left-pad@1.3.0\"", -1).length - 1, "listed once");
        assertTrue(sbom.contains("\"ref\":\"sample:web:pom:1\""), "one graph joins the module to its node project");
    }

    private static ReportModule module(String name, String scope, String license) {
        ReportPackage entry = new ReportPackage(
            "example:library:jar:1.0", List.of(license),
            List.of(new ReportUse(name, scope, "Direct")), Map.of("LICENSE", "Published license")
        );
        return new ReportModule("sample:" + name + ":jar:1", List.of(entry), List.of(), Map.of());
    }
}
