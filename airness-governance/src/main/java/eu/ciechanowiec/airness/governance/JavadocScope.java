package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The simple type names one source file can resolve, which is what decides whether a name in its
 * Javadoc had to be a link. A file resolves what it imports, what its own package declares, and what
 * {@code java.lang} exports, so those three make up the set, and they are the same three the compiler
 * uses. That is the point: a name outside them cannot be linked without qualifying it, so naming it in
 * prose is a choice rather than an omission.
 *
 * <p>Membership of {@code java.lang} is asked of the runtime rather than kept as a list here, so the
 * answer is the whole package and cannot drift from it.
 */
final class JavadocScope {

    private static final Pattern IMPORT = Pattern.compile("(?m)^import\\s+(?:static\\s+)?([\\w.]+);");
    private static final Pattern DECLARED_TYPE = Pattern.compile(
        "\\b(?:class|interface|enum|record|@interface)\\s+([A-Z][A-Za-z0-9]*)\\b"
    );
    private static final String JAVA_LANG = "java.lang.";
    private static final String JAVA = ".java";

    private final Map<Path, Set<String>> neighbours;

    private JavadocScope(Map<Path, Set<String>> neighbours) {
        this.neighbours = Map.copyOf(neighbours);
    }

    /**
     * The scope of every source in {@code sources}, indexed so each file can be told what its own
     * package declares.
     */
    static JavadocScope over(Collection<Path> sources) {
        return new JavadocScope(
            sources.stream().collect(
                Collectors.groupingBy(
                    Path::getParent,
                    Collectors.flatMapping(JavadocScope::declaredBy, Collectors.toUnmodifiableSet())
                )
            )
        );
    }

    /**
     * Whether a simple name resolves from {@code source}, whose content is {@code text}.
     */
    Predicate<String> of(Path source, CharSequence text) {
        Set<String> named = Stream.concat(
            this.neighbours.getOrDefault(source.getParent(), Set.of()).stream(),
            Stream.concat(imported(text), declared(text))
        ).collect(Collectors.toUnmodifiableSet());
        return name -> named.contains(name) || inJavaLang(name);
    }

    private static boolean inJavaLang(String name) {
        try {
            Class.forName(JAVA_LANG + name, false, ClassLoader.getSystemClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError _) {
            return false;
        }
    }

    private static Stream<String> imported(CharSequence text) {
        return IMPORT.matcher(JavadocLinkRules.codeOnly(text)).results()
            .map(MatchResult::group)
            .map(JavadocScope::simpleName);
    }

    private static Stream<String> declared(CharSequence text) {
        return DECLARED_TYPE.matcher(JavadocLinkRules.codeOnly(text)).results()
            .map(match -> match.group(1));
    }

    private static Stream<String> declaredBy(Path source) {
        return Stream.concat(
            Stream.of(typeName(source)), Repository.readText(source).stream().flatMap(JavadocScope::declared)
        );
    }

    private static String simpleName(String importLine) {
        String qualified = importLine.replace("import ", "").replace("static ", "").replace(";", "").strip();
        return qualified.substring(qualified.lastIndexOf('.') + 1);
    }

    private static String typeName(Path source) {
        String file = source.getFileName().toString();
        return file.substring(0, file.length() - JAVA.length());
    }
}
