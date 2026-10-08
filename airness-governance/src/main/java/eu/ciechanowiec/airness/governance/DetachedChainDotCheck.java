package eu.ciechanowiec.airness.governance;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import java.util.stream.IntStream;

/**
 * Keeps a chained dot beside a closing parenthesis that would otherwise occupy its own line.
 *
 * <p>Only Java dot tokens are visited, so examples inside comments and literals are not code. The
 * preceding nonblank line must contain only the closing parenthesis, and nothing but whitespace may
 * precede the dot on its line. A comment between the two therefore keeps its place, while any number
 * of empty lines still leaves the dot detached. Ordinary fluent calls split across lines stay valid.
 */
public final class DetachedChainDotCheck extends AbstractCheck {

    @Override
    public int[] getDefaultTokens() {
        return new int[] {TokenTypes.DOT};
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
    public void visitToken(DetailAST dot) {
        int line = dot.getLineNo() - 1;
        if (this.getLine(line).substring(0, dot.getColumnNo()).isBlank() && this.detached(line)) {
            this.log(dot, "chain.dot.detached");
        }
    }

    private boolean detached(int line) {
        return IntStream.iterate(line - 1, index -> index >= 0, index -> index - 1)
            .mapToObj(this::getLine)
            .dropWhile(String::isBlank)
            .findFirst()
            .filter(previous -> ")".equals(previous.strip()))
            .isPresent();
    }
}
