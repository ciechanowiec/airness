package eu.ciechanowiec.airness.governance;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A finding as classified by the vulnerability scanner, with the mitigation that covers it, if any.
 *
 * @param identity    advisory identifier
 * @param severity    scanner severity
 * @param artifact    the package URL, or the file name, of the affected artifact
 * @param modules     every reactor module whose scan reported the finding
 * @param description the advisory's description
 * @param mitigation  the applied exception, or empty while the advisory is open
 */
public record ReportAdvisory(
    String identity, String severity, String artifact, List<String> modules, String description,
    Optional<ReportMitigation> mitigation
) {

    /**
     * Orders advisories the way a reviewer triages: most severe first, then by artifact and identifier.
     */
    public static final Comparator<ReportAdvisory> TRIAGE = Comparator.comparingInt(ReportAdvisory::rank)
        .reversed().thenComparing(ReportAdvisory::artifact).thenComparing(ReportAdvisory::identity);

    private static final List<String> SCALE = List.of("info", "low", "medium", "high", "critical");

    public ReportAdvisory {
        modules = List.copyOf(modules);
    }

    /**
     * Whether no recorded mitigation covers this advisory, which fails the build.
     *
     * @return whether the advisory is open
     */
    public boolean open() {
        return this.mitigation.isEmpty();
    }

    /**
     * The same finding reported by another module's scan.
     *
     * @param other the other module's report of this advisory
     * @return this advisory with both modules
     */
    public ReportAdvisory merge(ReportAdvisory other) {
        List<String> both = Stream.concat(this.modules.stream(), other.modules.stream()).distinct().sorted().toList();
        return new ReportAdvisory(
            this.identity, this.severity, this.artifact, both, this.description, this.mitigation
        );
    }

    /**
     * The key under which reports of one finding from several modules are merged.
     *
     * @return identifier and artifact
     */
    public String key() {
        return this.identity + ' ' + this.artifact;
    }

    // A severity the scale does not know ranks above critical, so it is read first rather than last.
    private int rank() {
        String level = this.severity.toLowerCase(Locale.ROOT);
        int rank = SCALE.indexOf("moderate".equals(level) ? "medium" : level);
        return rank < 0 ? SCALE.size() : rank;
    }
}
