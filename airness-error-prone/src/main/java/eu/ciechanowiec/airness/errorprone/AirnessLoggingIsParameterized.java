package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.IfTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.VariableTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;
import java.io.Serial;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;

/**
 * Hands a logger a constant message with placeholders and lets it decide what to evaluate.
 *
 * <p>A message assembled by concatenation, {@link String#format(String, Object...)} or {@code MessageFormat} is built
 * even when
 * the level is off and defeats the logger's grouping of equal messages. An argument is passed as it is, not
 * as {@code toString()} or as an explicit array, and an expensive one at a fine level is guarded. A guard
 * that matches its statement's level only around cheap arguments says nothing. A logger is a private field
 * named for the class declaring it.
 */
@BugPattern(
    summary = "Pass the logger a constant message with placeholders and leave evaluation to it",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessLoggingIsParameterized extends BugChecker
                                                 implements MethodInvocationTreeMatcher, VariableTreeMatcher,
                                                 IfTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String SIMPLE_LOGGING = "org.slf4j.Logger";

    private static final Set<String> LEVELS = Set.of("trace", "debug", "info", "warn", "error");

    private static final Set<String> GUARDED_LEVELS = Set.of("trace", "debug", "info");

    private static final Matcher<ExpressionTree> LOG = MethodMatchers.instanceMethod()
        .onDescendantOf(SIMPLE_LOGGING).namedAnyOf(LEVELS);

    private static final Matcher<ExpressionTree> ENABLED = MethodMatchers.instanceMethod()
        .onDescendantOf(SIMPLE_LOGGING).namedAnyOf("isTraceEnabled", "isDebugEnabled", "isInfoEnabled");

    private static final Matcher<ExpressionTree> ASSEMBLED = Matchers.anyOf(
        MethodMatchers.staticMethod().onClass("java.lang.String").named("format"),
        MethodMatchers.staticMethod().onClass("java.text.MessageFormat").named("format")
    );

    private static final Matcher<ExpressionTree> GET_LOGGER = MethodMatchers.staticMethod()
        .onClass("org.slf4j.LoggerFactory").named("getLogger");

    private static final Matcher<ExpressionTree> JBOSS_LEVEL_ARGUMENT = MethodMatchers.instanceMethod()
        .onDescendantOf("org.jboss.logging.BasicLogger").namedAnyOf("log", "logv", "logf");

    private static final Set<String> LOGGER_TYPES = Set.of(
        SIMPLE_LOGGING, "java.util.logging.Logger", "org.apache.logging.log4j.Logger", "org.apache.log4j.Logger",
        "org.jboss.logging.Logger"
    );

    @Override
    public Description matchMethodInvocation(MethodInvocationTree call, VisitorState state) {
        boolean levelArgument = JBOSS_LEVEL_ARGUMENT.matches(call, state) && levelArgument(call);
        boolean logs = LOG.matches(call, state) && !call.getArguments().isEmpty();
        Optional<String> problem = levelArgument
            ? Optional.of("Call the logger method named for the level rather than passing the level")
            : Optional.of(call).filter(_ -> logs).flatMap(statement -> statementProblem(statement, state));
        return problem.map(message -> this.message(call, message)).orElse(Description.NO_MATCH);
    }

    private static Optional<String> statementProblem(MethodInvocationTree call, VisitorState state) {
        List<? extends ExpressionTree> arguments = call.getArguments();
        ExpressionTree message = ASTHelpers.stripParentheses(arguments.getFirst());
        List<? extends ExpressionTree> values = arguments.subList(1, arguments.size());
        return Finding.first(
            new Finding(
                concatenated(message), "Pass a constant message with {} placeholders rather than concatenating"
            ),
            new Finding(
                ASSEMBLED.matches(message, state),
                "Pass a constant message with {} placeholders rather than formatting it"
            ),
            new Finding(
                values.stream().anyMatch(NewArrayTree.class::isInstance),
                "Pass the arguments themselves rather than an array of them"
            ),
            new Finding(
                values.stream().anyMatch(AirnessLoggingIsParameterized::callsToString),
                "Pass the argument itself; the logger calls toString only when it writes"
            ),
            new Finding(
                unguardedExpense(call, values, state),
                "Guard the statement with the level check, since an argument is computed"
            )
        );
    }

    private static boolean concatenated(ExpressionTree message) {
        return message instanceof BinaryTree concatenation
            && concatenation.getKind() == Tree.Kind.PLUS
            && Optional.ofNullable(ASTHelpers.constValue(concatenation)).isEmpty();
    }

    @Override
    public Description matchVariable(VariableTree field, VisitorState state) {
        Symbol.VarSymbol symbol = ASTHelpers.getSymbol(field);
        boolean logger = symbol.getKind() == ElementKind.FIELD
            && LOGGER_TYPES.contains(symbol.asType().tsym.getQualifiedName().toString());
        return Finding.first(
            new Finding(logger && !symbol.getModifiers().contains(Modifier.PRIVATE), "Declare the logger private"),
            new Finding(
                logger && namesAnotherClass(field, symbol, state), "Name the logger for the class that declares it"
            )
        ).map(message -> this.message(field, message)).orElse(Description.NO_MATCH);
    }

    @Override
    public Description matchIf(IfTree guard, VisitorState state) {
        ExpressionTree condition = ASTHelpers.stripParentheses(guard.getCondition());
        boolean levelGuard = ENABLED.matches(condition, state) && Optional.ofNullable(guard.getElseStatement())
            .isEmpty();
        List<MethodInvocationTree> statements = levelGuard ? logStatements(guard.getThenStatement(), state) : List.of();
        if (statements.isEmpty()) {
            return Description.NO_MATCH;
        }
        String level = Optional.ofNullable(ASTHelpers.getSymbol(condition))
            .map(guardCall -> guardCall.getSimpleName().toString()).orElse("")
            .replace("is", "").replace("Enabled", "").toLowerCase(Locale.ROOT);
        boolean mismatched = statements.stream()
            .anyMatch(statement -> !ASTHelpers.getSymbol(statement).getSimpleName().contentEquals(level));
        boolean cheap = statements.stream().flatMap(statement -> statement.getArguments().stream())
            .noneMatch(MethodInvocationTree.class::isInstance);
        return Finding.first(
            new Finding(mismatched, "Guard the statement with the check for the level it logs at"),
            new Finding(cheap, "Placeholders already defer the work; drop the level guard")
        ).map(message -> this.message(guard, message)).orElse(Description.NO_MATCH);
    }

    private static boolean unguardedExpense(
        MethodInvocationTree call, List<? extends ExpressionTree> values, VisitorState state
    ) {
        boolean fine = GUARDED_LEVELS.contains(ASTHelpers.getSymbol(call).getSimpleName().toString());
        boolean expensive = values.stream().anyMatch(MethodInvocationTree.class::isInstance);
        boolean guarded = Optional.ofNullable(state.findEnclosing(IfTree.class))
            .map(guard -> ENABLED.matches(ASTHelpers.stripParentheses(guard.getCondition()), state))
            .orElse(false);
        return fine && expensive && !guarded;
    }

    private static List<MethodInvocationTree> logStatements(StatementTree body, VisitorState state) {
        List<? extends StatementTree> statements = body instanceof BlockTree block ? block.getStatements()
            : List.of(body);
        List<MethodInvocationTree> calls = statements.stream()
            .filter(ExpressionStatementTree.class::isInstance)
            .map(statement -> ((ExpressionStatementTree) statement).getExpression())
            .filter(expression -> LOG.matches(expression, state))
            .map(MethodInvocationTree.class::cast)
            .toList();
        return calls.size() == statements.size() ? calls : List.of();
    }

    private static boolean callsToString(ExpressionTree value) {
        return value instanceof MethodInvocationTree call
            && call.getArguments().isEmpty()
            && call.getMethodSelect() instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals("toString");
    }

    private static boolean levelArgument(MethodInvocationTree call) {
        return call.getArguments().stream()
            .findFirst()
            .map(ASTHelpers::getSymbol)
            .filter(level -> level.getKind() == ElementKind.ENUM_CONSTANT)
            .filter(level -> level.owner.getQualifiedName().contentEquals("org.jboss.logging.Logger.Level"))
            .isPresent();
    }

    private static boolean namesAnotherClass(VariableTree field, Symbol.VarSymbol symbol, VisitorState state) {
        return Optional.ofNullable(field.getInitializer())
            .filter(initializer -> GET_LOGGER.matches(initializer, state))
            .map(initializer -> ((MethodInvocationTree) initializer).getArguments().getFirst())
            .filter(AirnessLoggingIsParameterized::classLiteral)
            .map(argument -> ASTHelpers.getSymbol(((MemberSelectTree) argument).getExpression()))
            .filter(named -> !named.equals(ASTHelpers.enclosingClass(symbol)))
            .isPresent();
    }

    private static boolean classLiteral(ExpressionTree argument) {
        return argument instanceof MemberSelectTree select && select.getIdentifier().contentEquals("class");
    }

    private Description message(Tree tree, String message) {
        return this.buildDescription(tree).setMessage(message).build();
    }
}
