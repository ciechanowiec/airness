package eu.ciechanowiec.airness.governance;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * What a fragment is called and how many arguments it takes, read from the way one is written.
 *
 * <p>A fragment is written the same whether it is being declared or being called, so the declaration
 * {@code field(control, value, error)} and the call {@code field(${name}, '', '')} are one shape read
 * twice. Reading it in one place is what lets the rule over declarations and the rule over calls agree
 * about what a name is and where an argument list ends. Two readings would eventually disagree on a
 * fragment whose argument holds a comma, and then one rule would report on something the other had
 * already accepted.
 *
 * <p>Nothing here decides anything. It says only what was written, so that a caller can ask whether
 * what was written is answered somewhere else.
 */
@UtilityClass
final class FragmentSignature {

    private static final char OPENS = '(';

    private static final char CLOSES = ')';

    private static final char SEPARATOR = ',';

    // A literal, under either quotation or as a substitution the engine composes. What sits inside one
    // is a value rather than a list, so a comma there separates nothing.
    //
    // The third form is the one a template reaches for whenever a value has to read as a sentence, and
    // a sentence is exactly where a comma turns up. Reading it as a separator splits one argument into
    // two and reports a call that hands over the number the declaration asks for.
    private static final Pattern QUOTED = Pattern.compile("'[^']*'|\"[^\"]*\"|\\|[^|]*\\|");

    // A group holding no further group. Taking every one of those out and asking again reduces a
    // nesting of any depth, which is what keeps this file from counting brackets itself.
    private static final Pattern INNERMOST = Pattern.compile("[(\\[{][^()\\[\\]{}]*[)\\]}]");

    /**
     * How many arguments a fragment takes.
     *
     * <p>Commas inside a nested call or inside a quoted literal separate nothing, so a fragment
     * written as {@code field('one, two', other)} takes two arguments rather than three.
     *
     * @param written a fragment as it was declared or called, such as {@code field(label, name)}
     * @return the number of arguments, and zero for one that writes no list at all
     */
    static int arguments(String written) {
        int opens = written.indexOf(OPENS);
        int closes = written.lastIndexOf(CLOSES);
        if (opens < 0 || closes < opens) {
            return 0;
        }
        String list = written.substring(opens + 1, closes).trim();
        return list.isEmpty() ? 0 : separators(list) + 1;
    }

    /**
     * The name a fragment is known by, which is everything before its argument list.
     *
     * @param written a fragment as it was declared or called
     * @return the fragment name
     */
    static String name(String written) {
        int opens = written.indexOf(OPENS);
        return (opens < 0 ? written : written.substring(0, opens)).trim();
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
}
