package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The document that records the advisories a project cannot reach.
 *
 * <p>The scanner already refuses a rule that suppresses nothing, so an entry cannot outlive the finding
 * it was written for. What it cannot ask is why the entry was written, and an exception whose reason
 * nobody wrote down is one nobody can retire: a later reader can tell that it still matches something,
 * and nothing else.
 *
 * <p>So an entry says why this project cannot reach the advisory, and says when it was decided. The date
 * is what turns a permanent exception into a dated one. An advisory a project could not reach in 2026 is
 * not thereby an advisory it cannot reach now, and without a date nothing in the file distinguishes a
 * judgement made last week from one made before the code around it was rewritten.
 *
 * <p>An entry also names an advisory rather than only a package. A rule carrying a package and no
 * identifier excuses everything that will ever be published about that package, including the advisories
 * nobody has looked at yet, which is the opposite of what a recorded exception is.
 */
public final class SuppressionDocument {

    private static final Pattern DATE = Pattern.compile("\\b\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])\\b");
    private static final List<String> ADVISORY = List.of("cve", "vulnerabilityName", "cpe", "ghsa");

    private final List<Element> entries;

    /**
     * Reads one suppression document.
     *
     * @param document the file the project declared
     */
    public SuppressionDocument(Path document) {
        this.entries = Xml.children(read(document).getDocumentElement(), "suppress");
    }

    /**
     * Every way an entry falls short of recording a decision.
     *
     * @return problems ordered by their message
     */
    public List<String> problems() {
        return Stream.of(this.unexplained(), this.undated(), this.unnamed())
            .flatMap(List::stream)
            .sorted()
            .toList();
    }

    private List<String> unexplained() {
        return this.entries.stream()
            .filter(entry -> notes(entry).isEmpty())
            .map(entry -> problem(entry, "say why this project cannot reach the advisory"))
            .toList();
    }

    private List<String> undated() {
        return this.entries.stream()
            .filter(entry -> !notes(entry).isEmpty())
            .filter(entry -> !DATE.matcher(notes(entry)).find())
            .map(entry -> problem(entry, "record the date the exception was made, as YYYY-MM-DD"))
            .toList();
    }

    private List<String> unnamed() {
        return this.entries.stream()
            .filter(entry -> ADVISORY.stream().noneMatch(tag -> Xml.text(entry, tag).isPresent()))
            .map(entry -> problem(entry, "name the advisory it excuses, not only the package it sits in"))
            .toList();
    }

    private static String problem(Node entry, String requirement) {
        return "Dependency-Check suppression for " + subject(entry) + ": " + requirement;
    }

    private static String subject(Node entry) {
        return Stream.concat(ADVISORY.stream(), Stream.of("packageUrl"))
            .map(tag -> Xml.text(entry, tag))
            .flatMap(Optional::stream)
            .findFirst()
            .orElse("an entry naming nothing at all");
    }

    private static String notes(Node entry) {
        return Xml.text(entry, "notes").orElse("");
    }

    private static Document read(Path document) {
        try {
            return Xml.parse(Files.readString(document));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + document, exception);
        }
    }
}
