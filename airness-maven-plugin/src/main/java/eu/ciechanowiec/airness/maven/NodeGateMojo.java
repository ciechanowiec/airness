package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.maven.NodeProjects.NodeProject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
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
            int exit = tools.pnpm(bound.arguments(result), project.directory(), log, this.environment());
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

    // The parents' versions, which wiring compares, and the browser cache of the Playwright version Airness pins,
    // which the e2e gate drives: the same Chromium the report renderer uses, installed by report-browser-install.
    private Map<String, String> environment() {
        Map<String, String> environment = new HashMap<>(ReportBrowser.environment());
        environment.put(HarnessVersions.VARIABLE, HarnessVersions.of(this.project()).value());
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
