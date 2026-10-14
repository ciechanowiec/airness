package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.IdentifierTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.MemberSelectTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.suppliers.Suppliers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import java.io.Serial;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Writes a test with JUnit Jupiter where it still reaches for a JUnit 4 or Hamcrest form that has one.
 *
 * <p>Reported are the JUnit 4 annotations, assertions, assumptions, rules and runners Jupiter replaces, a
 * Hamcrest {@code assertThat} whose matcher has a Jupiter assertion, a matcher wrapped in {@code is}, a
 * matcher reached through a class other than {@code Matchers}, and the reason-first boolean
 * {@code assertThat}. A module that also runs TestNG keeps its JUnit 4 forms, since migrating them there
 * would move tests between engines.
 */
@BugPattern(
    summary = "Write the test with the JUnit Jupiter form of this JUnit 4 or Hamcrest construct",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessJUnit4HasAJupiterForm extends BugChecker
                                                implements IdentifierTreeMatcher, MemberSelectTreeMatcher,
                                                MethodInvocationTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Set<String> REPLACED_TYPES = Set.of(
        "org.junit.Test", "org.junit.Before", "org.junit.After", "org.junit.BeforeClass", "org.junit.AfterClass",
        "org.junit.Ignore", "org.junit.Rule", "org.junit.ClassRule", "org.junit.Assert", "org.junit.Assume",
        "org.junit.FixMethodOrder", "org.junit.ComparisonFailure", "org.junit.function.ThrowingRunnable",
        "org.junit.experimental.categories.Category", "org.junit.rules.ExpectedException",
        "org.junit.rules.TemporaryFolder", "org.junit.rules.TestName", "org.junit.rules.Timeout",
        "org.junit.rules.ExternalResource", "org.junit.runners.Parameterized", "org.junit.runners.JUnit4",
        "org.junit.runners.BlockJUnit4ClassRunner", "org.junit.experimental.runners.Enclosed",
        "junit.framework.TestCase", "junit.framework.AssertionFailedError", "junitparams.JUnitParamsRunner"
    );

    private static final Set<String> CONVERTIBLE = Set.of(
        "closeTo", "containsString", "empty", "emptyArray", "emptyIterable", "endsWith", "equalTo",
        "equalToIgnoringCase", "greaterThan", "greaterThanOrEqualTo", "hasEntry", "hasKey", "hasSize",
        "hasToString", "hasValue", "lessThan", "lessThanOrEqualTo", "notNullValue", "nullValue", "sameInstance",
        "startsWith", "theInstance", "typeCompatibleWith", "instanceOf", "isA"
    );

    private static final Supplier<@Nullable Symbol> TEST_NG = VisitorState.memoize(
        state -> state.getSymbolFromString("org.testng.annotations.Test")
    );

    private static final Supplier<Type> MATCHER = Suppliers.typeFromString("org.hamcrest.Matcher");

    private static final String MATCHER_ASSERT = "org.hamcrest.MatcherAssert";

    private static final String MATCHERS = "org.hamcrest.Matchers";

    private static final String IS = "is";

    private static final Matcher<ExpressionTree> ASSERT_THAT = MethodMatchers.staticMethod()
        .onClass(MATCHER_ASSERT).named("assertThat");

    @Override
    public Description matchIdentifier(IdentifierTree identifier, VisitorState state) {
        return this.replacedType(identifier, state);
    }

    @Override
    public Description matchMemberSelect(MemberSelectTree select, VisitorState state) {
        return this.replacedType(select, state);
    }

    @Override
    public Description matchMethodInvocation(MethodInvocationTree call, VisitorState state) {
        Symbol.MethodSymbol method = ASTHelpers.getSymbol(call);
        String owner = method.owner.getQualifiedName().toString();
        boolean factory = method.isStatic() && owner.startsWith("org.hamcrest.") && !MATCHER_ASSERT.equals(owner);
        boolean assertion = ASSERT_THAT.matches(call, state);
        return Finding.first(
            new Finding(factory && !MATCHERS.equals(owner), "Reach the matcher through org.hamcrest.Matchers"),
            new Finding(
                factory && method.getSimpleName().contentEquals(IS) && wrapsMatcher(call, state),
                "The is() wrapper adds nothing to a matcher; pass the matcher itself"
            ),
            new Finding(
                assertion && reasonFirst(call),
                "Use assertTrue for a condition rather than Hamcrest's reason-first assertThat"
            ),
            new Finding(assertion && convertible(call), "Use the JUnit Jupiter assertion this matcher spells out")
        ).map(message -> this.message(call, message)).orElse(Description.NO_MATCH);
    }

    private static boolean reasonFirst(MethodInvocationTree call) {
        return call.getArguments().size() == 2
            && Optional.ofNullable(ASTHelpers.getType(call.getArguments().get(1))).filter(Type::isPrimitive)
                .isPresent();
    }

    private static boolean convertible(MethodInvocationTree call) {
        return call.getArguments().getLast() instanceof MethodInvocationTree factory
            && CONVERTIBLE.contains(ASTHelpers.getSymbol(factory).getSimpleName().toString());
    }

    private Description replacedType(Tree reference, VisitorState state) {
        boolean imported = Optional.ofNullable(state.findEnclosing(ImportTree.class)).isPresent();
        return Optional.ofNullable(ASTHelpers.getSymbol(reference))
            .filter(symbol -> symbol.getKind().isClass() || symbol.getKind().isInterface())
            .filter(symbol -> REPLACED_TYPES.contains(symbol.getQualifiedName().toString()))
            .filter(_ -> !imported && !runsTestNg(state))
            .map(symbol -> this.message(reference, "Use the JUnit Jupiter replacement for " + symbol.getSimpleName()))
            .orElse(Description.NO_MATCH);
    }

    private static boolean wrapsMatcher(MethodInvocationTree call, VisitorState state) {
        return call.getArguments().size() == 1 && ASTHelpers.isSubtype(
            ASTHelpers.getType(call.getArguments().getFirst()), MATCHER.get(state), state
        );
    }

    private static boolean runsTestNg(VisitorState state) {
        return Optional.ofNullable(TEST_NG.get(state)).isPresent();
    }

    private Description message(Tree tree, String message) {
        return this.buildDescription(tree).setMessage(message).build();
    }
}
