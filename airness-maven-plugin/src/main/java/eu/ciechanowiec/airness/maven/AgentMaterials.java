package eu.ciechanowiec.airness.maven;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * The agent-facing prose carried by the assets artifact on the Maven plugin's classpath.
 */
final class AgentMaterials {

    private static final String INSTRUCTIONS = "airness/agent/instructions.md";

    private final Optional<URL> material;

    /**
     * Uses one classloader so the instructions and the plugin that enforces them stay at one version.
     *
     * @param classes classloader carrying {@code airness-assets}
     */
    AgentMaterials(ClassLoader classes) {
        this(Optional.ofNullable(classes.getResource(INSTRUCTIONS)));
    }

    AgentMaterials(Optional<URL> material) {
        this.material = material;
    }

    /**
     * The exact managed section required at the start of {@code AGENTS.md}.
     *
     * @return canonical section, including its final newline
     */
    String instructions() {
        try (
            InputStream stream = this.material
                .map(AgentMaterials::open)
                .orElseThrow(() -> new IllegalStateException("Agent material is missing: " + INSTRUCTIONS))
        ) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read shipped agent material " + INSTRUCTIONS, exception);
        }
    }

    private static InputStream open(URL material) {
        try {
            return material.openStream();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not open shipped agent material " + INSTRUCTIONS, exception);
        }
    }
}
