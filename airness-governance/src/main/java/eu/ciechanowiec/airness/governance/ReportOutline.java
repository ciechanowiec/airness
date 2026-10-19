package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * The single numbered outline shared by headings, navigation, and printed contents, so a section can never
 * be numbered one way in the text and another in the contents.
 */
@UtilityClass
final class ReportOutline {

    private static final List<Entry> OUTLINE = List.of(
        new Entry(
            "vulnerabilities", "Vulnerabilities", List.of(
                new Entry("advisories-all", "All vulnerabilities"),
                new Entry("advisories-plans", "Mitigation plans"),
                new Entry("advisories-stale", "Recorded mitigations without a vulnerability")
            )
        ),
        new Entry(
            "licenses", "Licenses", List.of(
                new Entry("license-classes", "License classes"),
                new Entry("common-licenses", "Most common licenses"),
                new Entry("review-weak-copyleft", "Weak copyleft packages"),
                new Entry("review-outside", "Packages outside the policy")
            )
        ),
        new Entry(
            "policy", "License policy", List.of(
                new Entry("policy-permissive", "Permissive licenses"),
                new Entry("policy-weak-copyleft", "Weak copyleft licenses"),
                new Entry("policy-outside", "Licenses outside the policy"),
                new Entry("policy-evidence", "Verified license evidence")
            )
        ),
        new Entry("inventory", "Package inventory", List.of()),
        new Entry(
            "notices", "Third-party notices", List.of(
                new Entry("notices-missing", "Packages without a license file"),
                new Entry("notices-texts", "License texts"),
                new Entry("notices-typefaces", "Report typefaces")
            )
        ),
        new Entry(
            "provenance", "Provenance and reproduction", List.of(
                new Entry("provenance-source", "Source"),
                new Entry("provenance-scope", "Scope")
            )
        )
    );

    private static final Map<String, Numbered> NUMBERED = IntStream.range(0, OUTLINE.size()).boxed().flatMap(
        index -> {
            Entry section = OUTLINE.get(index);
            String number = String.valueOf(index + 1);
            return Stream.concat(
                Stream.of(Map.entry(section.id(), new Numbered(number, section.title()))),
                IntStream.range(0, section.subsections().size()).mapToObj(
                    position -> Map.entry(
                        section.subsections().get(position).id(),
                        new Numbered(number + '.' + (position + 1), section.subsections().get(position).title())
                    )
                )
            );
        }
    ).collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

    /**
     * The table of contents. It lists the sections only, so it stays short enough to share the first page
     * with the verdict.
     *
     * @return the contents navigation
     */
    static String contents() {
        return "<nav id=\"toc\" aria-labelledby=\"toctitle\"><div id=\"toctitle\">Contents</div>"
            + "<ul class=\"sectlevel1\">" + OUTLINE.stream().map(entry -> "<li>" + link(entry.id()) + "</li>")
                .collect(Collectors.joining()) + "</ul></nav>";
    }

    /**
     * A numbered top-level section. Its heading keeps the {@code section-N} anchor the printed contents
     * fill page numbers by.
     *
     * @param id      the outline section
     * @param content the section's markup after its heading
     * @return the section element
     */
    static String section(String id, String content) {
        Numbered numbered = numbered(id);
        return "<section class=\"sect1\" id=\"" + id + "\" aria-labelledby=\"" + anchor(numbered) + "\"><h2 id=\""
            + anchor(numbered) + "\">" + heading(numbered) + "</h2>" + content + "</section>";
    }

    static String subheading(String id) {
        return "<h3 id=\"" + id + "\">" + heading(numbered(id)) + "</h3>";
    }

    /**
     * The cross-reference text for an outline entry, such as "section 1.2".
     *
     * @param id the outline entry
     * @return the reference text
     */
    static String reference(String id) {
        return "section " + numbered(id).number();
    }

    private static String link(String id) {
        Numbered numbered = numbered(id);
        return "<a href=\"#" + anchor(numbered) + "\">" + heading(numbered)
            + "<span class=\"leader\"></span><span class=\"page\" data-for=\"" + anchor(numbered)
            + "\"></span></a>";
    }

    private static Numbered numbered(String id) {
        return Optional.ofNullable(NUMBERED.get(id))
            .orElseThrow(() -> new IllegalArgumentException("Unknown report section: " + id));
    }

    private static String anchor(Numbered numbered) {
        return "section-" + numbered.number();
    }

    private static String heading(Numbered numbered) {
        return "<span class=\"adnum\">" + numbered.number() + ".</span><span class=\"adtext\">" + numbered.title()
            + "</span>";
    }

    private record Entry(String id, String title, List<Entry> subsections) {

        Entry(String id, String title) {
            this(id, title, List.of());
        }
    }

    private record Numbered(String number, String title) {
    }
}
