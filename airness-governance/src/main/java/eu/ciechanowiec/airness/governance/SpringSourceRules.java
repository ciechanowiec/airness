package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Reads a Spring source and reports constructs the container accepts and then does not honour.
 *
 * <p>The first is an application class outside the package the project declared. Component scanning
 * starts at the package of the class carrying the annotation, so a class one level too deep leaves every
 * bean above it unfound. The application still starts, and reports the absence as a missing bean far
 * from the declaration that caused it.
 *
 * <p>The second is a bean method calling another bean method of the same class. Under the lite mode the
 * rule set requires, no subclass intercepts that call, so it runs the method again and returns an object
 * the container never saw. Two of whatever the method builds then exist, one of them unmanaged.
 *
 * <p>The third is a request path written as the default member of {@code @Controller}. That member names
 * the bean rather than mapping a request, so the class compiles and starts while the path decides no
 * endpoint.
 *
 * <p>Structural rules read the source with comments and literals blanked, so a name inside a string or
 * an explanation is not mistaken for code. Value rules leave literals in place. The scan reads one file
 * at a time, which is what the one top-level class per file rule makes sufficient.
 */
@UtilityClass
final class SpringSourceRules {

    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern ENTRY_POINT = Pattern.compile("@SpringBootApplication\\b");
    private static final Pattern BEAN = Pattern.compile("@Bean\\b");
    private static final Pattern CONTROLLER = Pattern.compile(
        "@(?:org\\.springframework\\.stereotype\\.)?Controller\\s*\\(\\s*"
            + "(?:value\\s*=\\s*)?(\"[^\"]*\"|[A-Z][A-Z0-9_]*)\\s*\\)"
    );
    private static final Pattern STRING_CONSTANT = Pattern.compile(
        "\\bstatic\\s+final\\s+String\\s+(\\w+)\\s*=\\s*\"([^\"]*)\""
    );

    /**
     * Whether the application class sits anywhere but at the declared package root.
     *
     * @param source      the Java source to read
     * @param packageRoot the package every class of the project lives under
     * @return the offence, when this source declares a misplaced application class
     */
    static List<String> misplacedEntryPoint(CharSequence source, String packageRoot) {
        String code = JavaCode.blanked(source);
        Matcher annotation = ENTRY_POINT.matcher(code);
        return annotation.find()
            ? misplacement(source, code, annotation.start(), packageRoot)
            : List.of();
    }

    private static List<String> misplacement(
        CharSequence source, String code, int at, String packageRoot
    ) {
        String declared = declaredPackage(code);
        return declared.equals(packageRoot)
            ? List.of()
            : List.of(
                "line " + JavaCode.lineOf(source, at) + ": the application class is in " + declared
                    + ", so component scanning starts there rather than at " + packageRoot
            );
    }

    /**
     * Every call from one bean method to another declared beside it.
     *
     * @param source the Java source to read
     * @return one offence per call, in the order they are written
     */
    static List<String> calledBeanMethods(CharSequence source) {
        String code = JavaCode.blanked(source);
        List<SpringMembers.Member> beans = SpringMembers.annotated(code, BEAN);
        List<String> names = beans.stream().map(SpringMembers.Member::name).toList();
        return beans.stream().flatMap(bean -> calls(source, code, bean, names)).toList();
    }

    /**
     * Controller bean names that look like request paths.
     *
     * <p>The default member of {@code @Controller} names the bean. It does not map a request. A path
     * written there leaves the controller registered under that name while every request still starts
     * at the method or class mapping, which makes the annotation look effective while it decides the
     * wrong thing.
     *
     * @param source the Java source to read
     * @return one offence per path written as a controller bean name
     */
    static List<String> controllerPaths(CharSequence source) {
        String readable = JavaCode.withoutComments(source);
        Map<String, String> constants = STRING_CONSTANT.matcher(readable).results()
            .collect(Collectors.toMap(found -> found.group(1), found -> found.group(2)));
        return CONTROLLER.matcher(readable).results()
            .flatMap(found -> controllerPath(source, constants, found).stream())
            .toList();
    }

    private static Optional<String> controllerPath(
        CharSequence source, Map<String, String> constants, MatchResult found
    ) {
        return resolvedString(constants, found.group(1))
            .filter(value -> value.contains("/"))
            .map(
                value -> "line " + JavaCode.lineOf(source, found.start())
                    + ": @Controller names the bean " + value
                    + " rather than mapping a request; move the path to a class-level @RequestMapping"
            );
    }

    private static Optional<String> resolvedString(Map<String, String> constants, String written) {
        return written.startsWith("\"")
            ? Optional.of(written.substring(1, written.length() - 1))
            : Optional.ofNullable(constants.get(written));
    }

    private static Stream<String> calls(
        CharSequence source, String code, SpringMembers.Member bean, List<String> names
    ) {
        return SpringMembers.callsWithin(code, bean.start(), bean.end(), names).stream()
            .map(call -> offence(source, bean, call));
    }

    private static String declaredPackage(CharSequence code) {
        Matcher declaration = PACKAGE.matcher(code);
        return declaration.find() ? declaration.group(1) : "";
    }

    private static String offence(CharSequence source, SpringMembers.Member bean, MatchResult call) {
        return "line " + JavaCode.lineOf(source, call.start(1)) + ": bean method " + bean.name()
            + " calls " + call.group(1) + ", which builds a second instance the container never sees";
    }
}
