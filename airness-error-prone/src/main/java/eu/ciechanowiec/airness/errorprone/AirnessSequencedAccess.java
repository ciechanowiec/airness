package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import java.io.Serial;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reaches the ends of a sequenced collection through the methods that name them.
 *
 * <p>{@code get(0)}, {@code get(size() - 1)}, {@code remove(0)} and {@code add(0, element)} on a list,
 * {@code iterator().next()} on a list, {@code first()} and {@code last()} on a sorted set, and
 * {@code descendingSet()} on a navigable set each have a {@code SequencedCollection} counterpart.
 */
@BugPattern(
    summary = "Use the SequencedCollection method that names this end of the collection",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessSequencedAccess extends BugChecker implements MethodInvocationTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String LIST = "java.util.List";

    private static final Matcher<ExpressionTree> GET = MethodMatchers.instanceMethod().onDescendantOf(LIST)
        .named("get").withParameters("int");

    private static final Matcher<ExpressionTree> REMOVE_AT = MethodMatchers.instanceMethod().onDescendantOf(LIST)
        .named("remove").withParameters("int");

    private static final Matcher<ExpressionTree> ADD_AT = MethodMatchers.instanceMethod().onDescendantOf(LIST)
        .named("add").withParameters("int", "java.lang.Object");

    private static final Matcher<ExpressionTree> SIZE = MethodMatchers.instanceMethod().onDescendantOf(LIST)
        .named("size").withNoParameters();

    private static final Matcher<ExpressionTree> ITERATOR = MethodMatchers.instanceMethod().onDescendantOf(LIST)
        .named("iterator").withNoParameters();

    private static final Matcher<ExpressionTree> NEXT = MethodMatchers.instanceMethod()
        .onDescendantOf("java.util.Iterator").named("next").withNoParameters();

    private static final Matcher<ExpressionTree> SORTED_END = MethodMatchers.instanceMethod()
        .onDescendantOf("java.util.SortedSet").namedAnyOf("first", "last").withNoParameters();

    private static final Matcher<ExpressionTree> DESCENDING = MethodMatchers.instanceMethod()
        .onDescendantOf("java.util.NavigableSet").named("descendingSet").withNoParameters();

    @Override
    public Description matchMethodInvocation(MethodInvocationTree call, VisitorState state) {
        boolean named = Stream.of(SORTED_END, DESCENDING).anyMatch(matcher -> matcher.matches(call, state));
        boolean indexed = Stream.of(REMOVE_AT, ADD_AT, GET).anyMatch(matcher -> zeroAt(call, matcher, state));
        boolean sequenced = named || indexed || lastOf(call, state) || firstByIterator(call, state);
        return sequenced ? this.describeMatch(call) : Description.NO_MATCH;
    }

    private static boolean zeroAt(MethodInvocationTree call, Matcher<ExpressionTree> method, VisitorState state) {
        return method.matches(call, state)
            && Objects.equals(0, ASTHelpers.constValue(call.getArguments().getFirst(), Integer.class));
    }

    private static boolean firstByIterator(MethodInvocationTree call, VisitorState state) {
        return NEXT.matches(call, state) && Optional.ofNullable(ASTHelpers.getReceiver(call))
            .filter(receiver -> ITERATOR.matches(receiver, state)).isPresent();
    }

    private static boolean lastOf(MethodInvocationTree call, VisitorState state) {
        if (!GET.matches(call, state)) {
            return false;
        }
        List<? extends ExpressionTree> arguments = call.getArguments();
        return Optional.of(arguments.getFirst())
            .filter(BinaryTree.class::isInstance)
            .map(BinaryTree.class::cast)
            .filter(minus -> minus.getKind() == Tree.Kind.MINUS)
            .filter(minus -> Objects.equals(1, ASTHelpers.constValue(minus.getRightOperand(), Integer.class)))
            .filter(minus -> SIZE.matches(minus.getLeftOperand(), state))
            .filter(minus -> sameReceiver(call, (MethodInvocationTree) minus.getLeftOperand()))
            .isPresent();
    }

    private static boolean sameReceiver(MethodInvocationTree call, MethodInvocationTree size) {
        Optional<Symbol> receiver = receiverSymbol(call);
        return receiver.isPresent() && receiver.equals(receiverSymbol(size));
    }

    private static Optional<Symbol> receiverSymbol(MethodInvocationTree call) {
        return Optional.ofNullable(ASTHelpers.getReceiver(call)).map(ASTHelpers::getSymbol);
    }
}
