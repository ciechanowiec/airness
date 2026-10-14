package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import java.io.Serial;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;

/**
 * Writes a JUnit Jupiter assertion in the form that names what it checks.
 *
 * <p>{@code assertEquals(null, value)} is {@code assertNull}, asserting an instanceof test is
 * {@code assertInstanceOf}, an integral comparison needs no delta, and an assertion is called through its
 * static import rather than through {@code Assertions}. Only the throwing call belongs inside {@code assertThrows}.
 */
@BugPattern(
    summary = "Write the JUnit assertion in the form that names what it checks",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessJupiterAssertionIsDirect extends BugChecker implements MethodInvocationTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String ASSERTIONS = "org.junit.jupiter.api.Assertions";

    private static final Matcher<ExpressionTree> ANY = MethodMatchers.staticMethod().onClass(ASSERTIONS);

    private static final String ASSERT_EQUALS = "assertEquals";

    private static final List<Form> FORMS = List.of(
        new Form(
            "", (call, _) -> call.getMethodSelect() instanceof MemberSelectTree,
            "Call the assertion through its static import rather than through Assertions"
        ),
        new Form(
            ASSERT_EQUALS, (call, _) -> call.getArguments().size() >= 2 && comparesNull(call.getArguments()),
            "Use assertNull rather than comparing with null"
        ),
        new Form(
            "assertTrue", (call, _) -> ASTHelpers.stripParentheses(
                call.getArguments().getFirst()
            ) instanceof InstanceOfTree,
            "Use assertInstanceOf rather than asserting an instanceof"
        ),
        new Form(
            ASSERT_EQUALS, (call, state) -> takesDelta(call) && integral(call.getArguments(), state),
            "Compare integral values without a delta"
        ),
        new Form(
            "assertThrows", (call, _) -> call.getArguments().size() >= 2
                && preparesInside(call.getArguments().get(1)),
            "Prepare outside assertThrows so that only the throwing call stays inside it"
        )
    );

    @Override
    public Description matchMethodInvocation(MethodInvocationTree call, VisitorState state) {
        if (!ANY.matches(call, state)) {
            return Description.NO_MATCH;
        }
        return message(call, state)
            .map(message -> this.buildDescription(call).setMessage(message).build())
            .orElse(Description.NO_MATCH);
    }

    private static Optional<String> message(MethodInvocationTree call, VisitorState state) {
        String name = ASTHelpers.getSymbol(call).getSimpleName().toString();
        return FORMS.stream()
            .filter(form -> form.method().isEmpty() || form.method().equals(name))
            .filter(form -> form.applies().test(call, state))
            .map(Form::message)
            .findFirst();
    }

    private static boolean takesDelta(MethodInvocationTree call) {
        List<Symbol.VarSymbol> parameters = ASTHelpers.getSymbol(call).getParameters();
        return parameters.size() == 3 && switch (parameters.get(2).asType().getKind()) {
            case DOUBLE, FLOAT -> true;
            default -> false;
        };
    }

    private static boolean preparesInside(ExpressionTree executable) {
        return executable instanceof LambdaExpressionTree lambda
            && lambda.getBody() instanceof BlockTree block
            && block.getStatements().size() > 1;
    }

    private static boolean comparesNull(List<? extends ExpressionTree> arguments) {
        return arguments.getFirst().getKind() == Tree.Kind.NULL_LITERAL
            || arguments.get(1).getKind() == Tree.Kind.NULL_LITERAL;
    }

    private static boolean integral(List<? extends ExpressionTree> arguments, VisitorState state) {
        return arguments.subList(0, 2).stream()
            .map(ASTHelpers::getType)
            .map(type -> state.getTypes().unboxedTypeOrType(type))
            .allMatch(AirnessJupiterAssertionIsDirect::integralType);
    }

    private static boolean integralType(Type type) {
        return switch (type.getKind()) {
            case BYTE, SHORT, CHAR, INT, LONG -> true;
            default -> false;
        };
    }

    private record Form(String method, BiPredicate<MethodInvocationTree, VisitorState> applies, String message) {
    }
}
