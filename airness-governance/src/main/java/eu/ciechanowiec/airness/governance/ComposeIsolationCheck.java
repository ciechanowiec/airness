package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/**
 * Every compose project can start beside another checkout of itself and beside other projects on one machine.
 *
 * <p>Verification already starts its services under names and on ports of the run's own. A compose file is what a
 * developer, or an agent working in a worktree of its own, starts by hand, so the same holds for it: no service
 * publishes on a host port the file fixes, and no service fixes its container name. A port is given as a variable
 * without a default, which the script that starts the project chooses, or left to Docker.
 */
public final class ComposeIsolationCheck {

    private static final String FIXED_PORTS
        = "Compose services published on a host port every checkout asks for (give the host port as a variable"
            + " without a default, such as ${APP_PORT}, that the script starting the project chooses, or leave it to"
            + " Docker, as in 127.0.0.1::8080)";
    private static final String CONTAINER_NAMES
        = "Compose services whose container name every checkout asks for (remove container_name, so compose names"
            + " the container after the project's directory)";

    private final Path root;
    private final List<Path> files;

    /**
     * Names the working tree whose compose projects are read.
     *
     * @param root the working tree root
     */
    public ComposeIsolationCheck(Path root) {
        this.root = root;
        this.files = new ContainerDefinitions(root).composeFiles();
    }

    /**
     * How many compose files the check read.
     *
     * @return the number of compose projects
     */
    public int scanned() {
        return this.files.size();
    }

    /**
     * The ports and names each compose file fixes.
     *
     * @return one verdict for the ports and one for the names
     */
    public List<Findings> findings() {
        return List.of(
            new Findings(FIXED_PORTS, this.offences(ComposeIsolation::fixedPorts)),
            new Findings(CONTAINER_NAMES, this.offences(ComposeIsolation::containerNames))
        );
    }

    /**
     * The verdicts over compose files given by their text, as a project that stores one under another name has it.
     *
     * @param name the name each offence starts with
     * @param yaml the text of the file
     * @return one verdict for the ports and one for the names
     */
    public static List<Findings> findings(String name, CharSequence yaml) {
        return List.of(
            new Findings(FIXED_PORTS, offences(name, ComposeIsolation.fixedPorts(yaml))),
            new Findings(CONTAINER_NAMES, offences(name, ComposeIsolation.containerNames(yaml)))
        );
    }

    private List<String> offences(Function<CharSequence, List<Located<String>>> rule) {
        return this.files.stream()
            .flatMap(file -> offences(this.root.relativize(file).toString(), rule.apply(read(file))).stream())
            .toList();
    }

    private static List<String> offences(String name, List<Located<String>> located) {
        return located.stream().map(offence -> name + ":" + offence.line() + ": " + offence.value()).toList();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + file, exception);
        }
    }
}
