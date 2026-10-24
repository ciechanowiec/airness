package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies that every generated file is denied to the agent runtime and stays denied locally.
 */
class GeneratedDenialTest {

    private static final List<String> GENERATED = List.of("src/payload-types.ts", "web/src/importMap.js");

    @Test
    void namesEveryGeneratedFileWithoutAnEditDenial() {
        GeneratedDenial denial = new GeneratedDenial(
            GENERATED, List.of("Edit(src/payload-types.ts)", "Write(web/src/importMap.js)"), List.of()
        );
        assertEquals(
            List.of("Edit(web/src/importMap.js)"), Verdicts.offences(denial.findings(), "may still edit"),
            "a Write rule never matches a file path, so only the Edit form counts as a denial"
        );
    }

    @Test
    void namesALocalAllowThatGivesADenialBack() {
        GeneratedDenial denial = new GeneratedDenial(
            GENERATED,
            List.of("Edit(src/payload-types.ts)", "Edit(web/src/importMap.js)"),
            List.of("Edit(src/payload-types.ts)", "Bash(mvn verify)")
        );
        assertEquals(
            List.of("Edit(src/payload-types.ts)"),
            Verdicts.offences(denial.findings(), "local agent settings give back"),
            "the local allow undoes the denial, and an unrelated allow is the project's own"
        );
    }

    @Test
    void passesWhenNothingIsGenerated() {
        assertTrue(
            Verdicts.clean(new GeneratedDenial(List.of(), List.of(), List.of("Edit(anything)")).findings()),
            "with no generated file there is nothing to deny"
        );
    }
}
