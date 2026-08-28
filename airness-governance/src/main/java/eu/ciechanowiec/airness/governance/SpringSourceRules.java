package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Optional;
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
    private static final Pattern DECLARATION = Pattern.compile("\\b(\\w+)\\s*\\(");
    private static final char ANNOTATION = '@';

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
        List<Bean> beans = beans(code);
        List<String> names = beans.stream().map(Bean::name).toList();
        return beans.stream().flatMap(bean -> callsWithin(source, code, bean, names).stream()).toList();
    }

    private static String declaredPackage(CharSequence code) {
        Matcher declaration = PACKAGE.matcher(code);
        return declaration.find() ? declaration.group(1) : "";
    }

    private static List<String> callsWithin(
        CharSequence source, String code, Bean bean, List<String> names
    ) {
        return DECLARATION.matcher(code).results()
            .filter(call -> call.start() >= bean.body() && call.end() <= bean.end())
            .filter(call -> names.contains(call.group(1)))
            .map(call -> offence(source, bean, call))
            .toList();
    }

    private static String offence(CharSequence source, Bean bean, MatchResult call) {
        return "line " + JavaCode.lineOf(source, call.start(1)) + ": bean method " + bean.name()
            + " calls " + call.group(1) + ", which builds a second instance the container never sees";
    }

    private static List<Bean> beans(String code) {
        return BEAN.matcher(code).results()
            .map(annotation -> declaration(code, annotation.end()))
            .flatMap(Optional::stream)
            .toList();
    }

    private static Optional<Bean> declaration(String code, int from) {
        return declarations(code, from)
            .filter(match -> code.charAt(match.start(1) - 1) != ANNOTATION)
            .findFirst()
            .flatMap(match -> bean(code, match));
    }

    private static Stream<MatchResult> declarations(String code, int from) {
        return DECLARATION.matcher(code).results().filter(match -> match.start() >= from);
    }

    private static Optional<Bean> bean(String code, MatchResult match) {
        return body(code, match.end()).map(range -> new Bean(match.group(1), range));
    }

    private static Optional<Range> body(String code, int parenthesis) {
        int opening = code.indexOf('{', close(code, parenthesis - 1));
        return opening < 0 ? Optional.empty() : Optional.of(new Range(opening, matching(code, opening)));
    }

    private static int close(String code, int opening) {
        return balanced(code, opening, '(', ')');
    }

    private static int matching(String code, int opening) {
        return balanced(code, opening, '{', '}');
    }

    private static int balanced(String code, int opening, char open, char shut) {
        int depth = 0;
        for (int at = opening; at < code.length(); at++) {
            depth += step(code.charAt(at), open, shut);
            if (depth == 0) {
                return at;
            }
        }
        return code.length();
    }

    private static int step(char character, char open, char shut) {
        int opened = character == open ? 1 : 0;
        return character == shut ? -1 : opened;
    }

    /**
     * The half-open source range one construct occupies.
     *
     * @param start the offset the construct opens at
     * @param end   the offset it closes at
     */
    private record Range(int start, int end) {
    }

    /**
     * One bean method, by name and by the range of its body.
     *
     * @param name  the declared method name
     * @param range the range its body occupies
     */
    private record Bean(String name, Range range) {

        int body() {
            return this.range.start();
        }

        int end() {
            return this.range.end();
        }
    }
}
