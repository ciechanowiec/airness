package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ReportAdvisory;
import java.util.Collection;
import java.util.Set;

/**
 * What a project does about findings a shipped image holds, printed while any of them is still open.
 *
 * <p>An npm package the image holds can still be mitigated by the node project's allowlist, which is applied after
 * the image is judged, so a repair names the findings it answers and is printed only for the open ones.
 *
 * @param text     the repair, naming the module, the Dockerfile and what to change
 * @param findings the keys of the advisories the repair answers
 */
record ImageRepair(String text, Set<String> findings) {

    ImageRepair {
        findings = Set.copyOf(findings);
    }

    /**
     * Whether the repair still answers an open finding.
     *
     * @param advisories every advisory of the module, mitigated ones included
     * @return whether any advisory the repair answers is open
     */
    boolean needed(Collection<ReportAdvisory> advisories) {
        return advisories.stream().filter(ReportAdvisory::open).map(ReportAdvisory::key)
            .anyMatch(this.findings::contains);
    }
}
