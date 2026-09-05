package eu.ciechanowiec.airness.governance;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * What a written value calls in a place the engine will not evaluate a call.
 *
 * <p>A standard expression admits a call only where something evaluates one, which is inside a
 * variable expression or a selection expression. Everywhere else it admits a literal, a number, a
 * token and the operators between them, so the two arms of a conditional may be written out but may
 * not be worked out. Moving one brace is the whole difference, and the two spellings read alike.
 *
 * <p>What is read is the residue of a value, which is the value with every region blanked where a
 * call belongs. A quoted literal is text, what a variable expression carries is evaluated, a literal
 * substitution is written out, and the name a message, link or fragment expression reaches for is
 * written out as well. Whatever call survives all four is a call in a place nothing will evaluate.
 *
 * <p>The four are taken in that order because each decides what the next one is allowed to see. A
 * brace inside a quoted literal opens nothing, and an operator written twice inside a variable
 * expression is not the pair of marks that wraps a substitution.
 *
 * <p>Blanking a region rather than removing it keeps every offence at the offset it was written at,
 * and keeps two names that were written apart from being read as one.
 */
@UtilityClass
final class TemplateExpressionRules {

    // A name followed by the bracket that would hand it arguments, which is the one shape a standard
    // expression will not read outside something that evaluates it.
    private static final Pattern CALL = Pattern.compile("[A-Za-z_$#][\\w.$]*\\s*\\(");

    // What marks an expression written in a document's text rather than in an attribute. Everything
    // outside these marks is text the engine writes out untouched, brackets and all.
    private static final Pattern INLINED = Pattern.compile("\\[\\[(.*?)]]|\\[\\((.*?)\\)]", Pattern.DOTALL);

    private static final char OPENS = '{';

    private static final char CLOSES = '}';

    private static final char ARGUMENTS = '(';

    // What wraps a value the engine writes out with the expressions inside it filled in.
    private static final char SUBSTITUTION = '|';

    private static final char BLANK = ' ';

    private static final String QUOTES = "'\"";

    // What opens an expression the engine evaluates, which is where a call is at home.
    private static final String EVALUATED = "$*";

    // What opens a construct naming something the engine goes and reads. The name is written out and
    // the argument list after it is evaluated, so the two halves are read differently.
    private static final String NAMING = "#@~";

    /**
     * Every call the given value writes where nothing will evaluate one.
     *
     * @param written the value of one attribute, or the text between two elements
     * @return the name of each such call, in the order they were written
     */
    static List<String> calls(String written) {
        Matcher found = CALL.matcher(withoutNames(withoutSubstitutions(withoutExpressions(withoutQuotes(written)))));
        List<String> calls = new ArrayList<>();
        while (found.find()) {
            calls.add(called(found.group()));
        }
        return List.copyOf(calls);
    }

    /**
     * Every call the text between two elements writes where nothing will evaluate one.
     *
     * <p>Only what an inlining mark encloses is read. The rest of a document's text is written out as
     * it stands, so a bracket in a sentence and the sample content a designer leaves inside an element
     * are text rather than expressions, and reading them as expressions would report prose.
     *
     * @param text the text written between two elements
     * @return the name of each such call, in the order they were written
     */
    static List<String> inlined(String text) {
        Matcher marked = INLINED.matcher(text);
        List<String> calls = new ArrayList<>();
        while (marked.find()) {
            calls.addAll(calls(Optional.ofNullable(marked.group(1)).orElseGet(() -> marked.group(2))));
        }
        return List.copyOf(calls);
    }

    private static String called(String matched) {
        return matched.substring(0, matched.indexOf(ARGUMENTS)).trim();
    }

    // A quoted literal is text, so a bracket, a brace or a mark written inside one opens nothing.
    private static String withoutQuotes(String written) {
        StringBuilder kept = new StringBuilder(written);
        int opens = next(kept, 0, QUOTES);
        while (opens >= 0) {
            int closes = kept.indexOf(String.valueOf(kept.charAt(opens)), opens + 1);
            if (closes < 0) {
                return blanked(kept, opens, kept.length());
            }
            blank(kept, opens, closes + 1);
            opens = next(kept, closes + 1, QUOTES);
        }
        return kept.toString();
    }

    // What a variable expression or a selection expression carries is evaluated, so a call in one is
    // a call in the place it belongs.
    private static String withoutExpressions(String written) {
        StringBuilder kept = new StringBuilder(written);
        int opens = opening(kept, 0, EVALUATED);
        while (opens >= 0) {
            int closes = closing(kept, opens + 2);
            blank(kept, opens, closes);
            opens = opening(kept, closes, EVALUATED);
        }
        return kept.toString();
    }

    // A literal substitution writes its contents out, so what it wraps is text apart from the
    // expressions inside it, and those are already blanked by the time this reads one.
    private static String withoutSubstitutions(String written) {
        StringBuilder kept = new StringBuilder(written);
        int opens = kept.indexOf(String.valueOf(SUBSTITUTION));
        while (opens >= 0) {
            int closes = kept.indexOf(String.valueOf(SUBSTITUTION), opens + 1);
            if (closes < 0) {
                return blanked(kept, opens, kept.length());
            }
            blank(kept, opens, closes + 1);
            opens = kept.indexOf(String.valueOf(SUBSTITUTION), closes + 1);
        }
        return kept.toString();
    }

    // The name a construct reaches for is written out rather than evaluated, and it is written the way
    // a call is written. What follows that name is an argument list the engine evaluates, so the name
    // is blanked up to the bracket that opens the list and the list itself is left to be read.
    private static String withoutNames(String written) {
        StringBuilder kept = new StringBuilder(written);
        int opens = opening(kept, 0, NAMING);
        while (opens >= 0) {
            int closes = closing(kept, opens + 2);
            int arguments = kept.indexOf(String.valueOf(ARGUMENTS), opens);
            int named = arguments >= 0 && arguments < closes ? arguments + 1 : closes;
            blank(kept, opens, named);
            opens = opening(kept, named, NAMING);
        }
        return kept.toString();
    }

    // Where a construct of one of the given kinds opens, which is a mark of that kind followed by the
    // brace that opens it.
    private static int opening(CharSequence written, int from, String kinds) {
        for (int index = from; index < written.length() - 1; index++) {
            boolean opens = kinds.indexOf(written.charAt(index)) >= 0 && written.charAt(index + 1) == OPENS;
            if (opens) {
                return index;
            }
        }
        return -1;
    }

    private static int next(CharSequence written, int from, String kinds) {
        for (int index = from; index < written.length(); index++) {
            if (kinds.indexOf(written.charAt(index)) >= 0) {
                return index;
            }
        }
        return -1;
    }

    // Where the brace opened before the given position is closed, and the end of the value where a
    // document leaves it open. Depth is counted because a link writes a path variable in braces and a
    // fragment writes an expression of its own in them.
    private static int closing(CharSequence written, int from) {
        int depth = 1;
        for (int index = from + 1; index < written.length(); index++) {
            depth += step(written.charAt(index));
            if (depth == 0) {
                return index + 1;
            }
        }
        return written.length();
    }

    private static int step(char character) {
        int opened = character == OPENS ? 1 : 0;
        return character == CLOSES ? -1 : opened;
    }

    private static void blank(StringBuilder written, int from, int to) {
        for (int index = from; index < Math.min(to, written.length()); index++) {
            written.setCharAt(index, BLANK);
        }
    }

    private static String blanked(StringBuilder written, int from, int to) {
        blank(written, from, to);
        return written.toString();
    }
}
