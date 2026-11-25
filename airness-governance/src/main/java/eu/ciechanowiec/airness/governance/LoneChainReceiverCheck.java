package eu.ciechanowiec.airness.governance;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Keeps the first call of a chain on the line of its receiver when the two fit on one line.
 *
 * <p>A receiver is a bare name, qualified or not, such as {@code http} or {@code this.client}, that ends
 * its line, alone or after {@code return}, {@code yield}, an assignment, an arrow, an opening parenthesis
 * or a comma. A dot that starts the following nonblank line, with the rest of that line, is joined to it
 * whenever the joined line stays within the line length, so a short name never stands alone above the
 * call that uses it. A receiver whose first call does not fit beside it keeps its own line, a call or a
 * literal ending the line is no bare receiver, and a comment between the two keeps them apart. Only Java
 * dot tokens are visited, so examples inside comments and literals are not code.
 */
public final class LoneChainReceiverCheck extends AbstractCheck {

    private static final String NAME = "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*";
    private static final Pattern RECEIVER = Pattern.compile(
        "(?:.*?(?:\\breturn|\\byield|[=(,]|->)\\s*)?" + NAME + "(?:\\." + NAME + ")*"
    );
    // The line length the Airness configuration holds every Java line to.
    private static final int MAX = 120;

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
        String text = this.getLine(line);
        if (text.substring(0, dot.getColumnNo()).isBlank() && this.joinable(line, text.strip().length())) {
            this.log(dot, "chain.receiver.lone", MAX);
        }
    }

    private boolean joinable(int line, int call) {
        return IntStream.iterate(line - 1, index -> index >= 0, index -> index - 1)
            .mapToObj(this::getLine)
            .dropWhile(String::isBlank)
            .findFirst()
            .filter(previous -> RECEIVER.matcher(previous.strip()).matches())
            .filter(previous -> previous.stripTrailing().length() + call <= MAX)
            .isPresent();
    }
}
