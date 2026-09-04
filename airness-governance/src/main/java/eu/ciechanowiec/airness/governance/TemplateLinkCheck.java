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
 * Reads every markup resource a module ships and reports the link expressions and the fragment
 * expressions that reach for what the template engine will not read inside one.
 *
 * <p>A link expression is what ends up in an address a browser follows, so the engine evaluates its
 * contents under a rule that refuses to reach past the model. A bean, a static class and an
 * instantiation are all turned away there, and turning them away is what keeps an address out of
 * reach of a call that could build it from anything.
 *
 * <p>A fragment expression is evaluated under the same rule, wherever it is written. What it names is
 * a template the engine goes and reads, and what it hands over is put in place inside that template,
 * so its name, its arguments and its selector are all read without reach past the model. An argument
 * that reaches for a bean is the ordinary way to trip this, because the same reach one attribute
 * over, in a with attribute, is legal and idiomatic, and nothing says the argument position differs.
 *
 * <p>Nothing else in a build says so. The document parses, the expression compiles, every analyzer
 * passes over it, and the page fails on the first request that draws it, with a message naming the
 * restriction rather than the attribute that tripped it. That is the class of defect a build is meant
 * to reach before a reader does.
 *
 * <p>The repair keeps the rule rather than working around it. What the expression needs is asked for
 * beside it, in a with attribute, and the expression is handed the variable that answered.
 *
 * <p>Only what is written inside a link or a fragment expression is read this way. The same reference
 * in any other attribute is ordinary and is left alone, which is the whole difference between asking
 * first and asking inside the expression. A path segment or a template name that merely reads like
 * one of the refused words is not an expression at all, so the search runs inside the variable and
 * selection expressions of a link or a fragment rather than anywhere in it. A fragment expression
 * nested inside another is enclosed by the braces of the outer one and read as part of it, and one
 * written in a document's text rather than an attribute is passed over, as the link rule passes over
 * the text.
 */
public final class TemplateLinkCheck {

    private static final String HEADLINE = "Link expressions that reach for what the engine will not read in one";

    private static final String REPAIR
        = "inside a link expression. Ask for it in a th:with beside this and put the variable in the link";

    private static final String HEADLINE_FRAGMENT
        = "Fragment expressions that reach for a bean, a static class or an instantiation";

    private static final String REPAIR_FRAGMENT = "inside a fragment expression, which the engine refuses on the "
        + "first request that draws it. Ask for it in a th:with beside this and hand the fragment the variable";

    private static final char OPENS = '{';

    private static final char CLOSES = '}';

    // A reference to a bean of the container, which the engine reads everywhere except here. The
    // character is taken only where an identifier does not run into it, so an address written inside a
    // quoted literal is not read as one.
    private static final Pattern BEAN = Pattern.compile("(?<![\\w.])@\\w");

    private static final Pattern STATIC_CLASS = Pattern.compile("\\bT\\(");

    private static final Pattern INSTANTIATION = Pattern.compile("\\bnew\\s");

    // What a restricted expression may not reach for, and what each is called in the offence that names
    // it. Held in order rather than in a map, so that an expression reaching for two of them is always
    // reported for the same one.
    private static final List<Map.Entry<Pattern, String>> REFUSED = List.of(
        Map.entry(BEAN, "a bean"),
        Map.entry(STATIC_CLASS, "a static class"),
        Map.entry(INSTANTIATION, "an instantiation")
    );

    private static final Restricted LINK = new Restricted("@{", HEADLINE, REPAIR);

    private static final Restricted FRAGMENT = new Restricted("~{", HEADLINE_FRAGMENT, REPAIR_FRAGMENT);

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
     * The link rule and the fragment rule, and every expression that breaks either.
     *
     * @return two verdicts, each carrying a line per element whose expression reaches for what it may not
     */
    public List<Findings> findings() {
        return List.of(this.verdict(LINK), this.verdict(FRAGMENT));
    }

    private Findings verdict(Restricted context) {
        List<String> offences = this.scan.offences((named, found) -> new Reaches(named, found, context));
        return new Findings(context.headline(), offences);
    }

    /**
     * What every link expression written in one attribute value carries between its braces.
     *
     * @param value the value of one attribute
     * @return the contents of each link expression in it, in the order they were written
     */
    static List<String> links(String value) {
        return enclosed(value, LINK.opens());
    }

    /**
     * What every fragment expression written in one attribute value carries between its braces.
     *
     * @param value the value of one attribute
     * @return the contents of each fragment expression in it, in the order they were written
     */
    static List<String> fragments(String value) {
        return enclosed(value, FRAGMENT.opens());
    }

