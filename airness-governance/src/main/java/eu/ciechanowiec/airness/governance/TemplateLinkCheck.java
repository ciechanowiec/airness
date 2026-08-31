package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads every markup resource a module ships and reports the link expressions that reach for what the
 * template engine will not read inside one.
 *
 * <p>A link expression is what ends up in an address a browser follows, so the engine evaluates its
 * contents under a rule that refuses to reach past the model. A bean, a static class and an
 * instantiation are all turned away there, and turning them away is what keeps an address out of
 * reach of a call that could build it from anything.
 *
 * <p>Nothing else in a build says so. The document parses, the expression compiles, every analyzer
 * passes over it, and the page fails on the first request that draws it, with a message naming the
 * restriction rather than the attribute that tripped it. That is the class of defect a build is meant
 * to reach before a reader does.
 *
 * <p>The repair keeps the rule rather than working around it. What the link needs is asked for beside
 * it, in a with attribute, and the link is handed the variable that answered.
 *
 * <p>Only what is written inside a link expression is read this way. The same reference in any other
 * attribute is ordinary and is left alone, which is the whole difference between asking first and
 * asking inside the link. A path segment that merely reads like one of the refused words is not an
 * expression at all, so the search runs inside the variable and selection expressions of a link
 * rather than anywhere in it.
 */
public final class TemplateLinkCheck {

    private static final String HEADLINE = "Link expressions that reach for what the engine will not read in one";

    private static final String REPAIR
        = "inside a link expression. Ask for it in a th:with beside this and put the variable in the link";

    private static final String OPENS_LINK = "@{";

    private static final char OPENS = '{';

    private static final char CLOSES = '}';

    // A reference to a bean of the container, which the engine reads everywhere except here. The
    // character is taken only where an identifier does not run into it, so an address written inside a
    // quoted literal is not read as one.
    private static final Pattern BEAN = Pattern.compile("(?<![\\w.])@\\w");

    private static final Pattern STATIC_CLASS = Pattern.compile("\\bT\\(");

    private static final Pattern INSTANTIATION = Pattern.compile("\\bnew\\s");

    // What a link expression may not reach for, and what each is called in the offence that names it.
    // Held in order rather than in a map, so that an expression reaching for two of them is always
    // reported for the same one.
    private static final List<Map.Entry<Pattern, String>> REFUSED = List.of(
        Map.entry(BEAN, "a bean"),
        Map.entry(STATIC_CLASS, "a static class"),
        Map.entry(INSTANTIATION, "an instantiation")
    );

    private final MarkupScan scan;

