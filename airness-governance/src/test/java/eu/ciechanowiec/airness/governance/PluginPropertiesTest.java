package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PluginPropertiesTest {

    @Test
    void readsRowsAndPassesOverCommentsAndBlankLines() {
        assertEquals(
            List.of(
                new PluginProperty("maven-surefire-plugin", "argLine", PluginProperties.Use.PROJECT),
                new PluginProperty("jacoco-maven-plugin", "jacoco.skip", PluginProperties.Use.RESERVED)
            ),
            PluginProperties.parse(
                """
                    # a comment

                    maven-surefire-plugin\targLine\tPROJECT
                    jacoco-maven-plugin\tjacoco.skip\tRESERVED
                    """
            )
        );
    }

    @Test
    void refusesALineItCannotAccountFor() {
        IllegalStateException columns = assertThrows(
            IllegalStateException.class, () -> PluginProperties.parse("maven-surefire-plugin argLine PROJECT\n")
        );
        IllegalStateException owner = assertThrows(
            IllegalStateException.class, () -> PluginProperties.parse("maven-surefire-plugin\targLine\tOPEN\n")
        );
        assertAll(
            () -> assertEquals("plugin-properties.tsv line 1 does not hold three columns", columns.getMessage()),
            () -> assertEquals("plugin-properties.tsv line 1 names no owner", owner.getMessage())
        );
    }

    @Test
    void reservesWhatTheShippedTableReserves() {
        assertAll(
            () -> assertTrue(PluginProperties.reserves("maven.test.failure.ignore")),
            () -> assertTrue(PluginProperties.reserves("spotbugs.onlyAnalyze")),
            () -> assertFalse(PluginProperties.reserves("argLine")),
            () -> assertFalse(PluginProperties.rows().isEmpty())
        );
    }
}
