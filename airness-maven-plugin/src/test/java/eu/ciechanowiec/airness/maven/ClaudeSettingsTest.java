package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Test
    @SneakyThrows
    void readsTheStringRulesOfOnePermissionList(@TempDir Path directory) {
        Path settings = Files.writeString(
            directory.resolve("settings.json"), """
                {
                  // The runtime accepts comments and trailing commas.
                  "permissions": {
                    "deny": ["Edit(src/payload-types.ts)", 7, "Edit(src/importMap.js)",],
                    "allow": ["Bash(mvn verify)"]
                  }
                }
                """
        );
        assertEquals(
            List.of("Edit(src/payload-types.ts)", "Edit(src/importMap.js)"),
            ClaudeSettings.permissions(settings, "deny"),
            "only string rules are rules, and comments and trailing commas are read as the runtime reads them"
        );
    }

    @Test
    @SneakyThrows
    void readsNoRuleFromAnAbsentOrUnreadableFile(@TempDir Path directory) {
        Path settings = directory.resolve("settings.json");
        assertEquals(List.of(), ClaudeSettings.permissions(settings, "deny"), "an absent file carries no rule");
        Files.writeString(settings, "{\"permissions\": {\"deny\": [\"Edit(a.ts)\"");
        assertEquals(List.of(), ClaudeSettings.permissions(settings, "deny"), "an unreadable file denies nothing");
        Files.writeString(settings, "{\"permissions\": {\"deny\": \"Edit(a.ts)\"}}");
        assertEquals(List.of(), ClaudeSettings.permissions(settings, "deny"), "a non-list holds no rule");
    }
}
