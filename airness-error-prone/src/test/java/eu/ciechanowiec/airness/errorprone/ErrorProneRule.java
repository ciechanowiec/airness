package eu.ciechanowiec.airness.errorprone;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import lombok.experimental.UtilityClass;

/**
 * Compiles fixture sources with one Airness check enabled and reports the lines it flagged.
 */
@UtilityClass
final class ErrorProneRule {

    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([\\w.]+);", Pattern.MULTILINE);
    private static final Pattern TYPE = Pattern.compile(
        "(?:^|[;}\\s])(?:public\\s+|final\\s+|abstract\\s+)*(?:class|interface|enum|record|@interface)\\s+(\\w+)"
    );

    static List<Long> findings(Path directory, String check, String... sources) {
        return findingsIn(directory, check, "main", sources);
    }

    static List<Long> findingsIn(Path directory, String check, String scope, String... sources) {
        List<Path> files = Arrays.stream(sources).map(source -> write(directory, scope, source)).toList();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
            List<String> options = List.of(
                "-XDcompilePolicy=simple",
                "--should-stop=ifError=FLOW",
                "-proc:none",
                "-processorpath", System.getProperty("java.class.path"),
                "-d", Files.createDirectories(directory.resolve("classes")).toString(),
                "-cp", System.getProperty("java.class.path"),
                "-Xplugin:ErrorProne -XepDisableAllChecks -Xep:" + check + ":ERROR"
            );
            compiler.getTask(
                new StringWriter(), manager, diagnostics, options, null, manager.getJavaFileObjectsFromPaths(files)
            ).call();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        List<Diagnostic<? extends JavaFileObject>> reported = diagnostics.getDiagnostics();
        List<String> foreign = reported.stream()
            .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
            .map(diagnostic -> diagnostic.getMessage(Locale.ROOT))
            .filter(message -> !message.startsWith("[" + check + "]"))
            .toList();
        if (!foreign.isEmpty()) {
            throw new IllegalStateException("The fixture does not compile: " + foreign);
        }
        return reported.stream()
            .filter(diagnostic -> diagnostic.getMessage(Locale.ROOT).startsWith("[" + check + "]"))
            .map(Diagnostic::getLineNumber)
            .toList();
    }

    static Path write(Path directory, String scope, String source) {
        Matcher packageName = PACKAGE.matcher(source);
        Matcher type = TYPE.matcher(source);
        if (!type.find()) {
            throw new IllegalArgumentException("A fixture declares no type: " + source);
        }
        String folder = packageName.find() ? packageName.group(1).replace('.', '/') : "";
        try {
            Path file = Files.createDirectories(directory.resolve("src").resolve(scope).resolve("java").resolve(folder))
                .resolve(type.group(1) + ".java");
            return Files.writeString(file, source);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
