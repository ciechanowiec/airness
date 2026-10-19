package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.List;

/**
 * The exact authored tree a report describes.
 *
 * @param commit       complete source commit
 * @param fingerprint  committable content digest
 * @param generatedAt  generation timestamp in UTC
 * @param draftReasons reasons this report is not complete release evidence
 */
public record ReportSource(String commit, String fingerprint, String generatedAt, List<String> draftReasons) {

    /**
     * The draft reason a working tree with uncommitted changes carries.
     */
    public static final String DIRTY = "The working tree has uncommitted changes, so no commit describes this source.";

    public ReportSource {
        draftReasons = List.copyOf(draftReasons);
    }

    /**
     * Whether the working tree had uncommitted changes when the report was generated.
     *
     * @return whether no commit describes the reported source
     */
    public boolean dirty() {
        return this.draftReasons.contains(DIRTY);
    }

    /**
     * Reads the source identity directly from Git.
     *
     * @param root        repository root
     * @param generatedAt supplied UTC time
     * @return source identity and dirty-tree draft status
     */
    public static ReportSource read(Path root, String generatedAt) {
        String commit = GitPlumbing.run(root, List.of("rev-parse", "HEAD")).strip();
        boolean dirty = !GitPlumbing.run(root, List.of("status", "--porcelain")).isBlank();
        List<String> reasons = dirty ? List.of(DIRTY) : List.of();
        return new ReportSource(commit, TreeFingerprint.from(root), generatedAt, reasons);
    }
}
