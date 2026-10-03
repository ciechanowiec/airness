package eu.ciechanowiec.airness.governance;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Mirrors PMD's comparison of a method or field name with its enclosing class names.
 *
 * <p>Methods match any enclosing class or interface exactly. Fields match the nearest one without
 * regard to case. Separate configured instances select METHOD_DEF and VARIABLE_DEF so each rule
 * retains its own suppression identifier. Constructors, records and enum type names are outside
 * the corresponding PMD predicates.
 */
public final class EnclosingTypeNameCheck extends AbstractCheck {

    @Override
    public int[] getDefaultTokens() {
        return new int[] {TokenTypes.METHOD_DEF, TokenTypes.VARIABLE_DEF};
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
    public void visitToken(DetailAST declaration) {
        boolean field = declaration.getType() == TokenTypes.VARIABLE_DEF;
        if (field && declaration.getParent().getType() != TokenTypes.OBJBLOCK) {
            return;
        }
        DetailAST identifier = Objects.requireNonNull(declaration.findFirstToken(TokenTypes.IDENT));
        if (namesType(declaration, identifier.getText())) {
            this.log(identifier, "declaration.name.matches.type");
        }
    }

    private static boolean namesType(DetailAST declaration, String value) {
        boolean field = declaration.getType() == TokenTypes.VARIABLE_DEF;
        Stream<DetailAST> enclosing = Stream.iterate(
            declaration.getParent(), Objects::nonNull, DetailAST::getParent
        ).filter(type -> type.getType() == TokenTypes.CLASS_DEF || type.getType() == TokenTypes.INTERFACE_DEF);
        return enclosing.limit(field ? 1 : Long.MAX_VALUE)
            .map(type -> Objects.requireNonNull(type.findFirstToken(TokenTypes.IDENT)).getText())
            .anyMatch(name -> field ? name.equalsIgnoreCase(value) : name.equals(value));
    }
}
