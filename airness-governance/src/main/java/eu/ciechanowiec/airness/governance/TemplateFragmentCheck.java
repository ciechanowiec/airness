package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.attoparser.ParseException;
import org.attoparser.config.ParseConfiguration;
import org.attoparser.simple.AbstractSimpleMarkupHandler;
import org.attoparser.simple.SimpleMarkupParser;

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
 * a declaration.
 */
public final class TemplateFragmentCheck {

    private static final String HEADLINE = "Template fragments that take more arguments than a callable may";

    // The same cap the standard sets on a callable's parameters. It is not a separate number and is
    // never raised on its own: a fragment and a method are the same construct read by two tools.
    private static final int CAP = 4;

    private static final String THYMELEAF = "th:fragment";

    // The spelling a document uses when it has to stay valid HTML5, which no dialect prefix is.
    private static final String THYMELEAF_DATA = "data-th-fragment";

    private static final char OPENS = '(';

    private static final char CLOSES = ')';

    private static final char SEPARATOR = ',';

    // A literal, under either quotation. What sits inside one is a value rather than a list, so a
    // comma there separates nothing.
    private static final Pattern QUOTED = Pattern.compile("'[^']*'|\"[^\"]*\"");

    // A group holding no further group. Taking every one of those out and asking again reduces a
    // nesting of any depth, which is what keeps this file from counting brackets itself.
    private static final Pattern INNERMOST = Pattern.compile("[(\\[{][^()\\[\\]{}]*[)\\]}]");

    private final Path root;

    private final List<Path> files;

    /**
     * Creates a check over the markup one module ships.
     *
     * @param root          repository root the offences are reported relative to
     * @param resourceRoots resource directories of the module
     */
    public TemplateFragmentCheck(Path root, Collection<Path> resourceRoots) {
        this.root = root;
        this.files = MarkupResources.of(root, resourceRoots);
    }

    /**
     * How many markup resources the check read.
     *
     * @return the number of files in scope
     */
    public int scanned() {
        return this.files.size();
    }

    /**
     * The one fragment rule and every declaration that breaks it.
     *
     * @return one verdict, carrying a line per fragment that takes too many arguments
     */
    public List<Findings> findings() {
        return List.of(new Findings(HEADLINE, this.overwide()));
    }

    /**
     * How many arguments a fragment declaration takes.
     *
     * <p>Commas inside a nested call or inside a quoted literal separate nothing, so a fragment
     * declared as {@code field('one, two', other)} takes two arguments rather than three.
     *
     * @param declaration the value of a fragment attribute, such as {@code field(label, name)}
     * @return the number of arguments, and zero for a fragment that declares no list at all
     */
    static int arguments(String declaration) {
        int opens = declaration.indexOf(OPENS);
        int closes = declaration.lastIndexOf(CLOSES);
        if (opens < 0 || closes < opens) {
            return 0;
        }
        String list = declaration.substring(opens + 1, closes).trim();
        return list.isEmpty() ? 0 : separators(list) + 1;
    }

    /**
     * The name a fragment is invoked by, which is everything before its argument list.
     *
     * @param declaration the value of a fragment attribute
     * @return the fragment name
     */
    static String name(String declaration) {
        int opens = declaration.indexOf(OPENS);
        return (opens < 0 ? declaration : declaration.substring(0, opens)).trim();
    }

    private List<String> overwide() {
        List<String> offences = new ArrayList<>();
        this.files.forEach(file -> this.read(file, offences));
        return List.copyOf(offences);
    }

    private void read(Path file, Collection<String> offences) {
        Repository.readText(file)
            .ifPresent(text -> offences.addAll(declarationsIn(this.root.relativize(file), text)));
    }

    private static List<String> declarationsIn(Path named, String text) {
        List<String> found = new ArrayList<>();
        try {
            new SimpleMarkupParser(ParseConfiguration.htmlConfiguration())
                .parse(text, new Declarations(named, found));
            return List.copyOf(found);
        } catch (ParseException _) {
            // Markup no engine could read is not this check's finding to make. template-parse reports
            // it from the same files, with the line and column of what it could not read, and a build
            // fails on that before anybody asks what its fragments declare. Whatever the parser reached
            // before it stopped is half a document, so this contributes nothing rather than a count
            // taken from a file that has no settled contents.
            return List.of();
        }
    }

    // What is left once every literal and every group has been taken out is the list itself, so the
    // commas still standing are the ones that separate one argument from the next.
    private static int separators(String list) {
        return (int) flattened(QUOTED.matcher(list).replaceAll(""))
            .chars()
            .filter(character -> character == SEPARATOR)
            .count();
    }

    private static String flattened(String list) {
        String reduced = INNERMOST.matcher(list).replaceAll("");
        return reduced.equals(list) ? list : flattened(reduced);
    }

    /**
     * Collects every fragment declaration the parser reaches, with the place it was written.
     */
    private static final class Declarations extends AbstractSimpleMarkupHandler {

        private final Path named;

        private final Collection<String> offences;

        private Declarations(Path named, Collection<String> offences) {
            this.named = named;
            this.offences = offences;
        }

        @Override
        public void handleOpenElement(
            String element, Map<String, String> attributes, int line, int column
        ) {
            this.read(attributes, line, column);
        }

        // The list is attoparser's rather than this code's, which is why the parameter cap passes over
        // an override. Declining it would leave the handler uncalled for an element that closes itself,
        // and a fragment declared on one unread.
        @Override
        public void handleStandaloneElement(
            String element, Map<String, String> attributes, boolean minimized, int line, int column
        ) {
            this.read(attributes, line, column);
        }

        // An element carrying no attribute at all arrives with nothing rather than with an empty map,
        // which is the parser saying the same thing in a way its caller has to answer for.
        private void read(Map<String, String> attributes, int line, int column) {
            Optional.ofNullable(attributes)
                .orElseGet(Map::of)
                .entrySet()
                .stream()
                .filter(attribute -> declares(attribute.getKey()))
                .forEach(attribute -> this.measure(attribute.getValue(), line, column));
        }

        private void measure(String declaration, int line, int column) {
            int arguments = arguments(declaration);
            if (arguments > CAP) {
                this.offences.add(
                    "%s:%d:%d: %s takes %d arguments, and the cap is %d"
                        .formatted(this.named, line, column, name(declaration), arguments, CAP)
                );
            }
        }

        private static boolean declares(String attribute) {
            String spelled = attribute.toLowerCase(Locale.ROOT);
            return THYMELEAF.equals(spelled) || THYMELEAF_DATA.equals(spelled);
        }
    }
}