    /**
     * Creates a check over the markup one module ships.
     *
     * @param root          repository root the offences are reported relative to
     * @param resourceRoots resource directories of the module
     */
    public TemplateLinkCheck(Path root, Collection<Path> resourceRoots) {
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
     * The one link rule and every expression that breaks it.
     *
     * @return one verdict, carrying a line per element whose link reaches for what it may not
     */
    public List<Findings> findings() {
        return List.of(new Findings(HEADLINE, this.scan.offences(Links::new)));
    }

    /**
     * What every link expression written in one attribute value carries between its braces.
     *
     * <p>The closing brace is counted to rather than matched, because a link nests braces of its own
     * around a path variable and around every expression it interpolates. A link is also written as an
     * argument to something else, so what follows the brace that closes it belongs to that and not to
     * the link.
     *
     * @param value the value of one attribute
     * @return the contents of each link expression in it, in the order they were written
     */
    static List<String> links(String value) {
        List<String> found = new ArrayList<>();
        int opens = value.indexOf(OPENS_LINK);
        while (opens >= 0) {
            int closes = closing(value, opens + OPENS_LINK.length());
            if (closes < 0) {
                return List.copyOf(found);
            }
            found.add(value.substring(opens + OPENS_LINK.length(), closes));
            opens = value.indexOf(OPENS_LINK, closes);
        }
        return List.copyOf(found);
    }

    /**
     * What every variable and selection expression written inside one link carries between its braces.
     *
     * <p>A link is mostly an address, and an address is not an expression. Reading only what the link
     * interpolates is what keeps a path segment spelled like a refused word from being read as one.
     *
     * @param link the contents of one link expression
     * @return the contents of each expression inside it, in the order they were written
     */
    static List<String> expressions(String link) {
        List<String> found = new ArrayList<>();
        int cursor = 0;
        while (cursor < link.length() - 1) {
            int closes = ends(link, cursor);
            found.addAll(carried(link, cursor, closes));
            cursor = next(cursor, closes);
        }
        return List.copyOf(found);
    }

    // Where the expression opening at the cursor closes, and nothing where none opens there.
    private static int ends(String link, int cursor) {
        return opens(link, cursor) ? closing(link, cursor + 2) : -1;
    }

    // What the expression opening at the cursor carries, and nothing where none opens there.
    private static List<String> carried(String link, int cursor, int closes) {
        return closes < 0 ? List.of() : List.of(link.substring(cursor + 2, closes));
    }

    // Where the scan goes on, which is past an expression it kept and one character along otherwise.
    private static int next(int cursor, int closes) {
        return (closes < 0 ? cursor : closes) + 1;
    }

    /**
     * What an expression reaches for that a link expression may not carry.
     *
     * @param expression the contents of one expression written inside a link
     * @return what it reaches for, and nothing when it reaches for none of the three
     */
    static Optional<String> reached(String expression) {
        return REFUSED.stream()
            .filter(refused -> refused.getKey().matcher(expression).find())
            .map(Map.Entry::getValue)
            .findFirst();
    }

    // Where the brace opened before the given position is closed, and nothing where a document leaves
    // it open. Depth is counted because everything a link carries may nest another pair.
    private static int closing(String written, int from) {
        int depth = 1;
        for (int index = from; index < written.length(); index++) {
            depth += step(written.charAt(index));
            if (depth == 0) {
                return index;
            }
        }
        return -1;
    }

    // What one character does to how deep the scan is, which is nothing for all but two of them.
    private static int step(char character) {
        int opened = character == OPENS ? 1 : 0;
        return character == CLOSES ? -1 : opened;
    }

    private static boolean opens(String link, int cursor) {
        char lead = link.charAt(cursor);
        return (lead == '$' || lead == '*') && link.charAt(cursor + 1) == OPENS;
    }

    /**
     * Collects every element whose link reaches for what the engine will not read, with the place it
     * was written.
     */
    private static final class Links implements MarkupElement {

        private static final String THYMELEAF = "th:";

        // The spelling a document uses when it has to stay valid HTML5, which no dialect prefix is.
        private static final String THYMELEAF_DATA = "data-th-";

        private final Path named;

        private final Collection<String> offences;

        private Links(Path named, Collection<String> offences) {
            this.named = named;
            this.offences = offences;
        }

        // Sorted rather than in the order they were written, so that one element reads the same way in
        // two documents and a message does not change when somebody moves an attribute. An element is
        // reported once however many of its links reach for something, because the repair is one.
        @Override
        public void read(Map<String, String> attributes, int line, int column) {
            attributes.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Links::reach)
                .flatMap(Optional::stream)
                .findFirst()
                .ifPresent(reach -> this.report(reach, line, column));
        }

        private void report(String reach, int line, int column) {
            this.offences.add("%s:%d:%d: %s %s".formatted(this.named, line, column, reach, REPAIR));
        }

        private static Optional<String> reach(Map.Entry<String, String> attribute) {
            String spelled = attribute.getKey().toLowerCase(Locale.ROOT);
            if (!dialect(spelled)) {
                return Optional.empty();
            }
            return links(attribute.getValue())
                .stream()
                .flatMap(link -> expressions(link).stream())
                .map(TemplateLinkCheck::reached)
                .flatMap(Optional::stream)
                .findFirst()
                .map(found -> "%s reaches for %s".formatted(spelled, found));
        }

        private static boolean dialect(String attribute) {
            return attribute.startsWith(THYMELEAF) || attribute.startsWith(THYMELEAF_DATA);
        }
    }
}
