package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Airness runs its own CI on the hosted Ubuntu release it requires of every consumer.
 *
 * <p>The goal runs over this repository in the build of {@code airness-parent}, which a build narrowed with
 * {@code -pl} or stopped before the parents never reaches. This gives the same verdict beside the check, when its
 * module's tests run, so a workflow moved back to an alias or to an earlier release is named by the first build
 * that compiles the rule rather than the last.
 */
class SelfRunnerImagesTest {

    @Test
    void selectsEveryHostedUbuntuRunnerByTheRequiredRelease() {
        RunnerImageCheck check = new RunnerImageCheck(SelfModules.repository());
        assertAll(
            () -> assertTrue(check.scanned() > 0, "a check that read no workflow proves nothing about this repository"),
            () -> assertEquals(
                List.of(),
                check.findings().stream().filter(verdict -> !verdict.clean()).map(Findings::report).toList()
            )
        );
    }
}
