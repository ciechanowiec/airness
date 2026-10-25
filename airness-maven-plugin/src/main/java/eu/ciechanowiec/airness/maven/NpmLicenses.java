package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.NpmBlocklist;
import eu.ciechanowiec.airness.governance.ReportLicensePolicy;
import eu.ciechanowiec.airness.governance.ReportPackage;
import eu.ciechanowiec.airness.maven.NpmEvidence.NpmPackage;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Holds every npm package a node project resolves to the license allowlist the Maven artifacts answer to.
 *
 * <p>One allowlist, written once in the inherited license check, judges both ecosystems. An npm package
 * declares an SPDX expression, which is read as its alternatives: the package passes when the allowlist admits
 * one of them, and an alternative joining licenses with {@code AND} only when it admits every one. A node
 * project without a readable {@code dependencies.json} fails here rather than passing as an empty inventory. A
 * package limited to other platforms is resolved but not installed on this host, so no license was read for it,
 * and it is judged where it installs. An inventory written past a failing {@code wiring} gate, which only a
 * report-only build gets past, is named as such, so a reader knows the packages it judges are not the ones the
 * harness vouches for.
 *
 * @param policy the inherited allowlist and its spellings
 */
record NpmLicenses(ReportLicensePolicy policy) {

    private static final String MISSING = "npm dependency evidence every declared node project must leave (run the"
        + " build through package; node-gate-npm-inventory writes it at prepare-package)";
    private static final String UNWIRED = "npm evidence written past a failing wiring gate, so it describes packages"
        + " the harness does not vouch for (repair the wiring findings above)";
    private static final String BLOCKED = "npm packages Airness refuses by name, whatever license they declare";
    private static final String REFUSED = "npm packages whose declared license the allowlist admits under no"
        + " alternative (replace the package, or add a license to the allowlist once its terms have been read)";

    /**
     * The four verdicts over the node projects of one module.
     *
     * @param evidence the evidence locations of the module's node projects
     * @return the missing evidence, the unwired evidence, the refused licenses and the packages refused by name
     */
    List<Findings> findings(Collection<NpmEvidence> evidence) {
        List<Inventory> read = evidence.stream().map(Inventory::of).toList();
        List<String> missing = read.stream().flatMap(entry -> entry.problem().stream()).toList();
        List<String> unwired = evidence.stream().filter(NpmEvidence::wiringFailed).map(NpmEvidence::subject).toList();
        List<String> refused = read.stream().flatMap(
            entry -> entry.packages().stream().filter(NpmPackage::installed).filter(this::refuses)
                .map(
                    found -> entry.evidence().subject() + ": " + found.name() + '@' + found.version() + " declares "
                        + Optional.of(found.license()).filter(text -> !text.isBlank()).orElse("no license")
                )
        ).toList();
        List<String> blocked = read.stream().flatMap(
            entry -> entry.packages().stream().flatMap(
                found -> NpmBlocklist.refusal(found.name(), found.version(), entry.evidence().subject()).stream()
            )
        ).toList();
        return List.of(
            new Findings(MISSING, missing), new Findings(UNWIRED, unwired), new Findings(REFUSED, refused),
            new Findings(BLOCKED, blocked)
        );
    }

    /**
     * How many resolved packages this host did not install, whose licenses are judged where they install.
     *
     * @param evidence the evidence locations of the module's node projects
     * @return the count over every readable inventory
     */
    static long elsewhere(Collection<NpmEvidence> evidence) {
        return evidence.stream().map(Inventory::of).flatMap(entry -> entry.packages().stream())
            .filter(found -> !found.installed()).count();
    }

    private boolean refuses(NpmPackage found) {
        return this.policy.refuses(
            new ReportPackage(
                found.purl(), SpdxLicenses.alternatives(found.license()), List.of(), Map
                    .of()
            )
        );
    }

    /**
     * One node project's inventory as read: its packages, or why there are none.
     *
     * @param evidence the evidence location
     * @param packages the packages, empty when the inventory could not be read
     * @param problem  why the inventory could not be read, empty when it could
     */
    private record Inventory(NpmEvidence evidence, List<NpmPackage> packages, Optional<String> problem) {

        static Inventory of(NpmEvidence evidence) {
            try {
                return new Inventory(evidence, evidence.packages(), Optional.empty());
            } catch (IllegalStateException exception) {
                return new Inventory(evidence, List.of(), Optional.ofNullable(exception.getMessage()));
            }
        }
    }
}
