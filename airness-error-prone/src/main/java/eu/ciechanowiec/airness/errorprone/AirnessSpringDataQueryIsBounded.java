package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MemberReferenceTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.suppliers.Suppliers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import java.io.Serial;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.Modifier;
import org.jspecify.annotations.Nullable;

/**
 * Bounds every Spring Data query that returns rows by a page, a limit, or the size of its input.
 *
 * <p>The result set of an unbounded query is whatever the table holds. A query that answers instantly against a
 * seeded test database exhausts the heap against a real one, and nothing between the two says when that changed.
 * The rule is judged in two places, which never report the same method:
 *
 * <ul>
 * <li>A query method a repository declares, in any interface descending from Spring Data's
 * {@code Repository} or carrying {@code @RepositoryDefinition}, whatever its name, takes a
 * {@code Pageable}, a {@code Limit} or a {@code ScrollPosition} when it returns an {@link Iterable}, a
 * collection or a {@code Stream}. A bound named in the method rather than taken as a parameter, such as
 * {@code findTop10By}, is not accepted: a name carrying the bound carries the ordering with it, which is how
 * a derived name reaches the length a method name is held to, and the parameter states the same bound where
 * a reader of the call site can see and vary it.</li>
 * <li>A production call to a method Spring Data itself declares, such as {@code findAll(Specification)}
 * inherited from {@code JpaSpecificationExecutor}, is judged where it is written, since no declaration of
 * the application exists to read. It passes when the method takes a {@code Pageable}, a {@code Limit} or a
 * {@code ScrollPosition}, when it returns a {@code Page}, a {@code Slice} or a {@code Window}, or when it
 * takes an {@link Iterable} and so returns no more rows than it is given, as {@code findAllById} and
 * {@code saveAll} do. A fluent query's {@code all()} and {@code stream()} pass after {@code limit(...)}.</li>
 * </ul>
 *
 * <p>{@code Pageable.unpaged()} and {@code Limit.unlimited()} are reported in production code wherever they are
 * called, because each exists to take the bound away from a method that asks for one. Every exemption is a
 * constant of this check, so no configuration of a governed repository can widen it. Test sources are judged
 * only for their declarations, because a test reading back what it has just written reads a bounded table.
 */
