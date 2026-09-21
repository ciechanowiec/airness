package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Reads a test source and reports where it asks the accessibility rules a question of its own.
 *
 * <p>The rules report three verdicts: a rule that failed, a rule that passed, and a rule that could
 * not decide. A project that writes the question itself writes it once, gets the first verdict, and
 * never learns that there were three. That is not a hypothetical: three projects of this fleet wrote
 * the same question separately, each asking for failures alone, and between them the omission passed
 * a link whose label sat on its own colour at a contrast of one to one, two further component
 * families, and a pill whose text sat outside the background it was measured against.
 *
 * <p>So the question belongs to the harness and the browser belongs to the project. Two things are
 * looked for. Invoking the rules, which is the question itself. And naming the archive they ship in,
 * which is a project locating the script by a version it typed rather than by the one the archive
 * carries.
 *
 * <p>Comments are removed before the search and every literal is kept, a text block included, because
 * the question is written as one: the script a browser is handed is a block of text in a Java file.
 * The shared reader blanks a text block by default, on the ground that it quotes some other language,
 * and this is the one rule for which what a text block holds is the subject rather than prose.
 */
@UtilityClass
final class AxeAuditRules {

    private static final Pattern ASKED = Pattern.compile("\\baxe\\s*\\.\\s*run\\s*\\(");

    private static final Pattern LOCATED = Pattern.compile("webjars/axe-core");

    static List<String> asked(CharSequence source) {
        return found(source, ASKED, "asks the accessibility rules its own question");
    }

    static List<String> located(CharSequence source) {
        return found(source, LOCATED, "locates the accessibility rules by a version of its own");
    }

    private static List<String> found(CharSequence source, Pattern sought, String what) {
        String readable = JavaCode.withLiterals(source);
        return sought.matcher(readable).results()
            .map(hit -> "line %d: %s".formatted(JavaCode.lineOf(readable, hit.start()), what))
            .toList();
    }
}
