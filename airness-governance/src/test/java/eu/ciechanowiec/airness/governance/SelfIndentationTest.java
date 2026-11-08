package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Airness indents its own tracked tree as the {@code .editorconfig} it ships declares.
 *
 * <p>The goal runs over this repository in the build of {@code airness-parent}, and holds the tree to the
 * exemptions the file linter's configuration declares by role. This reads the same tree with no exemption at
 * all, which is the stronger claim: no file of this repository needs its role to pass, so a check added for
 * every consumer is one this repository already answers to without help.
 */
class SelfIndentationTest {

    @Test
    void indentsEveryTrackedFileAsItsEditorconfigDeclares() {
        IndentationCheck check = new IndentationCheck(SelfModules.repository(), List.of(), _ -> false);
        assertAll(
            () -> assertTrue(check.scanned() > 0, "a check that read nothing proves nothing about this repository"),
            () -> assertEquals(
                List.of(),
                check.findings().stream().filter(verdict -> !verdict.clean()).map(Findings::report).toList()
            )
        );
    }
}
