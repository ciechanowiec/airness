package eu.ciechanowiec.airness.governance;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Keeps a parameter declaration off the final line of a multiline annotation group.
 *
 * <p>Annotations directly under the parameter modifiers are the group this check reads. An annotation
 * nested inside another annotation or structurally inside the parameter type is not one of them. This
 * is deliberately a syntactic rule: an annotation written in the modifier sequence counts even when
 * its declared targets also allow a type use.
 */
public final class ParameterAnnotationLocationCheck extends AbstractCheck {

    /**
     * The message reported when the declaration remains beside the final annotation.
     */
    public static final String MSG_LOCATION = "parameter.annotation.location";

    private static final int MULTIPLE_ANNOTATIONS = 2;

    @Override
    public int[] getDefaultTokens() {
        return new int[] {TokenTypes.PARAMETER_DEF};
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
    public void visitToken(DetailAST parameter) {
        DetailAST modifiers = parameter.findFirstToken(TokenTypes.MODIFIERS);
        if (modifiers.getChildCount(TokenTypes.ANNOTATION) < MULTIPLE_ANNOTATIONS) {
            return;
        }
        List<DetailAST> annotations = children(modifiers)
            .filter(child -> child.getType() == TokenTypes.ANNOTATION)
            .toList();
        DetailAST first = annotations.getFirst();
        DetailAST last = annotations.getLast();
        int finalAnnotationLine = last.getLastChild().getLineNo();
        DetailAST declaration = children(modifiers)
            .filter(child -> child.getType() != TokenTypes.ANNOTATION)
            .findFirst()
            .orElseGet(() -> parameter.findFirstToken(TokenTypes.TYPE));
        if (first.getLineNo() != finalAnnotationLine && declaration.getLineNo() <= finalAnnotationLine) {
            this.log(declaration, MSG_LOCATION);
        }
    }

    private static Stream<DetailAST> children(DetailAST parent) {
        return Stream.iterate(parent.getFirstChild(), Objects::nonNull, DetailAST::getNextSibling);
    }
}
