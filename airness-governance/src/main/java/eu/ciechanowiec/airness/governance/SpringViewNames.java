package eu.ciechanowiec.airness.governance;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * Written view references paired with the executable that actually returns them.
 *
 * <p>The JDK parser supplies declaration boundaries without resolving a consumer's classpath or running
 * annotation processors. A lambda, local class and anonymous class each have their own return target.
 * Treating their braces as ordinary control flow would accuse response data of naming a template.
 */
@UtilityClass
final class SpringViewNames {

    private static final Pattern CANDIDATE = Pattern.compile(
        "\\b(?:Controller|ControllerAdvice|RestController|RestControllerAdvice|ModelAndView)\\b"
    );
    private static final Pattern CONSTANT = Pattern.compile("[A-Z][A-Z0-9_]*");
    private static final Set<String> MODELLED = Set.of("ModelAndView", "org.springframework.web.servlet.ModelAndView");
    private static final SpringViewScope OUTSIDE = new SpringViewScope(Set.of(), false);

    /**
     * Reads statically written references while keeping every original source offset.
     *
     * @param source the production source being checked
     * @return the written values and the positions that report them
     */
    static List<Reference> in(SpringTypes.Declared source) {
        return CANDIDATE.matcher(source.code()).find() ? parsed(source) : List.of();
    }

    private static List<Reference> parsed(SpringTypes.Declared source) {
        JavaCompiler compiler = Optional.ofNullable(ToolProvider.getSystemJavaCompiler()).orElseThrow();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (
            StandardJavaFileManager files = compiler.getStandardFileManager(
                diagnostics, Locale.ROOT, StandardCharsets.UTF_8
            )
        ) {
            JavaFileObject file = SimpleJavaFileObject.forSource(source.source().toUri(), source.text().toString());
            JavacTask task = (JavacTask) compiler.getTask(
                new StringWriter(), files, diagnostics, List.of("-proc:none"), List.of(), List.of(file)
            );
            CompilationUnitTree unit = task.parse().iterator().next();
            ViewReferences reading = new ViewReferences(unit, Trees.instance(task).getSourcePositions());
            reading.scan(unit, OUTSIDE);
            List<String> errors = diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                .map(diagnostic -> "line " + diagnostic.getLineNumber() + ": " + diagnostic.getMessage(Locale.ROOT))
                .toList();
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException(source.source() + ": cannot read view references: " + errors);
            }
            return reading.references();
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read view references in " + source.source(), exception);
        }
    }

    /**
     * A literal or constant name with the offset of the return or construction that names the view.
     *
     * @param written the expression as understood by the existing constant resolver
     * @param offset  the original source offset
     */
    public record Reference(String written, int offset) {
    }

    /**
     * A visit carries its own scope, so visiting a sibling never changes what the next sibling inherits.
     */
    private static final class ViewReferences extends TreeScanner<@Nullable Void, SpringViewScope> {

        private final CompilationUnitTree unit;
        private final SourcePositions positions;
        private final List<Reference> names;

        private ViewReferences(CompilationUnitTree unit, SourcePositions positions) {
            this.unit = unit;
            this.positions = positions;
            this.names = new ArrayList<>();
        }

        @Override
        public @Nullable Void visitClass(ClassTree tree, SpringViewScope scope) {
            return super.visitClass(tree, SpringViewScope.type(tree.getModifiers()));
        }

        @Override
        public @Nullable Void visitMethod(MethodTree tree, SpringViewScope scope) {
            return super.visitMethod(tree, scope.method(tree));
        }

        @Override
        public @Nullable Void visitLambdaExpression(LambdaExpressionTree tree, SpringViewScope scope) {
            return super.visitLambdaExpression(tree, OUTSIDE);
        }

        @Override
        public @Nullable Void visitReturn(ReturnTree tree, SpringViewScope scope) {
            if (scope.view()) {
                Optional.ofNullable(tree.getExpression()).ifPresent(expression -> this.keep(tree, expression));
            }
            return super.visitReturn(tree, scope);
        }

        @Override
        public @Nullable Void visitNewClass(NewClassTree tree, SpringViewScope scope) {
            if (MODELLED.contains(tree.getIdentifier().toString()) && !tree.getArguments().isEmpty()) {
                this.keep(tree, tree.getArguments().getFirst());
            }
            return super.visitNewClass(tree, scope);
        }

        List<Reference> references() {
            return List.copyOf(this.names);
        }

        private void keep(Tree tree, ExpressionTree expression) {
            int offset = Math.toIntExact(this.positions.getStartPosition(this.unit, tree));
            written(expression).ifPresent(value -> this.names.add(new Reference(value, offset)));
        }

        private static Optional<String> written(ExpressionTree expression) {
            if (expression instanceof LiteralTree literal && literal.getValue() instanceof String value) {
                return Optional.of('"' + value + '"');
            }
            return expression instanceof IdentifierTree identifier
                ? Optional.of(identifier.getName().toString()).filter(name -> CONSTANT.matcher(name).matches())
                : Optional.empty();
        }
    }
}
