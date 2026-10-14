package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void publishesOnlyWhenAllModulesArriveAndOnlyOncePerSession() {
        SessionData data = new DefaultSessionData();
        List<String> required = List.of("sample:first:jar:1", "sample:second:jar:1");
        assertTrue(ReportSession.collect(data, List.of(module("first", "compile", "MIT")), required).isEmpty());
        List<ReportModule> complete = ReportSession.collect(data, List.of(module("second", "test", "MIT")), required)
            .orElseThrow();
        assertEquals(required, complete.stream().map(ReportModule::coordinate).toList());
        assertTrue(ReportSession.collect(data, complete, required).isEmpty());
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

    private static ReportModule module(String name, String scope, String license) {
        ReportPackage entry = new ReportPackage(
            "example:library:jar:1.0", List.of(license),
            List.of(new ReportUse(name, scope, "Direct")), Map.of("LICENSE", "Published license")
        );
        return new ReportModule("sample:" + name + ":jar:1", List.of(entry), List.of(), Map.of());
    }
}
