package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies how the permission rules of an agent runtime settings file are read.
 */
class ClaudeSettingsTest {

    private static final String DENY = "deny";

    @Test
    @SneakyThrows
    void readsTheStringRulesOfOnePermissionList(@TempDir Path directory) {
        Path settings = Files.writeString(
            directory.resolve("settings.json"), """
                {
                  // The runtime accepts comments and trailing commas.
                  "permissions": {
                    "deny": ["Edit(src/generated-types.ts)", 7, "Edit(src/importMap.js)",],
                    "allow": ["Bash(mvn verify)"]
                  }
                }
                """
        );
        assertEquals(
            List.of("Edit(src/generated-types.ts)", "Edit(src/importMap.js)"),
            ClaudeSettings.permissions(settings, DENY),
            "only string rules are rules, and comments and trailing commas are read as the runtime reads them"
        );
    }

    @Test
    @SneakyThrows
    void readsNoRuleFromAnAbsentOrUnreadableFile(@TempDir Path directory) {
        Path settings = directory.resolve("settings.json");
        assertEquals(List.of(), ClaudeSettings.permissions(settings, DENY), "an absent file carries no rule");
        Files.writeString(settings, "{\"permissions\": {\"deny\": [\"Edit(a.ts)\"");
        assertEquals(List.of(), ClaudeSettings.permissions(settings, DENY), "an unreadable file denies nothing");
        Files.writeString(settings, "{\"permissions\": {\"deny\": \"Edit(a.ts)\"}}");
        assertEquals(List.of(), ClaudeSettings.permissions(settings, DENY), "a non-list holds no rule");
    }

    @Test
    @SneakyThrows
    void addsTheMissingRulesAndKeepsEverythingElse(@TempDir Path directory) {
        Path settings = directory.resolve(".claude/settings.json");
        assertTrue(ClaudeSettings.add(settings, DENY, List.of("Edit(.editorconfig)")), "an absent file is created");
        assertEquals(
            """
                {
                  "permissions": {
                    "deny": [
                      "Edit(.editorconfig)"
                    ]
                  }
                }
                """,
            Files.readString(settings), "written as the file is ordinarily written, one rule a line"
        );
        Files.writeString(
            settings, "{\"model\": \"opus\", \"permissions\": {\"deny\": [\"Bash(rm)\"], \"allow\": [\"Read\"]}}"
        );
        assertTrue(ClaudeSettings.add(settings, DENY, List.of("Edit(.editorconfig)", "Bash(rm)")));
        assertEquals(List.of("Bash(rm)", "Edit(.editorconfig)"), ClaudeSettings.permissions(settings, DENY));
        assertEquals(List.of("Read"), ClaudeSettings.permissions(settings, "allow"));
        assertTrue(Files.readString(settings).contains("opus"), "a key the project owns stays");
        assertFalse(ClaudeSettings.add(settings, DENY, List.of("Edit(.editorconfig)")), "nothing missing, no write");
        Files.writeString(settings, "{\"permissions\": []}");
        assertTrue(ClaudeSettings.add(settings, DENY, List.of("Edit(a)")), "a list replaces what was no object");
        Files.writeString(settings, "[");
        assertThrows(IllegalStateException.class, () -> ClaudeSettings.add(settings, DENY, List.of("Edit(a)")));
    }
}
