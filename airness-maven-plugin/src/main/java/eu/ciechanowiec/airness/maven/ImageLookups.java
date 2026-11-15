package eu.ciechanowiec.airness.maven;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Where the image check reads its verdicts from.
 *
 * @param osv      the vulnerability database
 * @param images   the host's Docker client with the pinned scanner
 * @param releases the newer releases of a base on Docker Hub
 * @param today    the date the harness records its accounts on
 */
record ImageLookups(OsvScan osv, DockerImages images, Function<String, Optional<String>> releases, LocalDate today) {

    private static final char PATH_SEPARATOR = '/';
    private static final char TAG_SEPARATOR = ':';
    private static final char DIGEST_SEPARATOR = '@';

    List<OsvAdvisory> findings(ImageInventory inventory, ImageEvidence evidence, String module) {
        return this.osv.findingsOf(inventory.queries(evidence.dockerfile()), module);
    }

    ImageCandidate candidate(String reference, ImageEvidence evidence, String module) {
        ImageInventory release = ImageInventory.of(this.images.packages(reference));
        return ImageCandidate.of(reference, release, this.findings(release, evidence, module));
    }

    // The release the base's tag names today, when the Dockerfile pins another, then the newest release on Docker Hub.
    Stream<String> candidates(String base) {
        Stream<String> rebuilt = tagged(base).map(this.images::current)
            .filter(current -> !digest(current).equals(digest(base))).stream();
        Stream<String> newer = this.releases.apply(base).map(this.images::current).stream();
        return Stream.concat(rebuilt, newer).distinct();
    }

    // The reference without its digest, when it names a tag.
    private static Optional<String> tagged(String reference) {
        int at = reference.indexOf(DIGEST_SEPARATOR);
        String named = at < 0 ? reference : reference.substring(0, at);
        return named.lastIndexOf(TAG_SEPARATOR) > named.lastIndexOf(PATH_SEPARATOR) ? Optional.of(named)
            : Optional.empty();
    }

    private static String digest(String reference) {
        int at = reference.indexOf(DIGEST_SEPARATOR);
        return at < 0 ? "" : reference.substring(at + 1);
    }
}
