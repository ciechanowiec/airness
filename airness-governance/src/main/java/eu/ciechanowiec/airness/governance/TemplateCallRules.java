package eu.ciechanowiec.airness.governance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * What a fragment expression names, read from the way one is written.
 *
 * <p>A fragment expression is the call half of the construct {@link FragmentSignature} reads the
 * declaration half of. It names a template, a fragment of that template, and the arguments handed to
 * it, and any of the three may be left out.
 *
 * <p>Only what is written plainly is read. An expression that builds the name it reaches for, as the
 * layout mechanism does when it hands a page its own body, names nothing this can resolve, and is
 * passed over rather than guessed at. That is the same answer the harness gives a constructor
 * expression whose type is declared in another module: a rule that cannot see both halves reports on
 * neither.
 *
 * <p>A markup selector is passed over for a different reason. A fragment expression may select by
 * identifier, class or tag instead of by fragment name, and what those reach is an element rather
 * than a declaration, so there is no argument list to hold anything to.
 */
@UtilityClass
final class TemplateCallRules {

    private static final String OPENS_FRAGMENT = "~{";

    private static final String SEPARATOR = "::";

    private static final char CLOSES = '}';

    // The template name a document writes when it means the one the expression was written in.
    private static final String SELF = "this";

    // What an expression opens with when it builds what it names rather than writing it out.
    private static final Set<String> BUILT = Set.of("${", "*{", "@{", "#{");

    // What a markup selector opens with. What one reaches is an element rather than a declaration, so
    // nothing here has an argument list to measure.
    private static final Set<Character> SELECTS = Set.of('#', '.', '/', '[', '%');

    // The three attributes that take a fragment expression, under the prefix and under the spelling a
    // document uses when it has to stay valid HTML5.
    private static final Set<String> CALLERS = Set.of(
        "th:replace", "th:insert", "th:include",
        "data-th-replace", "data-th-insert", "data-th-include"
    );

    /**
     * Every fragment expression written in one attribute value.
     *
     * <p>A value carries more than one when it chooses between them, and carries one written without
     * its braces when an attribute takes nothing else. An expression nested inside another is part of
     * that one's argument list rather than a call of its own, so the scan goes on after the expression
     * it kept rather than into it.
     *
     * @param value the value of one attribute
     * @return what each of them names, leaving out every one that names nothing readable
     */
    static List<FragmentCall> calls(String value) {
        List<String> written = expressions(value);
        return written.stream().map(TemplateCallRules::parsed).flatMap(List::stream).toList();
    }

    /**
     * Every fragment name a value reaches, however deeply it is written.
     *
     * <p>{@link #calls(String)} answers the calls a value makes, and a fragment written inside another
     * call's argument list is not one of those. It is handed over rather than called, and where it is
     * finally put in place is a variable, so the rule over calls is right to pass it by. It is a reach
     * all the same, and a rule about a fragment nothing reaches has to see it, or a page handing its own
     * controls to a shared header would report the controls of every page in the project.
     *
     * @param value the value of one attribute
     * @return every fragment name written in it, at any depth
     */
    static List<String> reached(String value) {
        List<String> found = new ArrayList<>();
        gather(value, found);
        return List.copyOf(found);
    }

    // Each descent strips one pair of braces, and a content carrying none is not descended into, which
    // is what stops a value written without braces being read as its own argument for ever.
    private static void gather(String value, Collection<String> found) {
        for (String written : expressions(value)) {
            parsed(written).stream()
                .filter(call -> !call.whole())
                .map(FragmentCall::fragment)
                .forEach(found::add);
            if (written.contains(OPENS_FRAGMENT)) {
                gather(written, found);
            }
        }
    }

    /**
     * What each fragment expression in a value carries between its braces.
     *
     * @param value the value of one attribute
     * @return the contents of each, and the whole value when it is written without braces
     */
    static List<String> expressions(String value) {
        List<String> found = new ArrayList<>();
        int opens = value.indexOf(OPENS_FRAGMENT);
        while (opens >= 0) {
            int closes = closing(value, opens + OPENS_FRAGMENT.length());
            if (closes < 0) {
                return List.copyOf(found);
            }
            found.add(value.substring(opens + OPENS_FRAGMENT.length(), closes));
            opens = value.indexOf(OPENS_FRAGMENT, closes);
        }
        return found.isEmpty() ? bare(value) : List.copyOf(found);
    }

    // An attribute that takes nothing but a fragment expression may write one without its braces, which
    // the engine still reads as one. A value that builds what it names is not one of those.
    private static List<String> bare(String value) {
        String written = value.trim();
        return written.isEmpty() || builds(written) ? List.of() : List.of(written);
    }

    private static List<FragmentCall> parsed(String expression) {
        int separator = separator(expression);
        String template = separator < 0 ? expression : expression.substring(0, separator);
        String fragment = separator < 0 ? "" : expression.substring(separator + SEPARATOR.length());
        return named(template.trim(), fragment.trim());
    }

    // What the two halves name, and nothing at all when either of them is built rather than written.
    //
    // Only the name is asked about. An argument list is where a call says what it hands over, so it
    // carries expressions in the ordinary case, and reading the list as part of the name would pass
    // over every call that hands a fragment anything at all.
    private static List<FragmentCall> named(String template, String fragment) {
        String name = FragmentSignature.name(fragment);
        if (builds(template) || builds(name) || selects(name)) {
            return List.of();
        }
        String reached = SELF.equalsIgnoreCase(template) ? "" : template;
        return List.of(new FragmentCall(reached, name, FragmentSignature.arguments(fragment)));
    }

    private static boolean builds(String written) {
        return BUILT.stream().anyMatch(written::contains);
    }

    private static boolean selects(String fragment) {
        return !fragment.isEmpty() && SELECTS.contains(fragment.trim().charAt(0));
    }

    // Where the two halves are parted, which is the first separator no bracket of any kind encloses.
    // A nested expression carries one of its own, and that one parts its halves rather than these.
    private static int separator(String expression) {
        int depth = 0;
        for (int index = 0; index < expression.length() - 1; index++) {
            depth += step(expression.charAt(index));
            if (depth == 0 && expression.startsWith(SEPARATOR, index)) {
                return index;
            }
        }
        return -1;
    }

    // Where the brace opened before the given position is closed, and nothing where a document leaves
    // it open. Depth is counted because everything an expression carries may nest another pair.
    private static int closing(String written, int from) {
        int depth = 1;
        for (int index = from; index < written.length(); index++) {
            depth += brace(written.charAt(index));
            if (depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private static int brace(char character) {
        int opened = character == '{' ? 1 : 0;
        return character == CLOSES ? -1 : opened;
    }

    // What one character does to how deep the scan is, counting every bracket a fragment expression may
    // nest, so that a separator inside an argument list is not read as the one that parts the halves.
    private static int step(char character) {
        int opened = "{([".indexOf(character) >= 0 ? 1 : 0;
        return "})]".indexOf(character) >= 0 ? -1 : opened;
    }

    /**
     * Whether an attribute of the given name takes a fragment expression, under either spelling.
     *
     * @param attribute the attribute name as the document wrote it
     * @return whether it calls a fragment
     */
    static boolean caller(String attribute) {
        return CALLERS.contains(attribute.toLowerCase(Locale.ROOT));
    }
}
