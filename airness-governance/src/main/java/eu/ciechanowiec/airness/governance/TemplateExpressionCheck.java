package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads every markup resource a module ships and reports the calls written where the template engine
 * will not evaluate one.
 *
 * <p>A standard expression evaluates a call only inside a variable expression or a selection
 * expression. Outside one it reads a literal, a number, a token and the operators between them, so the
 * arms of a conditional may be written out but may not be worked out. The engine refuses the
 * difference at the moment it parses the value, which is the first request that draws the page.
 *
 * <p>Nothing else in a build says so. The document parses, the formatter reads it, the analyzers pass
 * over it because it is markup rather than source, and the rule about fragment calls resolves the name
 * and counts the arguments without asking what those arguments are. The two spellings differ by one
 * brace, and the one that fails is the one an author reaches for when a literal becomes a call:
 * translating a page turns a written-out word into a lookup and moves the brace nowhere.
 *
 * <p>The repair is the brace. What needs evaluating goes inside the expression that evaluates it, so a
 * conditional choosing between two lookups is written as one variable expression rather than as an
 * expression, a question mark and two calls.
 *
 * <p>The text between elements is read as well as the attributes, because an expression is written in
 * one as readily as in the other and the engine reads both the same way. What is passed over is a
 * fragment declaration, which writes the names of the parameters it takes rather than an expression,
 * and every name a message, link or fragment expression reaches for, which is written out rather than
 * evaluated.
 */
public final class TemplateExpressionCheck {

    private static final String HEADLINE = "Calls written where a template engine will not evaluate one";

    private static final String REPAIR
        = "is written where nothing evaluates it, so the engine refuses the value on the first request "
            + "that draws it. Put the call inside the expression that needs it";

    private static final String THYMELEAF = "th:";

    // The spelling a document uses when it has to stay valid HTML5, which no dialect prefix is.
    private static final String THYMELEAF_DATA = "data-th-";

    private final MarkupScan scan;

    /**
     * Creates a check over the markup one module ships.
     *
     * @param root
     *                      repository root the offences are reported relative to
     * @param resourceRoots
     *                      resource directories of the module
     */
    public TemplateExpressionCheck(Path root, Collection<Path> resourceRoots) {
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
     * The one expression rule and every value that breaks it.
     *
     * @return one verdict, carrying a line per call written where nothing evaluates it
     */
    public List<Findings> findings() {
        return List.of(new Findings(HEADLINE, this.scan.offences(Unevaluated::new)));
    }

    // Whether the attribute carries an expression at all. A fragment declaration is written the way a
    // call is written and is a list of parameter names, so it is the one dialect attribute passed over.
    private static boolean carries(String attribute) {
        String spelled = attribute.toLowerCase(Locale.ROOT);
        boolean dialect = spelled.startsWith(THYMELEAF) || spelled.startsWith(THYMELEAF_DATA);
        return dialect && !TemplateFragmentCheck.declares(spelled);
    }

    /**
     * Collects every call a document writes outside the expressions that would evaluate one.
     */
    private static final class Unevaluated implements MarkupElement {

        private final Path named;

        private final Collection<String> offences;

        private Unevaluated(Path named, Collection<String> offences) {
            this.named = named;
            this.offences = offences;
        }

        @Override
        public void read(Map<String, String> attributes, int line, int column) {
            attributes.entrySet().stream().filter(attribute -> carries(attribute.getKey()))
                .forEach(attribute -> this.measure(attribute.getValue(), line, column));
        }

        @Override
        public void text(String content, int line, int column) {
            TemplateExpressionRules.inlined(content).forEach(call -> this.report(call, line, column));
        }

        private void measure(String written, int line, int column) {
            TemplateExpressionRules.calls(written).forEach(call -> this.report(call, line, column));
        }

        private void report(String call, int line, int column) {
            this.offences.add("%s:%d:%d: %s(...) %s".formatted(this.named, line, column, call, REPAIR));
        }
    }
}
