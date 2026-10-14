package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.ClassTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.TryTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.VariableTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;
import java.io.Serial;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeKind;

/**
 * Keeps the shape of a JUnit Jupiter test to what the engine reads.
 *
 * <p>A test returns nothing and is not named for being a test. A parameterized one says so and is not also a
 * plain test. A test declares that it throws {@link Exception} rather than listing what it may raise. A
 * single-column {@code @CsvSource} is a {@code @ValueSource}, a {@code @TempDir} field is injected and so
 * not final, an inner class holding tests is {@code @Nested}, and a test lets an exception fail it rather
 * than catching it to call {@code fail}.
 */
@BugPattern(
    summary = "Shape the JUnit test the way the engine reads it",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessJupiterTestIsWellFormed extends BugChecker
                                                  implements MethodTreeMatcher, ClassTreeMatcher, VariableTreeMatcher,
                                                  TryTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String TEST = "org.junit.jupiter.api.Test";

    private static final String PARAMETERIZED = "org.junit.jupiter.params.ParameterizedTest";

    private static final Pattern PREFIXED = Pattern.compile("^test(?:_+|(?=\\p{Lu}))(.+)$");

    private static final Matcher<ExpressionTree> FAIL = MethodMatchers.staticMethod()
        .onClass("org.junit.jupiter.api.Assertions").named("fail");

    @Override
    public Description matchMethod(MethodTree method, VisitorState state) {
        Symbol.MethodSymbol symbol = ASTHelpers.getSymbol(method);
        Optional<String> csv = Optional.of("A single-column @CsvSource feeding one parameter is a @ValueSource")
            .filter(_ -> singleColumnSource(method));
        return csv.or(() -> testProblem(method, symbol, state))
            .map(message -> this.message(method, message))
            .orElse(Description.NO_MATCH);
    }

    private static Optional<String> testProblem(MethodTree method, Symbol.MethodSymbol symbol, VisitorState state) {
        return ASTHelpers.hasAnnotation(symbol, TEST, state) ? problem(method, symbol, state) : Optional.empty();
    }

    @Override
    public Description matchClass(ClassTree declaration, VisitorState state) {
        Symbol.ClassSymbol symbol = ASTHelpers.getSymbol(declaration);
        boolean inner = symbol.getNestingKind().isNested() && !symbol.getModifiers().contains(Modifier.STATIC)
            && symbol.getKind() == ElementKind.CLASS;
        boolean holdsTests = declaration.getMembers().stream()
            .filter(MethodTree.class::isInstance)
            .anyMatch(member -> ASTHelpers.hasAnnotation(ASTHelpers.getSymbol(member), TEST, state));
        boolean nested = ASTHelpers.hasAnnotation(symbol, "org.junit.jupiter.api.Nested", state);
        return inner && holdsTests && !nested
            ? this.message(declaration, "Annotate the inner class with @Nested so that the engine runs its tests")
            : Description.NO_MATCH;
    }

    @Override
    public Description matchVariable(VariableTree field, VisitorState state) {
        Symbol.VarSymbol symbol = ASTHelpers.getSymbol(field);
        boolean injectedFinal = ASTHelpers.hasAnnotation(symbol, "org.junit.jupiter.api.io.TempDir", state)
            && symbol.getModifiers().contains(Modifier.FINAL);
        return injectedFinal
            ? this.message(field, "The engine injects a @TempDir field, so it cannot be final") : Description.NO_MATCH;
    }

    @Override
    public Description matchTry(TryTree statement, VisitorState state) {
        boolean plain = Optional.ofNullable(statement.getFinallyBlock()).isEmpty() && statement.getResources()
            .isEmpty();
        boolean onlyFails = plain && statement.getCatches().size() == 1 && onlyCallsFail(statement, state);
        return onlyFails
            ? this.message(statement, "Let the exception fail the test, or wrap the call in assertDoesNotThrow")
            : Description.NO_MATCH;
    }

    private static boolean onlyCallsFail(TryTree statement, VisitorState state) {
        List<? extends StatementTree> handler = statement.getCatches().getFirst().getBlock().getStatements();
        return handler.size() == 1
            && handler.getFirst() instanceof ExpressionStatementTree failure
            && FAIL.matches(failure.getExpression(), state);
    }

    private static Optional<String> problem(MethodTree method, Symbol.MethodSymbol symbol, VisitorState state) {
        boolean template = Stream.of(PARAMETERIZED, "org.junit.jupiter.api.RepeatedTest")
            .anyMatch(annotation -> ASTHelpers.hasAnnotation(symbol, annotation, state));
        return Finding.first(
            new Finding(symbol.getReturnType().getKind() != TypeKind.VOID, "A test returns nothing; make it void"),
            new Finding(template, "A method is either a @Test or a test template; remove @Test"),
            new Finding(hasArgumentSource(method), "A test given arguments is a @ParameterizedTest"),
            new Finding(
                method.getThrows().size() > 1,
                "Declare throws Exception rather than listing the exceptions a test may raise"
            )
        ).or(
            () -> unprefixedName(symbol).map(
                name -> "Name the test for its behaviour, " + name + ", rather than for being a test"
            )
        );
    }

    private static boolean hasArgumentSource(MethodTree method) {
        return method.getModifiers().getAnnotations().stream()
            .map(AirnessJupiterTestIsWellFormed::annotationName)
            .anyMatch(name -> name.startsWith("org.junit.jupiter.params.provider.") && name.endsWith("Source"));
    }

    private static Optional<String> unprefixedName(Symbol.MethodSymbol symbol) {
        return Optional.of(symbol.getSimpleName().toString())
            .filter(name -> PREFIXED.matcher(name).matches())
            .map(name -> PREFIXED.matcher(name).replaceFirst("$1"))
            .map(rest -> Character.toLowerCase(rest.charAt(0)) + rest.substring(1))
            .filter(candidate -> SourceVersion.isIdentifier(candidate) && !SourceVersion.isKeyword(candidate))
            .filter(
                candidate -> ASTHelpers.getEnclosedElements(symbol.owner).stream()
                    .noneMatch(member -> member.getSimpleName().contentEquals(candidate))
            );
    }

    private Description message(Tree tree, String message) {
        return this.buildDescription(tree).setMessage(message).build();
    }

    private static boolean singleColumnSource(MethodTree method) {
        return method.getParameters().size() == 1 && method.getModifiers().getAnnotations().stream()
            .filter(AirnessJupiterTestIsWellFormed::isCsvSource)
            .anyMatch(AirnessJupiterTestIsWellFormed::singleColumn);
    }

    private static boolean isCsvSource(AnnotationTree annotation) {
        return "org.junit.jupiter.params.provider.CsvSource".equals(annotationName(annotation));
    }

    private static String annotationName(AnnotationTree annotation) {
        return Optional.ofNullable(ASTHelpers.getType(annotation.getAnnotationType()))
            .map(type -> type.tsym.getQualifiedName().toString())
            .orElse("");
    }

    private static boolean singleColumn(AnnotationTree source) {
        return source.getArguments().stream().allMatch(AirnessJupiterTestIsWellFormed::isValue)
            && source.getArguments().stream()
                .map(argument -> argument instanceof AssignmentTree assignment ? assignment.getExpression() : argument)
                .flatMap(AirnessJupiterTestIsWellFormed::elements)
                .map(value -> Optional.ofNullable(ASTHelpers.constValue(value, String.class)))
                .allMatch(value -> value.filter(text -> !text.contains(",")).isPresent());
    }

    private static boolean isValue(ExpressionTree argument) {
        return !(argument instanceof AssignmentTree assignment)
            || Optional.ofNullable(ASTHelpers.getSymbol(assignment.getVariable()))
                .filter(attribute -> attribute.getSimpleName().contentEquals("value")).isPresent();
    }

    private static Stream<? extends ExpressionTree> elements(ExpressionTree argument) {
        return argument instanceof NewArrayTree array ? array.getInitializers().stream() : Stream.of(argument);
    }
}
