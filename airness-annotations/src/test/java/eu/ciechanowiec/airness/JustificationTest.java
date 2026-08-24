package eu.ciechanowiec.airness;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;

/**
 * The two declarations that decide what this artifact costs a consuming project, read back off the
 * annotation itself.
 *
 * <p>The retention is the load-bearing one. {@link Justification} is the only Airness artifact that
 * reaches a consumer's compile classpath, and it is declared at provided scope on the strength of the
 * retention being {@link RetentionPolicy#SOURCE}: the compiler discards it, so it reaches neither the
 * consumer's own artifact nor the dependency tree that artifact's consumers resolve. A retention
 * widened to CLASS or RUNTIME would keep compiling and would quietly make that claim false, which is
 * the kind of change no other check in this build would notice.
 */
class JustificationTest {

    private static final ElementType[] DECLARED_TARGETS = {
        ElementType.TYPE, ElementType.METHOD, ElementType.FIELD,
        ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.MODULE
    };

    @Test
    void keepsTheJustificationOutOfTheBytecodeItDocuments() {
        Retention retention = Justification.class.getAnnotation(Retention.class);
        assertEquals(
            RetentionPolicy.SOURCE, retention.value(),
            "provided scope is honest only while the compiler discards this annotation"
        );
    }

    @Test
    void reachesEveryDeclarationThatCanCarryASuppression() {
        Target target = Justification.class.getAnnotation(Target.class);
        assertArrayEquals(
            DECLARED_TARGETS, target.value(),
            "a suppression and its justification sit on one declaration, so both must be legal there"
        );
    }
}
