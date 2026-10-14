package eu.ciechanowiec.airness.governance;

import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Reads the counters a for loop introduces in its initialisation clause.
 */
@UtilityClass
final class LoopCounters {

    /**
     * Names the variables the loop declares in its initialisation clause.
     *
     * @param loop the for loop
     * @return the declared names
     */
    static Set<String> declared(DetailAST loop) {
        return initialisation(loop)
            .flatMap(LoopCounters::children)
            .filter(child -> child.getType() == TokenTypes.VARIABLE_DEF)
            .flatMap(variable -> Optional.ofNullable(variable.findFirstToken(TokenTypes.IDENT)).stream())
            .map(DetailAST::getText)
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Names the variables the loop declares or assigns in its initialisation clause.
     *
     * @param loop the for loop
     * @return the initialised names
     */
    static Set<String> initialised(DetailAST loop) {
        Stream<String> assigned = initialisation(loop)
            .map(clause -> clause.findFirstToken(TokenTypes.ELIST))
            .flatMap(list -> Optional.ofNullable(list).stream())
            .flatMap(LoopCounters::children)
            .map(DetailAST::getFirstChild)
            .filter(expression -> expression.getType() == TokenTypes.ASSIGN)
            .map(DetailAST::getFirstChild)
            .filter(target -> target.getType() == TokenTypes.IDENT)
            .map(DetailAST::getText);
        return Stream.concat(declared(loop).stream(), assigned).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Streams the direct children of a node in source order.
     *
     * @param parent the node
     * @return its children
     */
    static Stream<DetailAST> children(DetailAST parent) {
        return Stream.iterate(parent.getFirstChild(), Objects::nonNull, DetailAST::getNextSibling);
    }

    private static Stream<DetailAST> initialisation(DetailAST loop) {
        return Optional.ofNullable(loop.findFirstToken(TokenTypes.FOR_INIT)).stream();
    }
}
