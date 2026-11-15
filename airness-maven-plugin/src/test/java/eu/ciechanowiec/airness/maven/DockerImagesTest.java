package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * An image's packages are listed by the pinned scanner from a saved copy on its standard input, in a container with
 * no network and no mount, and a tag is pinned to the digest its registry names today.
 */
class DockerImagesTest {

    private static final String SCANNER = "aquasec/trivy:0.76.2@sha256:" + "a".repeat(64);
    private static final String BASE = "node:26.8.2-alpine@sha256:" + "b".repeat(64);
    private static final String DIGEST = "sha256:" + "c".repeat(64);
    private static final String LISTED = "{\"bomFormat\": \"CycloneDX\", \"components\": []}";

    @Test
    void listsAnImageThisMachineHoldsWithoutPullingIt() {
        Recording docker = new Recording(true, command -> command.contains("run") ? LISTED : "");
        new DockerImages(SCANNER, docker).packages(BASE);
        List<String> run = docker.commands().getLast();
        assertAll(
            () -> assertEquals(List.of("docker image inspect " + BASE), docker.asked()),
            () -> assertEquals("docker save --output", String.join(" ", docker.commands().getFirst().subList(0, 3))),
            () -> assertEquals(BASE, docker.commands().getFirst().getLast()),
            () -> assertTrue(
                run.containsAll(List.of("--network", "none", "--cap-drop=ALL", "--interactive", SCANNER)), run
                    .toString()
            ),
            () -> assertTrue(run.getLast().contains("--format cyclonedx --skip-files '**/*.jar'"), run.getLast()),
            () -> assertTrue(
                run.stream().noneMatch(argument -> argument.startsWith("--mount") || "-v".equals(argument))
            ),
            () -> assertTrue(
                docker.input().endsWith("image.tar"),
                "the saved image reaches the scanner on its input"
            ),
            () -> assertFalse(
                Files.exists(Path.of(docker.input())),
                "the saved image is removed once listed"
            )
        );
    }

    @Test
    void pullsAnImageThisMachineDoesNotHold() {
        Recording docker = new Recording(false, command -> command.contains("run") ? LISTED : "");
        new DockerImages(SCANNER, docker).packages(BASE);
        assertEquals(List.of("docker", "pull", "--quiet", BASE), docker.commands().getFirst());
    }

    @Test
    void refusesAListThatIsNoCycloneDxDocument() {
        Recording docker = new Recording(
            true, command -> command.contains("run") ? "{\"spdxVersion\": \"SPDX-2.3\"}" : ""
        );
        assertThrows(IllegalStateException.class, () -> new DockerImages(SCANNER, docker).packages(BASE));
    }

    @Test
    void pinsATagToTheDigestItsRegistryNamesToday() {
        Recording docker = new Recording(
            true, command -> command.contains("inspect") ? "[\"node@" + DIGEST + "\"]\n" : ""
        );
        assertAll(
            () -> assertEquals(
                "node:26.8.2-alpine@" + DIGEST, new DockerImages(SCANNER, docker).current("node:26.8.2-alpine")
            ),
            () -> assertEquals(List.of("docker", "pull", "--quiet", "node:26.8.2-alpine"), docker.commands().getFirst())
        );
    }

    @Test
    void failsForATagDockerRecordsNoDigestFor() {
        Recording docker = new Recording(true, command -> command.contains("inspect") ? "[]" : "");
        assertThrows(IllegalStateException.class, () -> new DockerImages(SCANNER, docker).current("kit:1.0.0"));
    }

    /**
     * Docker as a recording: whether an image is held, and what each command prints.
     */
    private static final class Recording implements HostDocker {

        private final boolean held;
        private final Function<List<String>, String> printed;
        private final List<List<String>> commands;
        private final List<String> asked;
        private final AtomicReference<String> input;

        Recording(boolean held, Function<List<String>, String> printed) {
            this.held = held;
            this.printed = printed;
            this.commands = new ArrayList<>();
            this.asked = new ArrayList<>();
            this.input = new AtomicReference<>("");
        }

        List<List<String>> commands() {
            return List.copyOf(this.commands);
        }

        List<String> asked() {
            return List.copyOf(this.asked);
        }

        String input() {
            return Objects.requireNonNull(this.input.get());
        }

        @Override
        public String run(List<String> command, Optional<Path> input) {
            this.commands.add(command);
            input.ifPresent(file -> this.input.set(file.toString()));
            return this.printed.apply(command);
        }

        @Override
        public boolean succeeds(List<String> command) {
            this.asked.add(String.join(" ", command));
            return this.held;
        }
    }
}
