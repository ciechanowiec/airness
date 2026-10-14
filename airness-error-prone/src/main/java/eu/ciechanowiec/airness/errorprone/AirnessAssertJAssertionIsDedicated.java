package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Type;
import java.io.Serial;
import java.util.List;
import java.util.Optional;

/**
 * Asserts with the AssertJ method that names the property rather than asserting on a value derived from it.
 *
 * <p>{@code assertThat(text.isEmpty()).isTrue()} fails with "expected true", while
 * {@code assertThat(text).isEmpty()} fails with the text itself. Each entry names the type of the examined
 * value, the method derived from it, the assertion made on the result, and the dedicated assertion.
 */
@BugPattern(
    summary = "Use the dedicated AssertJ assertion rather than asserting on a value derived from the subject",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessAssertJAssertionIsDedicated extends BugChecker implements MethodInvocationTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> ASSERT_THAT = MethodMatchers.staticMethod()
        .onClass("org.assertj.core.api.Assertions").named("assertThat");

    private static final String STRING = "java.lang.String";
    private static final String FILE = "java.io.File";
    private static final String PATH = "java.nio.file.Path";
    private static final String COLLECTION = "java.util.Collection";
    private static final String MAP = "java.util.Map";
    private static final String OPTIONAL = "java.util.Optional";

    private static final String IS_TRUE = "isTrue";
    private static final String IS_FALSE = "isFalse";
    private static final String IS_EMPTY = "isEmpty";
    private static final String IS_EQUAL_TO = "isEqualTo";
    private static final String CONTAINS = "contains";

    private static final List<Chain> CHAINS = List.of(
        new Chain("java.math.BigDecimal", "compareTo", "isZero", "isEqualByComparingTo"),
        new Chain(STRING, IS_EMPTY, IS_TRUE, IS_EMPTY), new Chain(STRING, IS_EMPTY, IS_FALSE, "isNotEmpty"),
        new Chain(STRING, "equals", IS_TRUE, IS_EQUAL_TO),
        new Chain(STRING, "equalsIgnoreCase", IS_TRUE, "isEqualToIgnoringCase"),
        new Chain(STRING, CONTAINS, IS_TRUE, CONTAINS), new Chain(STRING, CONTAINS, IS_FALSE, "doesNotContain"),
        new Chain(STRING, "startsWith", IS_TRUE, "startsWith"),
        new Chain(STRING, "startsWith", IS_FALSE, "doesNotStartWith"),
        new Chain(STRING, "endsWith", IS_TRUE, "endsWith"), new Chain(STRING, "endsWith", IS_FALSE, "doesNotEndWith"),
        new Chain(STRING, "matches", IS_TRUE, "matches"), new Chain(STRING, "matches", IS_FALSE, "doesNotMatch"),
        new Chain(STRING, "trim", IS_EMPTY, "isBlank"), new Chain(STRING, "length", IS_EQUAL_TO, "hasSize"),
        new Chain(STRING, "length", "hasSize", "hasSameSizeAs"),
        new Chain(FILE, "length", "isZero", IS_EMPTY), new Chain(FILE, "length", IS_EQUAL_TO, "hasSize"),
        new Chain(FILE, "canRead", IS_TRUE, "canRead"), new Chain(FILE, "canWrite", IS_TRUE, "canWrite"),
        new Chain(FILE, "exists", IS_TRUE, "exists"), new Chain(FILE, "getName", IS_EQUAL_TO, "hasName"),
        new Chain(FILE, "getParent", IS_EQUAL_TO, "hasParent"),
        new Chain(FILE, "getParentFile", "isNull", "hasNoParent"),
        new Chain(FILE, "isAbsolute", IS_TRUE, "isAbsolute"), new Chain(FILE, "isAbsolute", IS_FALSE, "isRelative"),
        new Chain(FILE, "isDirectory", IS_TRUE, "isDirectory"), new Chain(FILE, "isFile", IS_TRUE, "isFile"),
        new Chain(FILE, "list", IS_EMPTY, "isEmptyDirectory"),
        new Chain(PATH, "startsWith", IS_TRUE, "startsWithRaw"), new Chain(PATH, "endsWith", IS_TRUE, "endsWithRaw"),
        new Chain(PATH, "getParent", IS_EQUAL_TO, "hasParentRaw"),
        new Chain(PATH, "getParent", "isNull", "hasNoParentRaw"),
        new Chain(PATH, "isAbsolute", IS_TRUE, "isAbsolute"), new Chain(PATH, "isAbsolute", IS_FALSE, "isRelative"),
        new Chain(COLLECTION, IS_EMPTY, IS_TRUE, IS_EMPTY),
        new Chain(COLLECTION, IS_EMPTY, IS_FALSE, "isNotEmpty"),
        new Chain(COLLECTION, "size", "isZero", IS_EMPTY), new Chain(COLLECTION, "size", IS_EQUAL_TO, "hasSize"),
        new Chain(COLLECTION, CONTAINS, IS_TRUE, CONTAINS),
        new Chain(COLLECTION, CONTAINS, IS_FALSE, "doesNotContain"),
        new Chain(COLLECTION, "containsAll", IS_TRUE, "containsAll"),
        new Chain(MAP, "size", IS_EQUAL_TO, "hasSize"), new Chain(MAP, "containsKey", IS_TRUE, "containsKey"),
        new Chain(MAP, "keySet", CONTAINS, "containsKey"), new Chain(MAP, "keySet", "containsOnly", "containsOnlyKeys"),
        new Chain(MAP, "containsValue", IS_TRUE, "containsValue"),
        new Chain(MAP, "values", CONTAINS, "containsValue"), new Chain(MAP, "get", IS_EQUAL_TO, "containsEntry"),
        new Chain(MAP, IS_EMPTY, IS_TRUE, IS_EMPTY), new Chain(MAP, IS_EMPTY, IS_FALSE, "isNotEmpty"),
        new Chain(OPTIONAL, "isPresent", IS_TRUE, "isPresent"), new Chain(OPTIONAL, IS_EMPTY, IS_TRUE, IS_EMPTY),
        new Chain(OPTIONAL, "isPresent", IS_FALSE, "isNotPresent"),
        new Chain(OPTIONAL, IS_EMPTY, IS_FALSE, "isNotEmpty"), new Chain(OPTIONAL, "get", IS_EQUAL_TO, CONTAINS),
        new Chain(OPTIONAL, "get", "isSameAs", "containsSame"),
        new Chain("java.util.Iterator", "hasNext", IS_TRUE, "hasNext"),
        new Chain("java.util.Iterator", "hasNext", IS_FALSE, "isExhausted"),
        new Chain("java.lang.Object", "toString", IS_EQUAL_TO, "hasToString"),
        new Chain("java.lang.Object", "equals", IS_TRUE, IS_EQUAL_TO)
    );

    @Override
    public Description matchMethodInvocation(MethodInvocationTree assertion, VisitorState state) {
        ExpressionTree subject = ASTHelpers.getReceiver(assertion);
        if (!(subject instanceof MethodInvocationTree start) || !ASSERT_THAT.matches(start, state)) {
            return Description.NO_MATCH;
        }
        String asserted = ASTHelpers.getSymbol(assertion).getSimpleName().toString();
        ExpressionTree examined = ASTHelpers.stripParentheses(start.getArguments().getFirst());
        return derived(examined, asserted, state)
            .or(() -> simplified(assertion, asserted, examined, state))
            .map(dedicated -> this.report(assertion, dedicated))
            .orElse(Description.NO_MATCH);
    }

    private Description report(MethodInvocationTree assertion, String dedicated) {
        return this.buildDescription(assertion).setMessage("Assert " + dedicated + " on the subject").build();
    }

    private static Optional<String> derived(ExpressionTree examined, String asserted, VisitorState state) {
        if (!(examined instanceof MethodInvocationTree derivation)) {
            return Optional.empty();
        }
        return Optional.ofNullable(ASTHelpers.getReceiver(derivation))
            .map(ASTHelpers::getType)
            .flatMap(owner -> chained(derivation, owner, asserted, state));
    }

    private static Optional<String> chained(
        MethodInvocationTree derivation, Type owner, String asserted, VisitorState state
    ) {
        String method = ASTHelpers.getSymbol(derivation).getSimpleName().toString();
        return CHAINS.stream()
            .filter(chain -> chain.method().equals(method) && chain.assertion().equals(asserted))
            .filter(chain -> ASTHelpers.isSubtype(owner, state.getTypeFromString(chain.type()), state))
            .map(Chain::dedicated)
            .findFirst();
    }

    private static Optional<String> simplified(
        MethodInvocationTree assertion, String asserted, ExpressionTree examined, VisitorState state
    ) {
        if (assertion.getArguments().size() != 1) {
            return Optional.empty();
        }
        ExpressionTree expected = assertion.getArguments().getFirst();
        boolean isEqualTo = IS_EQUAL_TO.equals(asserted);
        boolean stringSubject = ASTHelpers.isSameType(
            ASTHelpers.getType(examined), state.getSymtab().stringType, state
        );
        boolean emptyString = "".equals(ASTHelpers.constValue(expected, String.class));
        return Finding.first(
            new Finding(isEqualTo && expected.getKind() == Tree.Kind.NULL_LITERAL, "isNull()"),
            new Finding(isEqualTo && stringSubject && emptyString, "isEmpty()"),
            new Finding(
                "hasSize".equals(asserted) && Integer.valueOf(0).equals(ASTHelpers.constValue(expected)), "isEmpty()"
            )
        );
    }

    private record Chain(String type, String method, String assertion, String dedicated) {
    }
}
