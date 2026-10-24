package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The container definitions and workflows of a working tree, found by the same rules everywhere they
 * are read.
 *
 * <p>The checks that lint these files and the check that holds their environment variables together have
 * to agree on what a Dockerfile, a compose project and a workflow are. Two readers with two rules would
 * leave a file one of them never reads. A Dockerfile is what the blocklist calls one.
 *
 * @param root the working tree root
 */
public record ContainerDefinitions(Path root) {

    // The names compose reads on its own, in the precedence it applies to them. An override file is left
    // out on purpose: compose merges it into the project its base file defines, so a directory is read
    // once, through that base, and an override validated alone would report a defect the project lacks.
    private static final List<String> COMPOSE_NAMES = List.of(
        "compose.yaml", "compose.yml", "docker-compose.yaml", "docker-compose.yml"
    );
    private static final Path WORKFLOWS = Path.of(".github", "workflows");

    /**
     * Every Dockerfile the tree carries.
     *
     * @return the Dockerfiles, ordered by path
     */
    public List<Path> dockerfiles() {
        return BlocklistFiles.dockerfiles(this.root, this.tracked()).stream().sorted().toList();
    }

    /**
     * The file that defines each compose project the tree carries, one per directory.
     *
     * <p>A directory is the unit compose validates: its base file and every override beside it form one
     * project, and the directory is where a developer's own {@code docker compose config} runs.
     *
     * @return one base compose file per directory, ordered by path
     */
    public List<Path> composeFiles() {
        Map<Path, Optional<Path>> byDirectory = this.tracked().stream()
            .filter(file -> COMPOSE_NAMES.contains(String.valueOf(file.getFileName())))
            .collect(
                Collectors.groupingBy(
                    Path::getParent,
                    Collectors.minBy(Comparator.comparingInt(file -> COMPOSE_NAMES.indexOf(name(file))))
                )
            );
        return byDirectory.values().stream().flatMap(Optional::stream).sorted().toList();
    }

    /**
     * Every GitHub Actions workflow the tree carries, leaving out composite actions.
     *
     * @return the workflows, ordered by path
     */
    public List<Path> workflows() {
        return BlocklistFiles.workflows(this.root, this.tracked()).stream()
            .filter(file -> this.root.relativize(file).startsWith(WORKFLOWS))
            .sorted()
            .toList();
    }

    private List<Path> tracked() {
        return Repository.trackedFiles(this.root);
    }

    private static String name(Path file) {
        return String.valueOf(file.getFileName());
    }
}
