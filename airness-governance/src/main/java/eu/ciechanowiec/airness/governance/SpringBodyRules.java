package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Reports the three defects that only the body of a particular member reveals.
 *
 * <p>Each asks what one member does rather than what the file declares. A bean writes itself into a
 * static slot, so the container stops being the only thing holding it. An entity decides equality by the
 * identifier the database has not assigned yet, so the object stops matching the set it was put in. A
 * handler copies an exception into the response, so a database error becomes an answer to whoever asked.
 *
 * <p>Finding the members is {@link SpringMembers}, the same way {@link SpringProxyRules} finds the ones
 * a proxy advises.
 */
@UtilityClass
final class SpringBodyRules {

    private static final Pattern STEREOTYPE = Pattern.compile(
        "@(?:Component|Service|Repository|RestController|Controller)\\b"
    );
    private static final Pattern STATIC_FIELD = Pattern.compile(
        "\\bstatic\\s+(?!final\\b)[\\w.<>\\[\\],]+\\s+(\\w+)\\s*[;=]"
    );
    private static final Pattern ENTITY = Pattern.compile("@Entity\\b");
    /*
     * The annotations between the identifier marker and the field carry arguments of their own, and a scan
     * that simply took the next name found the argument rather than the field. So the annotations are
     * consumed explicitly, then the type, and the name is what is left before the semicolon.
     */
    private static final Pattern IDENTIFIER = Pattern.compile(
        "@Id\\b\\s*(?:@\\w+(?:\\s*\\([^)]*\\))?\\s*)*[\\w.$<>\\[\\], ]+?\\s+(\\w+)\\s*[;=]"
    );
    /*
     * The return type is part of the marker because the name alone does not tell a declaration from a
     * call. An entity that overrides equality almost always calls equals on the identifier inside it, and
     * a marker matching that call had the declaration reader run on from there to the next brace it
     * found, which is the body of whatever method came next. The member that came back was named after
     * one method and carried the body of another, so the rule counted it twice and could report a class
     * whose equality never reads the identifier at all.
     *
     * An override has one signature each, so pairing the name with the type it must return is exact
     * rather than merely narrower: a call is never preceded by boolean or int.
     */
    private static final Pattern EQUALITY = Pattern.compile(
        "\\bboolean\\s+(?=equals\\s*\\()|\\bint\\s+(?=hashCode\\s*\\()"
    );
    private static final Pattern HANDLER = Pattern.compile("@ExceptionHandler\\b");
    /*
     * The four names below are answered by things that are not exceptions at all, and the commonest of
     * them is the message source a handler words its own refusal with. Read as a name alone, the marker
     * refused that ordinary work, and the only way past it was to move the call one method out of the
     * handler, which hides a genuine leak exactly as well. So the receiver is bound to a parameter the
     * handler declares: what is read off the exception that was caught is the defect, and what is read
     * off anything else is not this rule's business.
     *
     * Every parameter counts rather than the ones whose type reads like an exception, because a refusal
     * of this library's own naming carries none of those words and a leak that goes unreported is worse
     * than a rule that looks one parameter too wide. Nothing else a handler is handed answers these four.
     *
     * The chain in the middle is how a handler reaches a cause, the most specific cause of a data access
     * failure being the text such a handler most often hands out.
     */
    private static final String ECHO = "\\s*\\.\\s*(?:\\w+\\s*\\([^()]*\\)\\s*\\.\\s*)*"
        + "(getMessage|getLocalizedMessage|getStackTrace|printStackTrace)\\s*\\(";

    /**
     * Whether a bean assigns one of its own static fields, which is the container bypassed by hand.
     *
     * @param source the Java source to read
     * @return one offence per assignment, in the order they are written
     */
    static List<String> staticBeanHolders(CharSequence source) {
        String code = JavaCode.blanked(source);
        if (!STEREOTYPE.matcher(code).find()) {
            return List.of();
        }
        return STATIC_FIELD.matcher(code).results()
            .flatMap(field -> assignment(code, field.group(1), field.end()).stream())
            .map(
                at -> offence(
                    source, at,
                    "a bean assigning its own static field holds itself somewhere the container does not"
                        + " manage, and that slot outlives the context in a test"
                )
            )
            .toList();
    }

    /**
     * Whether an entity decides equality by an identifier the database assigns.
     *
     * @param source the Java source to read
     * @return one offence per method that reads it, in the order they are written
     */
    static List<String> generatedIdentityEquality(CharSequence source) {
        String code = JavaCode.blanked(source);
        Optional<String> identifier = ENTITY.matcher(code).find() ? field(code) : Optional.empty();
        return identifier.stream()
            .flatMap(name -> equality(code, name))
            .map(
                member -> offence(
                    source, member.declaration(),
                    "equality read from a generated identifier changes the moment the row is written, so an"
                        + " entity put in a set before saving cannot be found in it afterwards"
                )
            )
            .toList();
    }

    /**
     * Whether an exception handler copies the exception into the response.
     *
     * @param source the Java source to read
     * @return one offence per call, in the order they are written
     */
    static List<String> echoedExceptions(CharSequence source) {
        String code = JavaCode.blanked(source);
        return SpringMembers.annotated(code, HANDLER).stream()
            .flatMap(handler -> echoes(source, code, handler))
            .sorted()
            .map(
                at -> offence(
                    source, at,
                    "an exception copied into the response tells the caller which table and which constraint"
                        + " failed, which is reconnaissance handed to whoever asked"
                )
            )
            .toList();
    }

    private static Stream<SpringMembers.Member> equality(String code, String name) {
        return SpringMembers.annotated(code, EQUALITY).stream()
            .filter(member -> reads(code, member, name));
    }

    private static Stream<Integer> echoes(CharSequence source, String code, SpringMembers.Member handler) {
        return caught(source, code, handler).flatMap(name -> calls(code, handler, name));
    }

    private static Stream<String> caught(CharSequence source, String code, SpringMembers.Member handler) {
        return SpringParameters.after(code, handler.declaration()).stream()
            .flatMap(taken -> SpringParameters.in(source, code, taken).stream())
            .map(SpringParameters.Parameter::name);
    }

    private static Stream<Integer> calls(String code, SpringMembers.Member handler, String name) {
        return Pattern.compile("(?<![\\w.$])" + Pattern.quote(name) + ECHO)
            .matcher(code.substring(handler.start(), handler.end()))
            .results()
            .map(echo -> handler.start() + echo.start(1));
    }

    private static Optional<Integer> assignment(String code, String name, int from) {
        Matcher written = Pattern
            .compile("\\b(?:this\\s*\\.\\s*)?" + Pattern.quote(name) + "\\s*=(?!=)")
            .matcher(code);
        return written.find(from) ? Optional.of(written.start()) : Optional.empty();
    }

    private static Optional<String> field(String code) {
        Matcher declared = IDENTIFIER.matcher(code);
        return declared.find() ? Optional.of(declared.group(1)) : Optional.empty();
    }

    private static boolean reads(String code, SpringMembers.Member member, String name) {
        return Pattern.compile("\\b" + Pattern.quote(name) + "\\b")
            .matcher(code.substring(member.start(), member.end()))
            .find();
    }

    private static String offence(CharSequence source, int at, String consequence) {
        return "line " + JavaCode.lineOf(source, at) + ": " + consequence;
    }
}
