package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.IdentifierTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.MemberSelectTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import java.io.Serial;
import java.util.Optional;
import java.util.Set;

/**
 * Reaches an empty collection through the factory that carries its element type.
 *
 * <p>{@code Collections.EMPTY_LIST}, {@code EMPTY_SET} and {@code EMPTY_MAP} are raw, so every use is an
 * unchecked conversion that {@code emptyList()}, {@code emptySet()} and {@code emptyMap()} avoid. A use
 * through a qualified name and one through a static import are both reported, while the import declaration is never
 * read.
 */
@BugPattern(
    summary = "Use Collections.emptyList(), emptySet() or emptyMap() rather than the raw constant",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessEmptyCollectionIsTyped extends BugChecker
                                                 implements MemberSelectTreeMatcher, IdentifierTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Set<String> RAW = Set.of("EMPTY_LIST", "EMPTY_SET", "EMPTY_MAP");

    @Override
    public Description matchMemberSelect(MemberSelectTree select, VisitorState state) {
        return this.match(select);
    }

    @Override
    public Description matchIdentifier(IdentifierTree identifier, VisitorState state) {
        return this.match(identifier);
    }

    private Description match(Tree reference) {
        boolean raw = Optional.ofNullable(ASTHelpers.getSymbol(reference))
            .filter(symbol -> RAW.contains(symbol.getSimpleName().toString()))
            .filter(symbol -> symbol.owner.getQualifiedName().contentEquals("java.util.Collections"))
            .isPresent();
        return raw ? this.describeMatch(reference) : Description.NO_MATCH;
    }
}
