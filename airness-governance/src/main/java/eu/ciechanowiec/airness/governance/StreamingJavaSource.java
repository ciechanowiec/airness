package eu.ciechanowiec.airness.governance;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Parses Java source through the JDK without loading application classes.
 */
@UtilityClass
final class StreamingJavaSource {

    @SneakyThrows
    static CompilationUnitTree read(Path path, String content) {
        JavaCompiler compiler = Optional.ofNullable(ToolProvider.getSystemJavaCompiler()).orElseThrow();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (
            StandardJavaFileManager files = compiler.getStandardFileManager(
                diagnostics, Locale.ROOT, StandardCharsets.UTF_8
            )
        ) {
            JavaFileObject source = SimpleJavaFileObject.forSource(path.toUri(), content);
            JavacTask task = (JavacTask) compiler.getTask(
                new StringWriter(), files, diagnostics,
                List.of("-proc:none"), List.of(), List.of(source)
            );
            CompilationUnitTree unit = task.parse().iterator().next();
            if (
                diagnostics.getDiagnostics().stream().map(Diagnostic::getKind).anyMatch(Diagnostic.Kind.ERROR::equals)
            ) {
                throw new IllegalArgumentException("Cannot parse production streaming declarations in " + path);
            }
            return unit;
        }
    }
}
