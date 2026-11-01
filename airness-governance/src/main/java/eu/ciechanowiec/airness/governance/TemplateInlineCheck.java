package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reads every markup resource a module ships and reports the code written into it rather than into a
 * file a source check reads.
 *
 * <p>A script block, an event-handler attribute, a {@code javascript:} address, a style block and a
 * template inlined as script or as a stylesheet are all code, and none of them reaches the lint, type,
 * architecture or stylesheet checks a frontend file of the project's own is held to. Each is also what
 * a content security policy refuses unless it is told to allow inline code everywhere. The repair is the
 * same for all of them: move the code into a file of the project's checked frontend sources and
 * reference it from the markup.
 *
 * <p>What is not code is left alone. A script element that carries data, a JSON document, an import map
 * or speculation rules, is read by the browser as data rather than run. The attributes a template engine
 * or a markup framework reads, {@code th:*}, {@code x-*}, {@code hx-*} and their kind, are template
 * syntax rather than standalone script, and parsing them as script would report a language that is not
 * written there. Those are assessed where they run: the browser sweep reports the page errors and broken
 * interactions they cause.
 */
public final class TemplateInlineCheck {

    private static final String INLINE = "Code written inline in markup, where no source check reads it";

    private static final String REPAIR
        = "Move it into a checked file of the project's frontend tooling project and reference that file";

    private static final String SCRIPT = "script";

    private static final String STYLE = "style";

    private static final Set<String> SOURCES = Set.of("src", "th:src", "data-th-src");

    private static final Set<String> INLINING = Set.of("th:inline", "data-th-inline");

    private static final Set<String> INLINED_CODE = Set.of("javascript", "css");

    // The script types a browser runs. Anything else a script element declares is data it hands to
    // whoever reads it, which is why the list is of what runs rather than of what does not.
    private static final Set<String> EXECUTABLE_TYPES = Set.of(
        "", "module", "text/javascript", "application/javascript", "application/ecmascript",
        "text/ecmascript", "application/x-javascript", "text/jscript"
    );

    // An event handler, written as the attribute itself or as the dialect's attribute that writes it.
    private static final Pattern HANDLER = Pattern.compile("(?:(?:data-)?th:)?on[a-z]+");

    private static final Pattern JAVASCRIPT_URL = Pattern.compile("^\\s*javascript\\s*:", Pattern.CASE_INSENSITIVE);

    private final MarkupScan scan;

    /**
     * Creates a check over the markup one module ships.
     *
     * @param root          repository root the offences are reported relative to
     * @param resourceRoots resource directories of the module
     */
    public TemplateInlineCheck(Path root, Collection<Path> resourceRoots) {
        this.scan = new MarkupScan(root, resourceRoots);
    }

    /**
     * How many markup resources the check read.
     *
     * @return the number of files in scope
     */
    public int scanned() {
        return this.scan.scanned();
    }

    /**
     * The inline-code rule and every place that breaks it.
     *
     * @return one verdict
     */
    public List<Findings> findings() {
        return List.of(new Findings(INLINE, this.scan.offences(InlineReading::new)));
    }

    /**
     * Collects every place a document writes code inline.
     */
    private static final class InlineReading implements MarkupElement {

        private final Path named;

        private final Collection<String> offences;

        // The element whose attributes are read next: the one opened last.
        private final Deque<String> open;

        private InlineReading(Path named, Collection<String> offences) {
            this.named = named;
            this.offences = offences;
            this.open = new ArrayDeque<>();
        }

        @Override
        public void opened(String element, int line, int column) {
            this.open.push(element.toLowerCase(Locale.ROOT));
            if (STYLE.equals(this.open.peek())) {
                this.add(line, column, "a style block holds a stylesheet");
            }
        }

        @Override
        public void read(Map<String, String> attributes, int line, int column) {
            Map<String, String> lowered = lowered(attributes);
            if (SCRIPT.equals(this.open.peek()) && runsInline(lowered)) {
                this.add(line, column, "a script block holds code");
            }
            lowered.forEach((attribute, value) -> this.attribute(attribute, value, line, column));
        }

        private void attribute(String attribute, String value, int line, int column) {
            if (HANDLER.matcher(attribute).matches()) {
                this.add(line, column, attribute + " is an event handler written as an attribute");
            }
            if (JAVASCRIPT_URL.matcher(value).find()) {
                this.add(line, column, attribute + " is a javascript: address");
            }
            if (INLINING.contains(attribute) && INLINED_CODE.contains(value.strip().toLowerCase(Locale.ROOT))) {
                this.add(line, column, attribute + " inlines the template as " + value.strip());
            }
        }

        private static boolean runsInline(Map<String, String> attributes) {
            boolean sourced = SOURCES.stream().anyMatch(attributes::containsKey);
            String type = Optional.ofNullable(attributes.get("type")).orElse("").strip().toLowerCase(Locale.ROOT);
            return !sourced && EXECUTABLE_TYPES.contains(type);
        }

        private static Map<String, String> lowered(Map<String, String> attributes) {
            return attributes.entrySet()
                .stream()
                .collect(
                    Collectors.toMap(
                        entry -> entry.getKey().toLowerCase(Locale.ROOT),
                        Map.Entry::getValue,
                        (first, _) -> first,
                        TreeMap::new
                    )
                );
        }

        private void add(int line, int column, String said) {
            this.offences.add("%s:%d:%d: %s. %s".formatted(this.named, line, column, said, REPAIR));
        }
    }
}
