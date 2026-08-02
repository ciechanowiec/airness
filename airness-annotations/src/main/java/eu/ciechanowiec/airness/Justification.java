package eu.ciechanowiec.airness;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * States why an accompanying {@link SuppressWarnings} is correct. A suppression records that a rule was
 * consciously set aside. Without the reason, a later reader cannot tell a considered exception from a
 * silenced defect, and the only safe move is to leave it alone forever. Pairing the two keeps the
 * reason in the code rather than in somebody's memory, and the {@code SuppressionNeedsJustification}
 * rule in the shipped PMD configuration fails the build when it is missing.
 *
 * <p>The two annotations must sit on the same declaration. The rule reads them as siblings, so a
 * justification placed on the enclosing class does not cover a suppression on a method inside it.
 *
 * <p>The retention is {@link RetentionPolicy#SOURCE}: the justification is addressed to whoever reads
 * or changes the code, so it has no business in the bytecode. The compiler discards it, which is why
 * this artifact is declared at {@code provided} scope and costs a consuming project nothing at all,
 * neither in its own artifact nor in the dependency tree its own consumers resolve.
 */
@Documented
@Retention(
    RetentionPolicy.SOURCE
)
@Target(
    {
        ElementType.TYPE, ElementType.METHOD, ElementType.FIELD,
        ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.MODULE
    }
)
public @interface Justification {

    /**
     * Why the accompanying suppression is correct.
     *
     * @return the reason the suppressed rule does not apply here
     */
    @Justification(
        "an annotation attribute has no caller by design: this one is read by whoever changes the code"
    )
    @SuppressWarnings(
        "UnusedReturnValue"
    )
    String value();
}
