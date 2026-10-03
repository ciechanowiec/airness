package eu.ciechanowiec.airness.governance;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Reports the multiline condition shape also checked by PMD's PairedConditionParentheses.
 *
 * <p>The start of the statement and the start of its body are the boundaries the PMD predicate
 * measures. Keeping those same boundaries matters when a brace is written below the closing
 * parenthesis. Expression tokens supply the first and last lines, including nested parentheses
 * and text blocks. Their operator's line alone does not locate the whole expression.
 */
public final class PairedConditionCheck extends AbstractCheck {

    @Override
    public int[] getDefaultTokens() {
        return new int[] {TokenTypes.LITERAL_IF, TokenTypes.LITERAL_WHILE};
    }

    @Override
    public int[] getAcceptableTokens() {
        return this.getDefaultTokens();
    }

    @Override
    public int[] getRequiredTokens() {
        return new int[] {};
    }

    @Override
    public void visitToken(DetailAST statement) {
        DetailAST expression = Objects.requireNonNull(statement.findFirstToken(TokenTypes.EXPR));
        DetailAST closing = Objects.requireNonNull(statement.findFirstToken(TokenTypes.RPAREN));
        DetailAST body = Objects.requireNonNull(closing.getNextSibling());
        int first = tokens(expression).mapToInt(DetailAST::getLineNo).min().orElseThrow();
        int last = tokens(expression).mapToInt(DetailAST::getLineNo).max().orElseThrow();
        int opening = statement.getLineNo();
        int ending = body.getLineNo();
        boolean multiline = opening != first || first != last || last != ending;
        if (multiline && (opening == first || last == ending)) {
            this.log(statement, "condition.parentheses.paired");
        }
    }

    private static Stream<DetailAST> tokens(DetailAST root) {
        Stream<DetailAST> children = Stream.iterate(
            root.getFirstChild(), Objects::nonNull, DetailAST::getNextSibling
        );
        return Stream.concat(Stream.of(root), children.flatMap(PairedConditionCheck::tokens));
    }
}
