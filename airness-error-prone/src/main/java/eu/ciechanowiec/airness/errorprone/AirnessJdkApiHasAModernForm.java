package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.NewClassTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.google.errorprone.util.TargetType;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;
import java.io.Serial;
import java.util.Map;
import java.util.Optional;

/**
 * Calls the current JDK method where an older call says the same with more ceremony.
 *
 * <p>Reported are {@link Math#random()}, {@link Process#waitFor(long, java.util.concurrent.TimeUnit)}, a
 * {@code StringReader}
 * created only to be read as a {@code Reader}, and {@code StandardCharsets.UTF_8} passed to a
 * {@code Files} method whose default is already UTF-8.
 */
@BugPattern(
    summary = "Use the current JDK form of this call",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessJdkApiHasAModernForm extends BugChecker
                                               implements MethodInvocationTreeMatcher, NewClassTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> RANDOM = MethodMatchers.staticMethod()
        .onClass("java.lang.Math").named("random").withNoParameters();

    private static final Matcher<ExpressionTree> WAIT_FOR = MethodMatchers.instanceMethod()
        .onDescendantOf("java.lang.Process").named("waitFor")
        .withParameters("long", "java.util.concurrent.TimeUnit");

    private static final Map<String, Integer> CHARSET_POSITION = Map.of(
        "lines", 1, "readString", 1, "readAllLines", 1, "newBufferedReader", 1,
        "newBufferedWriter", 1, "write", 2, "writeString", 2
    );

    @Override
    public Description matchMethodInvocation(MethodInvocationTree call, VisitorState state) {
        return Finding.first(
            new Finding(RANDOM.matches(call, state), "Draw from ThreadLocalRandom.current() rather than Math.random()"),
            new Finding(
                WAIT_FOR.matches(call, state), "Pass a Duration to waitFor rather than an amount and a TimeUnit"
            ),
            new Finding(redundantCharset(call), "Files reads and writes UTF-8 by default; drop StandardCharsets.UTF_8")
        ).map(message -> this.message(call, message)).orElse(Description.NO_MATCH);
    }

    @Override
    public Description matchNewClass(NewClassTree creation, VisitorState state) {
        boolean stringReader = Optional.ofNullable(creation.getClassBody()).isEmpty()
            && Optional.ofNullable(ASTHelpers.getType(creation))
                .filter(created -> created.tsym.getQualifiedName().contentEquals("java.io.StringReader")).isPresent()
            && creation.getArguments().size() == 1;
        return stringReader && readAsReader(state)
            ? this.message(creation, "Create the reader with Reader.of(text)") : Description.NO_MATCH;
    }

    private static boolean readAsReader(VisitorState state) {
        Tree parent = state.getPath().getParentPath().getLeaf();
        boolean returnedOrStored = parent instanceof ReturnTree || parent instanceof VariableTree;
        return returnedOrStored && Optional.ofNullable(TargetType.targetType(state))
            .map(target -> target.type().tsym.getQualifiedName().contentEquals("java.io.Reader"))
            .orElse(false);
    }

    private static boolean redundantCharset(MethodInvocationTree call) {
        Symbol.MethodSymbol method = ASTHelpers.getSymbol(call);
        boolean files = method.owner.getQualifiedName().contentEquals("java.nio.file.Files");
        return files && Optional.ofNullable(CHARSET_POSITION.get(method.getSimpleName().toString()))
            .filter(position -> call.getArguments().size() > position)
            .map(position -> ASTHelpers.getSymbol(call.getArguments().get(position)))
            .filter(charset -> charset.getSimpleName().contentEquals("UTF_8"))
            .filter(charset -> charset.owner.getQualifiedName().contentEquals("java.nio.charset.StandardCharsets"))
            .isPresent();
    }

    private Description message(ExpressionTree tree, String message) {
        return this.buildDescription(tree).setMessage(message).build();
    }
}
