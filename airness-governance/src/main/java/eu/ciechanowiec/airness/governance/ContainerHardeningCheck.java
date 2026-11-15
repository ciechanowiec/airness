package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/**
 * Every container the repository defines keeps the isolation a default container has, and no credential is
 * written into a container's definition.
 *
 * <p>A compose service gains no privilege, shares no namespace of the host, mounts no Docker socket and publishes
 * its ports on the host's loopback interface only, so nothing on the network reaches it. A credential a compose
 * file passes on comes from the environment that starts it, and a Dockerfile declares no credential as a build
 * argument or an environment variable, whose values the image keeps for anybody who pulls it. Every tracked compose
 * project and Dockerfile is read, the test fixtures' included.
 */
public final class ContainerHardeningCheck {

    private static final String LIFTED
        = "Compose services that lift the isolation of their container (remove privileged, cap_add, the unconfined"
            + " security_opt, the host namespace or the Docker socket mount)";
    private static final String EXPOSED
        = "Compose services that publish a port beyond the host's loopback interface (bind it to 127.0.0.1, as in"
            + " 127.0.0.1:${APP_PORT}:8080 or host_ip: 127.0.0.1)";
    private static final String VALUED
        = "Compose services whose credentials the file writes (pass each as ${NAME} without a default, which the"
            + " environment that starts the project supplies)";
    private static final String BAKED
        = "Dockerfiles that declare a credential as a build argument or an environment variable, which the image"
            + " keeps for anybody who pulls it (pass a build credential with RUN --mount=type=secret and a runtime one"
            + " when the container starts)";

    private final Path root;
    private final List<Path> composeFiles;
    private final List<Path> dockerfiles;

    /**
     * Names the working tree whose container definitions are read.
     *
     * @param root the working tree root
     */
    public ContainerHardeningCheck(Path root) {
        ContainerDefinitions definitions = new ContainerDefinitions(root);
        this.root = root;
        this.composeFiles = definitions.composeFiles();
        this.dockerfiles = definitions.dockerfiles();
    }

    /**
     * How many compose projects and Dockerfiles the check read, which a caller logs so the reach of a clean verdict
     * is on record.
     *
     * @return the counts, in words
     */
    public String scanned() {
        return this.composeFiles.size() + " compose project(s) and " + this.dockerfiles.size() + " Dockerfile(s)";
    }

    /**
     * What each compose project and Dockerfile gives up of its container's isolation or writes of a credential.
     *
     * @return one verdict for each rule
     */
    public List<Findings> findings() {
        return List.of(
            new Findings(LIFTED, offences(this.composeFiles, ComposeHardening::isolationLifts)),
            new Findings(EXPOSED, offences(this.composeFiles, ComposeHardening::exposedPorts)),
            new Findings(VALUED, offences(this.composeFiles, ComposeHardening::valuedCredentials)),
            new Findings(BAKED, offences(this.dockerfiles, DockerfileCredentials::declared))
        );
    }

    /**
     * The compose verdicts over a file given by its text, as a project that stores one under another name has it.
     *
     * @param name the name each offence starts with
     * @param yaml the text of the file
     * @return one verdict for each compose rule
     */
    static List<Findings> composeFindings(String name, String yaml) {
        return List.of(
            new Findings(LIFTED, offences(name, ComposeHardening.isolationLifts(yaml))),
            new Findings(EXPOSED, offences(name, ComposeHardening.exposedPorts(yaml))),
            new Findings(VALUED, offences(name, ComposeHardening.valuedCredentials(yaml)))
        );
    }

    /**
     * The Dockerfile verdict over a file given by its text.
     *
     * @param name       the name each offence starts with
     * @param dockerfile the text of the file
     * @return the verdict on the credentials it declares
     */
    static Findings dockerfileFindings(String name, String dockerfile) {
        return new Findings(BAKED, offences(name, DockerfileCredentials.declared(dockerfile)));
    }

    private List<String> offences(List<Path> files, Function<String, List<Located<String>>> rule) {
        return files.stream().flatMap(
            file -> offences(
                this.root.relativize(file).toString(), rule.apply(Repository.readText(file).orElse(""))
            ).stream()
        ).toList();
    }

    private static List<String> offences(String name, List<Located<String>> located) {
        return located.stream().map(offence -> name + ":" + offence.line() + ": " + offence.value()).toList();
    }
}
