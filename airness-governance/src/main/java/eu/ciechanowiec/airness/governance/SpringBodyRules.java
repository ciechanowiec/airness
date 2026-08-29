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
    private static final Pattern ECHO = Pattern.compile(
        "\\.\\s*(getMessage|getLocalizedMessage|getStackTrace|printStackTrace)\\s*\\("
    );

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
            .flatMap(handler -> echoes(code, handler))
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

    private static Stream<Integer> echoes(String code, SpringMembers.Member handler) {
        return ECHO.matcher(code.substring(handler.start(), handler.end())).results()
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
