package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.BinaryTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.Tree;
import java.io.Serial;
import java.util.List;
import java.util.Optional;
import java.util.function.IntPredicate;

/**
 * Says what a comparison of an {@code indexOf} result means instead of encoding it in a number.
 *
 * <p>An index above minus one, or at least zero, asks whether the element is present, which
 * {@code contains} answers. An index above zero excludes a match at the start, which is usually a mistake
 * for presence. A string index above a positive number skips a prefix the search could start after.
 */
@BugPattern(
    summary = "State what the indexOf comparison asks rather than comparing the index with a number",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessIndexOfComparisonIsDirect extends BugChecker implements BinaryTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> STRING_SEARCH = MethodMatchers.instanceMethod()
        .onExactClass("java.lang.String")
        .named("indexOf").withParameters("java.lang.String");

    private static final Matcher<ExpressionTree> LIST_SEARCH = MethodMatchers.instanceMethod()
        .onDescendantOf("java.util.List")
        .named("indexOf");

    private static final String PRESENCE
        = "Ask contains() whether the element is present rather than comparing its index";

    private static final List<Comparison> COMPARISONS = List.of(
        new Comparison(Tree.Kind.GREATER_THAN, bound -> bound == -1, false, PRESENCE),
        new Comparison(Tree.Kind.GREATER_THAN_EQUAL, bound -> bound == 0, false, PRESENCE),
        new Comparison(
            Tree.Kind.GREATER_THAN, bound -> bound == 0, false,
            "An index above zero misses a match at the start; compare with >= 0 or use contains()"
        ),
        new Comparison(
            Tree.Kind.GREATER_THAN, bound -> bound > 0, true,
            "Search from the position with indexOf(text, start) rather than discarding earlier matches"
        )
    );

    @Override
    public Description matchBinary(BinaryTree comparison, VisitorState state) {
        ExpressionTree search = comparison.getLeftOperand();
        boolean searchesText = STRING_SEARCH.matches(search, state);
        if (!searchesText && !LIST_SEARCH.matches(search, state)) {
            return Description.NO_MATCH;
        }
        return bound(comparison)
            .flatMap(bound -> message(comparison.getKind(), bound, searchesText))
            .map(message -> this.buildDescription(comparison).setMessage(message).build())
            .orElse(Description.NO_MATCH);
    }

    private static Optional<Integer> bound(BinaryTree comparison) {
        return Optional.ofNullable(ASTHelpers.constValue(comparison.getRightOperand(), Integer.class));
    }

    private static Optional<String> message(Tree.Kind kind, int bound, boolean searchesText) {
        return COMPARISONS.stream()
            .filter(comparison -> comparison.matches(kind, bound, searchesText))
            .map(Comparison::message)
            .findFirst();
    }

    private record Comparison(Tree.Kind kind, IntPredicate bound, boolean stringOnly, String message) {

        boolean matches(Tree.Kind actualKind, int actualBound, boolean searchesText) {
            boolean applies = searchesText || !this.stringOnly;
            return applies && this.kind == actualKind && this.bound.test(actualBound);
        }
    }
}
