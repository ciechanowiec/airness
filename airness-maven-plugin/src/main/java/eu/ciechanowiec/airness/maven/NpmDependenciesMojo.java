package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.OwnedCoordinate;
import eu.ciechanowiec.airness.maven.NpmEvidence.NpmPackage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Every npm package a declared node project resolves is admitted by the license allowlist the Maven artifacts
 * answer to, no package a node project depends on directly trails its latest stable release by the freshness
 * bound, and every declared node project left the inventory that says so.
 *
 * <p>Bound at {@code package}, beside the Maven license and freshness checks, and fed by
 * {@code node-gate-npm-inventory}, which writes {@code dependencies.json} at {@code prepare-package}. It runs
 * in every module that declares a node project, because each module writes its own inventory. The harness
 * packages are held to the parent's version by {@code wiring}, not to their latest release.
 */
@Mojo(name = "npm-dependencies", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class NpmDependenciesMojo extends AbstractGovernanceMojo {

    @Override
    boolean applies() {
        return !NpmEvidence.of(this.project()).isEmpty();
    }

    @Override
    List<Findings> findings() {
        List<NpmEvidence> evidence = NpmEvidence.of(this.project());
        long elsewhere = NpmLicenses.elsewhere(evidence);
        if (elsewhere > 0) {
            this.getLog().info(
                elsewhere + " resolved npm package(s) are limited to other platforms and not installed here; their"
                    + " licenses are judged where they install"
            );
        }
        List<Findings> licenses = new NpmLicenses(MavenReportPolicy.from(this.project())).findings(evidence);
        NpmFreshness freshness = this.freshness(evidence);
        this.getLog().info("Version checking asked npm registries about " + freshness.scanned() + " package(s)");
        freshness.updates().forEach(update -> this.getLog().info("  " + update.report()));
        return Stream.concat(licenses.stream(), freshness.findings().stream()).toList();
    }

    private NpmFreshness freshness(List<NpmEvidence> evidence) {
        Map<String, NpmRegistries> registries = evidence.stream().collect(
            Collectors.toUnmodifiableMap(
                NpmEvidence::subject, entry -> NpmRegistries.of(entry.directory(), System.getenv())
            )
        );
        List<OwnedCoordinate> declared = evidence.stream().flatMap(
            entry -> readable(entry).stream().filter(NpmPackage::direct)
                .filter(found -> !HarnessVersions.harnessPackage(found.name()))
                .map(found -> NpmFreshness.npm(entry.subject(), found.name(), found.version()))
        ).toList();
        return NpmFreshness.of(
            declared,
            coordinate -> Optional.ofNullable(registries.get(coordinate.owner())).orElseThrow()
                .versions(coordinate.coordinate().artifactId())
        );
    }

    // An unreadable inventory is already the license verdict's finding, and freshness has nothing to ask about it.
    private static List<NpmPackage> readable(NpmEvidence entry) {
        try {
            return entry.packages();
        } catch (IllegalStateException _) {
            return List.of();
        }
    }
}
