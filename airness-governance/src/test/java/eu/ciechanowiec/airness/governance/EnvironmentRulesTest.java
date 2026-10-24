package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Verifies how environment variable names are read from each kind of file.
 */
class EnvironmentRulesTest {

    @Test
    void readsOnlyTheInterpolationsThatSupplyNoValueOfTheirOwn() {
        String compose = """
            services:
              db:
                environment:
                  PASSWORD: ${DB_PASSWORD}
                  PORT: ${DB_PORT:-5432}
                  USER: ${DB_USER-admin}
                  NAME: ${DB_NAME:?set the database name}
                  HOST: $DB_HOST
                  ESCAPED: $${LITERAL_ONLY_IN_SHELL}
                  AGAIN: ${DB_PASSWORD}
            """;
        assertEquals(
            Set.of("DB_PASSWORD"), EnvironmentRules.interpolated(compose),
            "a default, a required-with-message form, the unbraced form and an escaped dollar need nothing"
        );
    }

    @Test
    void readsTheAssignmentsAnExampleFileDocuments() {
        String example = """
            # DB_COMMENTED=no
            DB_PASSWORD=
            export DB_PORT=5432
              DB_USER = admin
            """;
        assertEquals(
            Set.of("DB_PASSWORD", "DB_PORT", "DB_USER"), EnvironmentRules.documented(example),
            "an exported or indented assignment documents a name and a comment does not"
        );
    }

    @Test
    void readsEveryWayAWorkflowSuppliesAName() {
        String workflow = """
            env:
              DB_PASSWORD: ${{ secrets.DATABASE_SECRET }}
            jobs:
              verify:
                steps:
                  - run: mvn verify
                    env:
                      TOKEN: ${{ vars.SHARED_TOKEN }}
                      lowercase: ignored
            """;
        assertEquals(
            Set.of("DATABASE_SECRET", "DB_PASSWORD", "SHARED_TOKEN", "TOKEN"), EnvironmentRules.supplied(workflow),
            "a capitalised key at any depth and a secret, variable or env reference all supply a name"
        );
    }

    @Test
    void recognisesAWorkflowThatRunsVerification() {
        assertTrue(EnvironmentRules.verifying("      - run: mvn --batch-mode verify\n"), "verify is verification");
        assertTrue(EnvironmentRules.verifying("      - run: ./mvnw install -Pextended\n"), "install includes verify");
        assertFalse(EnvironmentRules.verifying("      - run: mvn deploy:help\n      - run: echo verify\n"), "neither");
        assertFalse(EnvironmentRules.verifying("      - run: mvn clean package\n"), "package stops before verify");
        assertFalse(
            EnvironmentRules.verifying("      - run: mvn --batch-mode deploy -DskipTests\n"),
            "a run that skips the checks evaluates no compose file"
        );
        assertTrue(
            EnvironmentRules.verifying("      - run: |\n          mvn --batch-mode \\\n            clean verify\n"),
            "a command continued with a backslash is one command"
        );
        assertFalse(
            EnvironmentRules.verifying("      - run: |\n          mvn \\\n            install -Dmaven.test.skip\n"),
            "the skip is read on the continued line too"
        );
    }
}
