package eu.ciechanowiec.airness.governance;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A finding as classified by the vulnerability scanner, with the account that covers it, if any.
 *
 * @param identity    advisory identifier
 * @param severity    the rating as the database wrote it
 * @param artifact    the package URL, or the file name, of the affected artifact
 * @param modules     every reactor module whose scan reported the finding
 * @param description the advisory's description
 * @param mitigation  the recorded account, or empty while the advisory is open
 * @param aliases     the advisory's other identifiers, such as the CVE a distribution's advisory answers
 */
public record ReportAdvisory(
    String identity, String severity, String artifact, List<String> modules, String description,
    Optional<ReportMitigation> mitigation, List<String> aliases
) {

    /**
     * Orders advisories the way a reviewer triages: most severe first, then by artifact and identifier.
     */
    public static final Comparator<ReportAdvisory> TRIAGE = Comparator.comparing(ReportAdvisory::level)
        .thenComparing(ReportAdvisory::artifact).thenComparing(ReportAdvisory::identity);

    public ReportAdvisory {
        modules = List.copyOf(modules);
        aliases = List.copyOf(aliases);
    }

    /**
     * Whether no recorded account covers this advisory, which fails the build.
     *
     * @return whether the advisory is open
     */
    public boolean open() {
        return this.mitigation.isEmpty();
    }

    /**
     * Whether this is a container image finding no release of the image can fix yet, which the build accepts until
     * one can.
     *
     * @return whether the advisory awaits a fix
     */
    public boolean awaitingFix() {
        return this.mitigation.map(ReportMitigation::kind).filter(ReportMitigation.Kind.AWAITING_FIX::equals)
            .isPresent();
    }

    /**
     * Whether a recorded mitigation covers this advisory.
     *
     * @return whether the advisory is mitigated
     */
    public boolean mitigated() {
        return !this.open() && !this.awaitingFix();
    }

    /**
     * The rating on the report's one scale.
     *
     * @return the level
     */
    public ReportSeverity level() {
        return ReportSeverity.of(this.severity);
    }

    /**
     * The same finding reported by another module's scan.
     *
     * @param other the other module's report of this advisory
     * @return this advisory with both modules
     */
    public ReportAdvisory merge(ReportAdvisory other) {
        List<String> both = Stream.concat(this.modules.stream(), other.modules.stream()).distinct().sorted().toList();
        List<String> names = Stream.concat(this.aliases.stream(), other.aliases.stream()).distinct().sorted().toList();
        return new ReportAdvisory(
            this.identity, this.severity, this.artifact, both, this.description, this.mitigation, names
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
}
