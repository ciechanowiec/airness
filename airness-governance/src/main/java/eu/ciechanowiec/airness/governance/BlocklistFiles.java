package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * The files of a repository that name a container image or a JDK, which is what the repository half of
 * the blocklist reads.
 *
 * <p>Discovered from the working tree rather than listed, for the reason every other reader here gives:
 * an enumeration is correct only at the moment it is written, and a compose file added later would be a
 * compose file nothing reads. A Dockerfile is found by its name wherever it sits, a compose file by the
 * names compose itself looks for and the override and profile variants beside them, and a workflow or a
 * composite action by the directories GitHub reads them from.
 */
@UtilityClass
final class BlocklistFiles {

    /**
     * The version manager file that selects a JDK by vendor.
     */
    static final String SDKMANRC = ".sdkmanrc";
    /**
     * The core extensions file Maven loads before the build.
     */
    static final Path EXTENSIONS = Path.of(".mvn", "extensions.xml");

    private static final Pattern DOCKERFILE = Pattern.compile("^(?:Dockerfile(?:\\..+)?|.+\\.[Dd]ockerfile)$");
    private static final Pattern COMPOSE = Pattern.compile("^(?:docker-)?compose(?:\\.[\\w.-]+)?\\.ya?ml$");
    private static final Pattern YAML = Pattern.compile("^.+\\.ya?ml$");
    private static final Pattern ACTION = Pattern.compile("^action\\.ya?ml$");
    private static final Path WORKFLOWS = Path.of(".github", "workflows");
    private static final Path ACTIONS = Path.of(".github", "actions");
    private static final int WORKFLOW_DEPTH = 3;

    /**
     * Every Dockerfile the tree carries.
     *
     * @param root    the working tree root
     * @param tracked the committable files
     * @return the Dockerfiles, in the order git reports them
     */
    static List<Path> dockerfiles(Path root, Collection<Path> tracked) {
        return tracked.stream().filter(file -> DOCKERFILE.matcher(name(file)).matches()).toList();
    }

    /**
     * Every compose file the tree carries.
     *
     * @param root    the working tree root
     * @param tracked the committable files
     * @return the compose files, in the order git reports them
     */
    static List<Path> composeFiles(Path root, Collection<Path> tracked) {
        return tracked.stream().filter(file -> COMPOSE.matcher(name(file)).matches()).toList();
    }

    /**
     * Every GitHub Actions workflow and composite action the tree carries.
     *
     * @param root    the working tree root
     * @param tracked the committable files
     * @return the workflow files, in the order git reports them
     */
    static List<Path> workflows(Path root, Collection<Path> tracked) {
        return tracked.stream()
            .filter(file -> isWorkflow(root.relativize(file)) || isAction(root.relativize(file)))
            .toList();
    }

    private static boolean isWorkflow(Path relative) {
        return relative.startsWith(WORKFLOWS)
            && relative.getNameCount() == WORKFLOW_DEPTH
            && YAML.matcher(name(relative)).matches();
    }

    private static boolean isAction(Path relative) {
        return relative.startsWith(ACTIONS) && ACTION.matcher(name(relative)).matches();
    }

    private static String name(Path file) {
        return String.valueOf(file.getFileName());
    }
}
