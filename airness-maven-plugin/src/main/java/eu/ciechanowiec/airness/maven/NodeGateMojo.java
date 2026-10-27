package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.maven.NodeProjects.NodeProject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

/**
 * Runs one JS/TS gate in every declared node project and turns the result file it writes into findings.
 *
 * <p>Every node gate, whichever npm package ships it, runs through this goal with one execution per gate. The
 * command runs inside each node project as {@code pnpm exec <command> --result <file>} on the harness's Node
 * and pnpm, and the result file, under {@code target/airness/node/<project>/<gate>.json}, is the only
 * contract between Maven and the gate. The rule logic stays in the gate.
 */
@Mojo(name = "node-gate", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public final class NodeGateMojo extends AbstractGovernanceMojo {

    private static final String BROWSER_GATE = "e2e";

    /**
     * The gate id, which the result file must repeat. Only an execution an Airness parent binds names it: no
     * property sets it, so no command line can point the goal at another gate or another command. It is not
     * required of Maven, so a command-line run reaches the refusal that names the repair.
     */
    @Parameter
    private @Nullable String gate;

    /**
     * The command that runs the gate inside a node project through {@code pnpm exec}, before
     * {@code --result <file>}, named by the same bound execution.
     */
    @Parameter
    private @Nullable String command;

    /**
     * The module's node project directories, comma-separated and relative to the module.
     */
    @Parameter(property = "airness.node.projects")
    private @Nullable String declared;

    /**
     * The execution being run, which tells a command-line run from a bound one.
     */
    @Parameter(defaultValue = "${mojoExecution}", readonly = true, required = true)
    private @Nullable MojoExecution execution;

    @Override
    boolean applies() {
        return !RepositoryProjects.harnessParent(this.project()) && !this.projects().none();
    }

    @Override
    List<Findings> findings() {
        Optional<NodeGate> named = this.fromCommandLine() ? Optional.empty() : this.boundGate();
        if (named.isEmpty()) {
            return List.of(
                new Findings(
                    "Node gate from the command line: an execution an Airness parent binds",
                    List.of(
                        "airness:node-gate runs only in the node-gate executions the Airness parents bind, and "
                            + this.executionId() + " is none of them; run mvn verify, or one bound execution by its"
                            + " id, such as airness:node-gate@node-gate-types (the Extended gates with -Pextended)"
                    )
                )
            );
        }
        NodeGate bound = named.orElseThrow();
        try {
            NodeTools tools = AbstractNodeMojo.install(this.session(), this.getLog());
            return this.projects().projects().stream().map(project -> this.run(bound, project, tools)).toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not install the pinned Node and pnpm", exception);
        }
    }

    private Findings run(NodeGate bound, NodeProject project, NodeTools tools) {
        if (project.outside() || !project.hasManifest()) {
            return new Findings(
                "Node gate " + bound.id() + " in " + project.key() + ": a node project to run in",
                List.of(project.key() + " is no node project inside the module, so the gate cannot run there")
            );
        }
        Path build = Path.of(this.project().getBuild().getDirectory());
        Path results = project.results(build);
        Path result = bound.result(results);
        Path log = bound.log(project.logs(build));
        try {
            Files.createDirectories(results);
            clear(result);
            int exit = runner(bound, tools).pnpm(
                bound.arguments(result), project.directory(), log, this.gateEnvironment(bound, log)
            );
            NodeGateResult read = NodeGateResult.read(bound.id(), project.key(), result, exit);
            if (!read.findings().clean()) {
                AbstractNodeMojo.replay(log, this.getLog());
            }
            this.getLog().info(read.line());
            return read.findings();
        } catch (IOException exception) {
            throw new UncheckedIOException(
                "Could not run the node gate " + bound.id() + " in " + project.key(), exception
            );
        }
    }

    private static NodeTools runner(NodeGate gate, NodeTools tools) {
        return BROWSER_GATE.equals(gate.id())
            ? new NodeTools(tools.node(), tools.pnpm(), Duration.ofHours(6)) : tools;
    }

    private Map<String, String> gateEnvironment(NodeGate gate, Path log) {
        Map<String, String> environment = new HashMap<>(this.environment());
        if (BROWSER_GATE.equals(gate.id())) {
            environment.put(NodeTools.PROGRESS_LOG, log.toString());
        }
        return Map.copyOf(environment);
    }

    /**
     * The variables every gate runs with: the parents' versions and the module's own version and identity, which
     * wiring compares, and the verification mode. The report browser cache remains available to tools that
     * render reports. The node browser suite connects to its independently pinned container image.
     *
     * @param module the module the gate runs in
     * @return the variables
     */
    static Map<String, String> environment(MavenProject module) {
        Map<String, String> environment = new HashMap<>(ReportBrowser.environment());
        environment.put(HarnessVersions.VARIABLE, HarnessVersions.of(module).value());
        environment.put(HarnessVersions.MODULE_VARIABLE, module.getVersion());
        environment.put(HarnessVersions.IDENTITY_VARIABLE, module.getGroupId() + ':' + module.getArtifactId());
        environment.put("AIRNESS_VERIFICATION_MODE", ReportProjects.active(module, "delta") ? "delta" : "full");
        return Map.copyOf(environment);
    }

    private Map<String, String> environment() {
        Map<String, String> environment = new HashMap<>(environment(this.project()));
        environment.put("AIRNESS_REPOSITORY_ROOT", this.repositoryRoot().toString());
        environment.put(
            "AIRNESS_NODE_ROOTS", this.session().getAllProjects().stream()
                .flatMap(project -> NpmEvidence.directories(project).stream())
                .map(Path::toString).distinct().collect(Collectors.joining(File.pathSeparator))
        );
        return Map.copyOf(environment);
    }

    // The previous result and every evidence file it listed, so a gate cannot pass on what an earlier run left.
    private static void clear(Path result) throws IOException {
        for (Path stale : NodeGateResult.previousEvidence(result)) {
            Files.deleteIfExists(stale);
        }
        Files.deleteIfExists(result);
    }

    // Maven names the execution of a goal given on the command line default-cli.
    private boolean fromCommandLine() {
        return "default-cli".equals(this.executionId());
    }

    // The gate and the command a bound execution names. A goal run by the id of no bound execution names neither.
    private Optional<NodeGate> boundGate() {
        return Optional.ofNullable(this.gate)
            .flatMap(id -> Optional.ofNullable(this.command).map(line -> NodeGate.of(id, line)));
    }

    private String executionId() {
        return Objects.requireNonNull(this.execution, "Maven did not inject the mojo execution").getExecutionId();
    }

    private NodeProjects projects() {
        return NodeProjects.of(this.project().getBasedir().toPath(), this.declared);
    }
}
