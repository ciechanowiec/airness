package eu.ciechanowiec.airness.errorprone;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import lombok.experimental.UtilityClass;

/**
 * Compiles a fixture with the deprecation flags Airness compiles every source with.
 */
@UtilityClass
final class JavacRule {

    static boolean refusesDeprecation(Path directory, String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
            Path file = ErrorProneRule.write(Files.createDirectories(directory), "main", source);
            List<String> options = List.of(
                "-proc:none", "-Xlint:deprecation,removal", "-Werror",
                "-d", Files.createDirectories(directory.resolve("classes")).toString(),
                "-cp", System.getProperty("java.class.path")
            );
            boolean compiled = compiler.getTask(
                new StringWriter(), manager, diagnostics, options, null, manager.getJavaFileObjects(file)
            ).call();
            boolean deprecated = diagnostics.getDiagnostics().stream()
                .filter(JavacRule::isWarning)
                .anyMatch(diagnostic -> diagnostic.getMessage(Locale.ROOT).contains("has been deprecated"));
            return !compiled && deprecated;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static boolean isWarning(Diagnostic<? extends JavaFileObject> diagnostic) {
        return diagnostic.getKind() == Diagnostic.Kind.MANDATORY_WARNING || diagnostic.getKind()
            == Diagnostic.Kind.WARNING;
    }
}
