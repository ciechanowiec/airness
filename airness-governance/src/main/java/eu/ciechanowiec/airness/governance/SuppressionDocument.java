package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The document that records the advisories a project cannot reach.
 *
 * <p>The scanner already refuses a rule that suppresses nothing, so an entry cannot outlive the finding
 * it was written for. What it cannot ask is why the entry was written, and an exception whose reason
 * nobody wrote down is one nobody can retire: a later reader can tell that it still matches something,
 * and nothing else.
 *
 * <p>So an entry says why this project cannot reach the advisory, states a mitigation plan after a
 * {@code Mitigation:} marker, and says when it was decided. The plan is what an outside reviewer of the
 * compliance report reads beside the advisory: what limits the risk now, and what will remove the
 * vulnerable package. The date
 * is what turns a permanent exception into a dated one. An advisory a project could not reach in 2026 is
 * not thereby an advisory it cannot reach now, and without a date nothing in the file distinguishes a
 * judgement made last week from one made before the code around it was rewritten.
 *
 * <p>An entry also names an advisory rather than only a package. A rule carrying a package and no
 * identifier excuses everything that will ever be published about that package, including the advisories
 * nobody has looked at yet, which is the opposite of what a recorded exception is. Advisory names
 * are literal and non-empty. A product, weakness, score threshold or advisory pattern can excuse
 * findings the author never named, so those selectors are refused even beside a specific advisory.
 * Entries inside groups and namespace-prefixed entries carry the same obligations, including their
 * own explanation and date.
 */
public final class SuppressionDocument {

    private static final Set<String> ADVISORY = Set.of("cve", "vulnerabilityName");
    private static final Set<String> BROAD = Set.of(
        "cpe", "cwe", "cvssBelow", "cvssV2Below", "cvssV3Below", "cvssV4Below"
    );
    private static final Set<String> LITERAL = Set.of("false", "0");

    private final List<Element> entries;

    /**
     * Reads one suppression document.
     *
     * @param document the file the project declared
     */
    public SuppressionDocument(Path document) {
        this.entries = elements(read(document).getElementsByTagName("*"))
            .filter(element -> "suppress".equals(name(element))).toList();
    }

    /**
     * Every way an entry falls short of recording a decision.
     *
     * @return problems ordered by their message
     */
    public List<String> problems() {
        return Stream.of(this.unexplained(), this.unplanned(), this.undated(), this.unnamed(), this.selectors())
            .flatMap(List::stream)
            .sorted()
            .toList();
    }

    private List<String> unexplained() {
        return this.entries.stream()
            .filter(entry -> noteText(entry).isEmpty())
            .map(entry -> problem(entry, "say why this project cannot reach the advisory"))
            .toList();
    }

    /**
     * The notes of every entry, normalized the way the report reads them.
     *
     * @return each entry's notes as one line
     */
    public List<String> notes() {
        return this.entries.stream().map(entry -> SuppressionNotes.normalized(noteText(entry))).toList();
    }

    private List<String> unplanned() {
        return this.entries.stream()
            .filter(entry -> !noteText(entry).isEmpty())
            .filter(entry -> SuppressionNotes.parse(noteText(entry)).mitigation().isEmpty())
            .map(
                entry -> problem(
                    entry, "state a mitigation plan in notes after a " + SuppressionNotes.MARKER
                        + " marker, saying what limits the risk now and what will remove the vulnerable package"
                )
            ).toList();
    }

    private List<String> undated() {
        return this.entries.stream()
            .filter(entry -> !noteText(entry).isEmpty())
            .filter(entry -> !SuppressionNotes.DATE.matcher(noteText(entry)).find())
            .map(entry -> problem(entry, "record the date the exception was made, as YYYY-MM-DD"))
            .toList();
    }

    private List<String> unnamed() {
        return this.entries.stream()
            .filter(
                entry -> children(entry).noneMatch(
                    element -> ADVISORY.contains(name(element)) && !text(element).isEmpty()
                )
            ).map(entry -> problem(entry, "name the advisory it excuses, not only the package it sits in"))
            .toList();
    }

    private List<String> selectors() {
        return this.entries.stream().flatMap(
            entry -> children(entry).flatMap(element -> issue(element).stream().map(reason -> problem(entry, reason)))
        ).toList();
    }

    private static Optional<String> issue(Element element) {
        String selector = name(element);
        if (BROAD.contains(selector)) {
            return Optional.of("replace " + selector + " with individual cve or literal vulnerabilityName entries");
        }
        return ADVISORY.contains(selector) && (text(element).isEmpty() || regex(element))
            ? Optional.of("name a non-empty literal advisory in " + selector + ", without regular-expression matching")
            : Optional.empty();
    }

    private static boolean regex(Element element) {
        return element.hasAttribute("regex") && !LITERAL.contains(element.getAttribute("regex").strip());
    }

    private static String problem(Node entry, String requirement) {
        return "Dependency-Check suppression for " + subject(entry) + ": " + requirement;
    }

    private static String subject(Node entry) {
        return children(entry)
            .filter(element -> !"notes".equals(name(element)))
            .map(SuppressionDocument::text)
            .filter(value -> !value.isEmpty())
            .findFirst()
            .orElse("an entry naming nothing at all");
    }

    private static String noteText(Node entry) {
        return children(entry).filter(element -> "notes".equals(name(element)))
            .map(SuppressionDocument::text).findFirst().orElse("");
    }

    private static Stream<Element> children(Node entry) {
        return elements(entry.getChildNodes());
    }

    private static Stream<Element> elements(NodeList nodes) {
        return IntStream.range(0, nodes.getLength()).mapToObj(nodes::item)
            .filter(node -> node.getNodeType() == Node.ELEMENT_NODE).map(Element.class::cast);
    }

    private static String name(Element element) {
        String qualified = element.getTagName();
        return qualified.substring(qualified.indexOf(':') + 1);
    }

    private static String text(Node element) {
        return Objects.requireNonNull(element.getTextContent()).strip();
    }

    private static Document read(Path document) {
        try {
            return Xml.parse(Files.readString(document));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + document, exception);
        }
    }
}
