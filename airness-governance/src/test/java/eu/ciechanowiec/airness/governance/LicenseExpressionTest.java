package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * An npm license expression is written out as the alternatives the license policy judges.
 */
class LicenseExpressionTest {

    @Test
    void readsOneIdentifierAsOneAlternative() {
        assertEquals(List.of("MIT"), LicenseExpression.alternatives(" MIT "));
        assertEquals(List.of(), LicenseExpression.alternatives(""), "nothing declared offers no alternative");
    }

    @Test
    void readsEachDisjunctAsAnAlternative() {
        assertEquals(List.of("MIT", "Apache-2.0"), LicenseExpression.alternatives("(MIT OR Apache-2.0)"));
    }

    @Test
    void distributesAConjunctionOverADisjunction() {
        assertEquals(
            List.of("MIT AND BSD-3-Clause", "ISC AND BSD-3-Clause"),
            LicenseExpression.alternatives("(MIT OR ISC) AND BSD-3-Clause"),
            "AND binds tighter than OR, and a group is one operand"
        );
        assertEquals(
            List.of("MIT", "ISC AND Zlib"), LicenseExpression.alternatives("MIT OR ISC AND Zlib")
        );
    }

    @Test
    void keepsAnExceptionWithItsLicense() {
        assertEquals(
            List.of("GPL-2.0-only WITH Classpath-exception-2.0"),
            LicenseExpression.alternatives("GPL-2.0-only WITH Classpath-exception-2.0")
        );
    }

    @Test
    void keepsADeclarationThatIsNoExpressionWhole() {
        assertEquals(List.of("SEE LICENSE IN LICENSE.md"), LicenseExpression.alternatives("SEE LICENSE IN LICENSE.md"));
        assertEquals(List.of("(MIT OR"), LicenseExpression.alternatives("(MIT OR"));
        assertEquals(List.of("MIT AND"), LicenseExpression.alternatives("MIT AND"));
        assertEquals(List.of("MIT WITH ("), LicenseExpression.alternatives("MIT WITH ("));
        assertEquals(List.of("(MIT))"), LicenseExpression.alternatives("(MIT))"));
    }

    @Test
    void writesEachIdentifierTheWayThePolicySpellsIt() {
        assertEquals(
            List.of("MIT", "APACHE-2.0"),
            LicenseExpression.alternatives("mit OR Apache-2.0", text -> text.toUpperCase(Locale.ROOT))
        );
    }

    @Test
    void judgesALicenseWithAKnownExceptionAsTheLicense() {
        assertEquals(
            List.of("Apache-2.0"),
            LicenseExpression.alternatives("Apache-2.0 WITH LLVM-exception", text -> text, "LLVM-exception"::equals)
        );
        assertEquals(
            List.of("Apache-2.0 WITH Other-exception"),
            LicenseExpression.alternatives("Apache-2.0 WITH Other-exception", text -> text, "LLVM-exception"::equals)
        );
    }
}