    // The closing brace is counted to rather than matched, because an expression nests braces of its
    // own around a path variable, around every expression it interpolates and around a fragment it
    // hands over. An expression is also written as an argument to something else, so what follows the
    // brace that closes it belongs to that and not to the expression.
    private static List<String> enclosed(String value, String opens) {
        List<String> found = new ArrayList<>();
        int begins = value.indexOf(opens);
        while (begins >= 0) {
            int closes = closing(value, begins + opens.length());
            if (closes < 0) {
                return List.copyOf(found);
            }
            found.add(value.substring(begins + opens.length(), closes));
            begins = value.indexOf(opens, closes);
        }
        return List.copyOf(found);
    }

    /**
     * What every variable and selection expression written inside one restricted expression carries
     * between its braces.
     *
     * <p>A link is mostly an address, and a fragment expression is mostly a name, and neither of those
     * is an expression. Reading only what is interpolated is what keeps a path segment or a template
     * name spelled like a refused word from being read as one.
     *
     * @param written the contents of one link or fragment expression
     * @return the contents of each expression inside it, in the order they were written
     */
    static List<String> expressions(String written) {
        List<String> found = new ArrayList<>();
        int cursor = 0;
        while (cursor < written.length() - 1) {
            int closes = ends(written, cursor);
            found.addAll(carried(written, cursor, closes));
            cursor = next(cursor, closes);
        }
        return List.copyOf(found);
    }

    // Where the expression opening at the cursor closes, and nothing where none opens there.
    private static int ends(String written, int cursor) {
        return opens(written, cursor) ? closing(written, cursor + 2) : -1;
    }

    // What the expression opening at the cursor carries, and nothing where none opens there.
    private static List<String> carried(String written, int cursor, int closes) {
        return closes < 0 ? List.of() : List.of(written.substring(cursor + 2, closes));
    }

    // Where the scan goes on, which is past an expression it kept and one character along otherwise.
    private static int next(int cursor, int closes) {
        return (closes < 0 ? cursor : closes) + 1;
    }

    /**
     * What an expression reaches for that a restricted expression may not carry.
     *
     * @param expression the contents of one expression written inside a link or a fragment expression
     * @return what it reaches for, and nothing when it reaches for none of the three
     */
    static Optional<String> reached(String expression) {
        return REFUSED.stream()
            .filter(refused -> refused.getKey().matcher(expression).find())
            .map(Map.Entry::getValue)
            .findFirst();
    }

    // Where the brace opened before the given position is closed, and nothing where a document leaves
    // it open. Depth is counted because everything an expression carries may nest another pair.
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

    private static boolean opens(String written, int cursor) {
        char lead = written.charAt(cursor);
        return (lead == '$' || lead == '*') && written.charAt(cursor + 1) == OPENS;
    }

    /**
     * One of the two expressions the engine evaluates without reach past the model, with the token that
     * opens one and the words an offence about it carries.
     *
     * @param opens    what the expression is written after
     * @param headline the rule an offence is reported under
     * @param repair   what an offence says after naming the reach
     */
    private record Restricted(String opens, String headline, String repair) {
    }

    /**
     * Collects every element whose expression of one kind reaches for what the engine will not read,
     * with the place it was written.
     */
    private static final class Reaches implements MarkupElement {

        private static final String THYMELEAF = "th:";

        // The spelling a document uses when it has to stay valid HTML5, which no dialect prefix is.
        private static final String THYMELEAF_DATA = "data-th-";

        private final Path named;

        private final Collection<String> offences;

        private final Restricted context;

        private Reaches(Path named, Collection<String> offences, Restricted context) {
            this.named = named;
            this.offences = offences;
            this.context = context;
        }

        // Sorted rather than in the order they were written, so that one element reads the same way in
        // two documents and a message does not change when somebody moves an attribute. An element is
        // reported once however many of its expressions reach for something, because the repair is one.
        @Override
        public void read(Map<String, String> attributes, int line, int column) {
            attributes.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(this::reach)
                .flatMap(Optional::stream)
                .findFirst()
                .ifPresent(reach -> this.report(reach, line, column));
        }

        private void report(String reach, int line, int column) {
            String repair = this.context.repair();
            this.offences.add("%s:%d:%d: %s %s".formatted(this.named, line, column, reach, repair));
        }

        private Optional<String> reach(Map.Entry<String, String> attribute) {
            String spelled = attribute.getKey().toLowerCase(Locale.ROOT);
            if (!dialect(spelled)) {
                return Optional.empty();
            }
            return enclosed(attribute.getValue(), this.context.opens())
                .stream()
                .flatMap(written -> expressions(written).stream())
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
