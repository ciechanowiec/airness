package eu.ciechanowiec.airness.maven;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.lang.model.SourceVersion;
import lombok.experimental.UtilityClass;

/**
 * Confirms that the package prefix supplied to NullAway contains every ordinary Java source package.
 */
@UtilityClass
final class PackageRoots {

    private static final Pattern DECLARATION = Pattern.compile(
        "(?m)^\\s*package\\s+(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*"
            + "(?:\\.\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)*)\\s*;"
    );
    // A text block consumes its escapes, so a block embedding its own delimiter is masked whole. Ending
    // the token at the first three quotation marks leaves the rest of the block readable as code, and a
    // package declaration quoted in a fixture would then answer for the file quoting it. Each branch
    // reads runs rather than single characters, because a matcher spends a stack frame on each turn of a
    // group it repeats and a text block is as long as its author made it. This is the spelling the lexer
    // of airness-governance carries, and the two are meant to read a source the same way.
    private static final Pattern NON_CODE = Pattern.compile(
        "(?s)\"\"\"(?:\\\\.|[^\"\\\\]++|\"{1,2}+(?!\"))*+\"\"\"|\"(?:\\\\.|[^\"\\\\\\n]++)*+\"|'(?:\\\\.|[^'\\\\]++)*+'"
            + "|/\\*.*?\\*/|//[^\\n]*"
    );
    private static final String JAVA = ".java";
    private static final String MODULE = "module-info.java";
    private static final char NEWLINE = '\n';

    static List<String> problems(String root, Collection<Path> sourceRoots) {
        if (!SourceVersion.isName(root)) {
            return List.of("airness.package.root is not a valid Java package name: " + root);
        }
        return sources(sourceRoots)
            .map(source -> problem(root, source))
            .flatMap(Optional::stream)
            .toList();
    }

    private static Stream<Path> sources(Collection<Path> roots) {
        return roots.stream().filter(Files::isDirectory).flatMap(PackageRoots::walk)
            .filter(Files::isRegularFile)
            .filter(path -> path.getFileName().toString().endsWith(JAVA))
            .filter(path -> !MODULE.equals(path.getFileName().toString()));
    }

    private static Stream<Path> walk(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.toList().stream();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not inspect Java packages under " + root, exception);
        }
    }

    private static Optional<String> problem(String root, Path source) {
        Optional<String> declared = declared(source);
        if (declared.isEmpty()) {
            return Optional.of(source + " declares no package, so it is outside " + root);
        }
        String held = declared.orElseThrow();
        return Optional.of(source + " declares package " + held + ", which is outside " + root)
            .filter(_ -> !held.equals(root) && !held.startsWith(root + '.'));
    }

    private static Optional<String> declared(Path source) {
        try {
            return DECLARATION.matcher(codeOnly(Files.readString(source))).results()
                .findFirst()
                .map(match -> match.group(1));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + source, exception);
        }
    }

    private static String codeOnly(String source) {
        StringBuilder code = new StringBuilder(source);
        NON_CODE.matcher(source).results().forEach(token -> mask(code, token));
        return code.toString();
    }

    private static void mask(StringBuilder code, MatchResult token) {
        IntStream.range(token.start(), token.end())
            .filter(index -> code.charAt(index) != NEWLINE)
            .forEach(index -> code.setCharAt(index, ' '));
    }
}