@BugPattern(
    summary = "Bound the query that returns rows by a page, a limit, or the size of its input",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessSpringDataQueryIsBounded extends BugChecker
                                                   implements MethodTreeMatcher, MethodInvocationTreeMatcher,
                                                   MemberReferenceTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String TEST_SOURCES = "/src/test/";

    private static final String SPRING_DATA = "org.springframework.data.";

    private static final String DOMAIN = SPRING_DATA + "domain.";

    private static final String REPOSITORY = SPRING_DATA + "repository.Repository";

    private static final String DEFINITION = SPRING_DATA + "repository.RepositoryDefinition";

    private static final String FLUENT_QUERY = SPRING_DATA + "repository.query.FluentQuery";

    private static final List<String> QUERY_TYPES = List.of(
        REPOSITORY,
        FLUENT_QUERY,
        SPRING_DATA + "repository.query.QueryByExampleExecutor",
        SPRING_DATA + "querydsl.QuerydslPredicateExecutor",
        SPRING_DATA + "jpa.repository.JpaSpecificationExecutor"
    );

    private static final List<String> ROWS = List.of("java.lang.Iterable", "java.util.stream.Stream");

    private static final List<String> BOUNDED_RESULTS = List.of(DOMAIN + "Slice", DOMAIN + "Window");

    private static final List<String> BOUNDS = List.of(
        DOMAIN + "Pageable", DOMAIN + "Limit", DOMAIN + "ScrollPosition"
    );

    private static final Supplier<Type> INPUT = Suppliers.typeFromString("java.lang.Iterable");

    private static final Matcher<ExpressionTree> LIMIT = MethodMatchers.instanceMethod()
        .onDescendantOf(FLUENT_QUERY).named("limit");

    private static final Matcher<ExpressionTree> UNBOUND = Matchers.anyOf(
        MethodMatchers.staticMethod().onClass(DOMAIN + "Pageable").named("unpaged"),
        MethodMatchers.staticMethod().onClass(DOMAIN + "Limit").named("unlimited")
    );

    private static final String DECLARED = "A repository method returning a collection reads as many rows as the "
        + "table holds unless the caller states the bound. Take a Pageable, or a Limit where only the bound is "
        + "wanted, because a bound written into the method name cannot be seen or varied at the call site.";

    private static final String CALLED = "This call reads every row the query matches, because the Spring Data "
        + "method it calls takes no bound. Pass a Pageable or a Limit to a method that takes one, or count with "
        + "an aggregate query in place of reading the rows.";

    private static final String REMOVED = "Pageable.unpaged() and Limit.unlimited() remove the bound a query "
        + "states. Pass a Pageable or a Limit that states the bound, or count with an aggregate query in place of "
        + "reading the rows.";

    @Override
    public Description matchMethod(MethodTree method, VisitorState state) {
        Symbol.MethodSymbol symbol = ASTHelpers.getSymbol(method);
        boolean query = symbol.getModifiers().contains(Modifier.ABSTRACT) && isRepository(symbol.owner, state);
        return query && unbounded(symbol, state) ? this.report(method, DECLARED) : Description.NO_MATCH;
    }

    @Override
    public Description matchMethodInvocation(MethodInvocationTree call, VisitorState state) {
        return this.judgeCall(call, ASTHelpers.getSymbol(call), ASTHelpers.getReceiver(call), state);
    }

    @Override
    public Description matchMemberReference(MemberReferenceTree reference, VisitorState state) {
        return this.judgeCall(reference, ASTHelpers.getSymbol(reference), reference.getQualifierExpression(), state);
    }

    private Description judgeCall(
        ExpressionTree call, Symbol.MethodSymbol symbol, @Nullable ExpressionTree receiver, VisitorState state
    ) {
        String path = state.getPath().getCompilationUnit().getSourceFile().toUri().getPath();
        boolean production = !path.contains(TEST_SOURCES);
        if (production && UNBOUND.matches(call, state)) {
            return this.report(call, REMOVED);
        }
        boolean unboundedCall = production && isUnboundedCall(symbol, receiver, state);
        return unboundedCall ? this.report(call, CALLED) : Description.NO_MATCH;
    }

    private static boolean isUnboundedCall(
        Symbol.MethodSymbol symbol, @Nullable ExpressionTree receiver, VisitorState state
    ) {
        return declaredBySpringData(symbol, state) && unbounded(symbol, state) && !boundedByInput(symbol, state)
            && !limited(receiver, state);
    }

    private Description report(Tree tree, String message) {
        return this.buildDescription(tree).setMessage(message).build();
    }

    private static boolean isRepository(Symbol owner, VisitorState state) {
        return owner.isInterface()
            && (isAnyOf(owner.type, List.of(REPOSITORY), state) || ASTHelpers.hasAnnotation(owner, DEFINITION, state));
    }

    private static boolean declaredBySpringData(Symbol.MethodSymbol symbol, VisitorState state) {
        Symbol owner = symbol.owner;
        return owner.getQualifiedName().toString().startsWith(SPRING_DATA) && isAnyOf(owner.type, QUERY_TYPES, state);
    }

    private static boolean unbounded(Symbol.MethodSymbol symbol, VisitorState state) {
        Type result = symbol.getReturnType();
        return isAnyOf(result, ROWS, state) && !isAnyOf(result, BOUNDED_RESULTS, state)
            && symbol.getParameters().stream().noneMatch(parameter -> isAnyOf(parameter.type, BOUNDS, state));
    }

    /*
     * Only a parameter declared as an Iterable itself counts as the input that bounds the result, as it is on
     * findAllById and saveAll. A Sort is an Iterable of its orders, and bounds nothing.
     */
    private static boolean boundedByInput(Symbol.MethodSymbol symbol, VisitorState state) {
        return Optional.ofNullable(INPUT.get(state)).filter(input -> takes(symbol, input, state)).isPresent();
    }

    private static boolean takes(Symbol.MethodSymbol symbol, Type input, VisitorState state) {
        return symbol.getParameters().stream()
            .anyMatch(parameter -> ASTHelpers.isSameType(parameter.type, input, state));
    }

    private static boolean limited(@Nullable ExpressionTree receiver, VisitorState state) {
        return receiver instanceof MethodInvocationTree invocation
            && (LIMIT.matches(invocation, state) || limited(ASTHelpers.getReceiver(invocation), state));
    }

    private static boolean isAnyOf(Type type, List<String> names, VisitorState state) {
        return names.stream()
            .map(name -> Optional.ofNullable(state.getTypeFromString(name)))
            .flatMap(Optional::stream)
            .anyMatch(target -> ASTHelpers.isSubtype(type, target, state));
    }
}
