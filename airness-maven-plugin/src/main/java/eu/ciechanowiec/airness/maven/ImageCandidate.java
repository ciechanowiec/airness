package eu.ciechanowiec.airness.maven;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A newer release of a base image and the advisories its own packages still carry.
 *
 * @param reference the release, pinned by its digest
 * @param carried   each advisory the release carries, as a package identity and an advisory identifier
 */
record ImageCandidate(String reference, Set<String> carried) {

    ImageCandidate {
        carried = Set.copyOf(carried);
    }

    /**
     * Reads what a release carries from its own findings.
     *
     * @param reference the release
     * @param inventory what the release holds
     * @param findings  the findings OSV lists for it
     * @return the candidate
     */
    static ImageCandidate of(String reference, ImageInventory inventory, List<OsvAdvisory> findings) {
        return new ImageCandidate(
            reference, findings.stream().flatMap(
                finding -> inventory.held(finding.advisory().artifact()).stream().flatMap(
                    held -> finding.identifiers().stream().map(identifier -> key(held, identifier))
                )
            ).collect(Collectors.toUnmodifiableSet())
        );
    }

    /**
     * Whether the release no longer carries a finding of a package the current image holds: it holds no package of
     * that identity, or holds one none of whose findings is that advisory.
     *
     * @param held    the package the current image holds
     * @param finding the finding against it
     * @return whether moving to this release answers the finding
     */
    boolean answers(ImagePackage held, OsvAdvisory finding) {
        return finding.identifiers().stream().noneMatch(identifier -> this.carried.contains(key(held, identifier)));
    }

    private static String key(ImagePackage held, String identifier) {
        return held.identity() + ' ' + identifier;
    }
}
