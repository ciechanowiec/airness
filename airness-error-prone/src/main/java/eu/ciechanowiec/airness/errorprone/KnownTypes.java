package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.VisitorState;
import com.google.errorprone.util.ASTHelpers;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import java.util.Collection;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * Answers whether a type or a symbol belongs to a family named by its binary names or its packages.
 *
 * <p>A name the compilation cannot resolve answers no, so a check naming a library type stays inert in a
 * module that does not have that library.
 */
@UtilityClass
final class KnownTypes {

    /**
     * Whether a type is any of the named types or a subtype of one.
     *
     * @param type  the type to judge, absent for a tree the compiler could not attribute
     * @param names binary names of the family
     * @param state the visitor state that resolves the names
     * @return whether the type belongs to the family
     */
    static boolean isAnyOf(@Nullable Type type, Collection<String> names, VisitorState state) {
        return Optional.ofNullable(type).filter(
            judged -> names.stream()
                .map(name -> Optional.ofNullable(state.getTypeFromString(name)))
                .flatMap(Optional::stream)
                .anyMatch(target -> ASTHelpers.isSubtype(state.getTypes().erasure(judged), target, state))
        ).isPresent();
    }

    /**
     * Whether a class symbol is declared in one of the packages or beneath one.
     *
     * @param symbol   the class symbol
     * @param packages the package names
     * @return whether the symbol's package is one of them or nested under one
     */
    static boolean declaredIn(Symbol symbol, Collection<String> packages) {
        String declared = ASTHelpers.enclosingPackage(symbol)
            .map(enclosing -> enclosing.getQualifiedName().toString())
            .orElse("");
        return packages.stream().anyMatch(name -> (declared + '.').startsWith(name + '.'));
    }
}
