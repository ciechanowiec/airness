package eu.ciechanowiec.airness.maven;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * The agent-facing prose carried by the assets artifact on the Maven plugin's classpath.
 */
final class AgentMaterials {

    private static final String INSTRUCTIONS = "airness/agent/instructions.md";

    private final ClassLoader classes;

    /**
     * Uses one classloader so the instructions and the plugin that enforces them stay at one version.
     *
     * @param classes classloader carrying {@code airness-assets}
     */
    AgentMaterials(ClassLoader classes) {
        this.classes = classes;
    }

    /**
     * The exact managed section required at the start of {@code AGENTS.md}.
     *
     * @return canonical section, including its final newline
     */
    String instructions() {
        try (
            InputStream stream = Optional.ofNullable(this.classes.getResourceAsStream(INSTRUCTIONS))
                .orElseThrow(() -> new IllegalStateException("Agent material is missing: " + INSTRUCTIONS))
        ) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read shipped agent material " + INSTRUCTIONS, exception);
        }
    }
}
