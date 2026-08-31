package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads every markup resource a module ships and reports the fragments that take more arguments than
 * a callable may.
 *
 * <p>A fragment is invoked, by name, with a positional argument list, which is what a callable is. The
 * cap the standard sets on a callable's parameters therefore applies to it, and until something reads
 * the markup it applies to nothing: a fragment can grow to seven positional strings while every
 * measured cap in the build stays green, because every one of them stops at the last Java file.
 *
 * <p>A fragment that reaches the cap is not made smaller by removing an argument. It is made smaller
 * by giving it one argument that carries what several used to, which is the same repair the cap asks
 * for in Java, and which a template gets for free because a view record is already what a controller
 * hands it.
 *
 * <p>The arguments are counted from the parsed document rather than matched in its text, so a fragment
 * named inside a comment, an attribute of some other name, or a block of literal script is not read as
 * a declaration. What counts them is {@link FragmentSignature}, which reads a declaration and a call
 * alike, so this rule and the rule over calls agree about where an argument list ends.
 */
public final class TemplateFragmentCheck {

    private static final String HEADLINE = "Template fragments that take more arguments than a callable may";

    // The same cap the standard sets on a callable's parameters. It is not a separate number and is
    // never raised on its own: a fragment and a method are the same construct read by two tools.
    private static final int CAP = 4;

    private static final String THYMELEAF = "th:fragment";

    // The spelling a document uses when it has to stay valid HTML5, which no dialect prefix is.
    private static final String THYMELEAF_DATA = "data-th-fragment";

    private final MarkupScan scan;

    /**
     * Creates a check over the markup one module ships.
     *
     * @param root          repository root the offences are reported relative to
     * @param resourceRoots resource directories of the module
     */
    public TemplateFragmentCheck(Path root, Collection<Path> resourceRoots) {
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
     * The one fragment rule and every declaration that breaks it.
     *
     * @return one verdict, carrying a line per fragment that takes too many arguments
     */
    public List<Findings> findings() {
        return List.of(new Findings(HEADLINE, this.scan.offences(Declarations::new)));
    }

    /**
     * Whether an attribute of the given name declares a fragment, under either spelling.
     *
     * @param attribute the attribute name as the document wrote it
     * @return whether it declares a fragment
     */
    static boolean declares(String attribute) {
        String spelled = attribute.toLowerCase(Locale.ROOT);
        return THYMELEAF.equals(spelled) || THYMELEAF_DATA.equals(spelled);
    }

    /**
     * Collects every fragment declaration the parser reaches, with the place it was written.
     */
    private static final class Declarations implements MarkupElement {

        private final Path named;

        private final Collection<String> offences;

        private Declarations(Path named, Collection<String> offences) {
            this.named = named;
            this.offences = offences;
        }

        @Override
        public void read(Map<String, String> attributes, int line, int column) {
            attributes.entrySet()
                .stream()
                .filter(attribute -> declares(attribute.getKey()))
                .forEach(attribute -> this.measure(attribute.getValue(), line, column));
        }

        private void measure(String declaration, int line, int column) {
            int arguments = FragmentSignature.arguments(declaration);
            if (arguments > CAP) {
                this.offences.add(
                    "%s:%d:%d: %s takes %d arguments, and the cap is %d"
                        .formatted(
                            this.named, line, column, FragmentSignature.name(declaration), arguments, CAP
                        )
                );
            }
        }
    }
}
