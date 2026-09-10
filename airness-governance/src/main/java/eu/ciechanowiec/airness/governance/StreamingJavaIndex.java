package eu.ciechanowiec.airness.governance;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * Indexes named production types and deliberately bounded MVC callback declarations.
 */
@UtilityClass
public final class StreamingJavaIndex {

    private static final String CALLBACK = "configureAsyncSupport";
    private static final Set<Tree.Kind> STATEMENTS = Set.of(
        Tree.Kind.VARIABLE, Tree.Kind.EXPRESSION_STATEMENT,
        Tree.Kind.EMPTY_STATEMENT
    );
    private static final Set<String> CALLS = Set.of(
        "setDefaultTimeout", "setTaskExecutor",
        "registerCallableInterceptors", "registerDeferredResultInterceptors"
    );

    /**
     * Records source fingerprints and permitted runtime origins for every named type.
     *
     * @param source   the production Java file
     * @param location the repository-relative diagnostic location
     * @param origins  the production output directory and optional reactor artifact
     * @return the source and type entries
     */
    @SneakyThrows
    public static List<StreamingInput> read(Path source, String location, Collection<String> origins) {
        String content = Files.readString(source);
        CompilationUnitTree unit = StreamingJavaSource.read(source, content);
        StreamingInput file = new StreamingInput(
            "source", location, source.toUri().toString(),
            StreamingTimeoutInputs.fingerprint(content)
        );
        String namespace = Optional.ofNullable(unit.getPackageName()).map(Object::toString).orElse("");
        String prefix = namespace.isEmpty() ? "" : namespace + '.';
        return Stream.concat(
            Stream.of(file), unit.getTypeDecls().stream().filter(ClassTree.class::isInstance)
                .map(ClassTree.class::cast).flatMap(type -> types(type, prefix, origins))
        ).toList();
    }

    private static Stream<StreamingInput> types(ClassTree type, String prefix, Collection<String> origins) {
        String name = prefix + type.getSimpleName();
        Stream<StreamingInput> declared = origins.stream().map(
            origin -> new StreamingInput("type", name, origin, classification(type))
        );
        Stream<StreamingInput> nested = type.getMembers().stream().filter(ClassTree.class::isInstance)
            .map(ClassTree.class::cast).flatMap(member -> types(member, name + '$', origins));
        return Stream.concat(declared, nested);
    }

    private static String classification(ClassTree type) {
        List<MethodTree> callbacks = type.getMembers().stream().filter(MethodTree.class::isInstance)
            .map(MethodTree.class::cast).filter(method -> method.getName().contentEquals(CALLBACK)).toList();
        if (callbacks.isEmpty()) {
            return "none";
        }
        return callbacks.size() == 1 ? callback(callbacks.getFirst()) : "unsupported";
    }

    private static String callback(MethodTree method) {
        Optional<BlockTree> body = Optional.ofNullable(method.getBody());
        if (method.getParameters().size() != 1 || body.isEmpty()) {
            return "unsupported";
        }
        return body(body.orElseThrow(), method.getParameters().getFirst().getName().toString());
    }

    private static String body(BlockTree body, String parameter) {
        boolean straight = body.getStatements().stream().allMatch(
            statement -> STATEMENTS.contains(statement.getKind())
        );
        boolean safe = body.getStatements().stream().allMatch(statement -> supported(statement, parameter));
        if (!straight || !safe) {
            return "unsupported";
        }
        boolean timeout = body.getStatements().stream().filter(ExpressionStatementTree.class::isInstance)
            .map(ExpressionStatementTree.class::cast).map(ExpressionStatementTree::getExpression)
            .filter(MethodInvocationTree.class::isInstance).map(MethodInvocationTree.class::cast)
            .anyMatch(call -> chain(call, parameter) && chooses(call));
        return timeout ? "timeout" : "none";
    }

    private static boolean supported(StatementTree statement, String parameter) {
        boolean call = statement instanceof ExpressionStatementTree expression
            && expression.getExpression() instanceof MethodInvocationTree invoked && chain(invoked, parameter);
        return !mentions(statement, parameter) || call;
    }

    private static boolean chain(MethodInvocationTree call, String parameter) {
        if (
            !(call.getMethodSelect() instanceof MemberSelectTree member) || !CALLS.contains(
                member.getIdentifier().toString()
            )
        ) {
            return false;
        }
        boolean arguments = call.getArguments().stream().noneMatch(argument -> mentions(argument, parameter));
        return arguments && receiver(member.getExpression(), parameter);
    }

    private static boolean receiver(ExpressionTree expression, String parameter) {
        if (expression instanceof IdentifierTree identifier) {
            return identifier.getName().contentEquals(parameter);
        }
        return expression instanceof MethodInvocationTree call && chain(call, parameter);
    }

    private static boolean chooses(MethodInvocationTree call) {
        MemberSelectTree member = (MemberSelectTree) call.getMethodSelect();
        boolean earlier = member.getExpression() instanceof MethodInvocationTree previous && chooses(previous);
        return member.getIdentifier().contentEquals("setDefaultTimeout") || earlier;
    }

    private static boolean mentions(Tree tree, String parameter) {
        return Optional.ofNullable(new ParameterReferences().scan(tree, parameter)).orElse(Boolean.FALSE);
    }

    private static final class ParameterReferences extends TreeScanner<@Nullable Boolean, String> {

        @Override
        public Boolean visitIdentifier(IdentifierTree identifier, String parameter) {
            return identifier.getName().contentEquals(parameter);
        }

        @Override
        public Boolean reduce(@Nullable Boolean left, @Nullable Boolean right) {
            return Optional.ofNullable(left).orElse(Boolean.FALSE)
                || Optional.ofNullable(right).orElse(Boolean.FALSE);
        }
    }
}
