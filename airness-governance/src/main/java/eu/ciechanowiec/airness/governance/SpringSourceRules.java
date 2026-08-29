package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Reads a Spring source and reports the two constructs the container accepts and then does not honour.
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
 * <p>Both read the source with its comments and literals blanked, so a name inside a string or an
 * explanation is not mistaken for code. The scan reads one file at a time, which is what the one
 * top-level class per file rule makes sufficient.
 */
@UtilityClass
final class SpringSourceRules {

    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern ENTRY_POINT = Pattern.compile("@SpringBootApplication\\b");
    private static final Pattern BEAN = Pattern.compile("@Bean\\b");

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
