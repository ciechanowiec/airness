package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.maven.NodeProjects.NodeProject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
     * The gate id, which the result file must repeat. A bound execution names it in its configuration, and
     * the property serves only a gate run from the command line, which adds a gate: an id a bound execution
     * owns is refused there.
     */
    @Parameter(property = "airness.node.gate", required = true)
    private @Nullable String gate;

    /**
     * The command that runs the gate inside a node project through {@code pnpm exec}, before
     * {@code --result <file>}.
     */
    @Parameter(property = "airness.node.command", required = true)
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
        NodeGate bound = NodeGate.of(
            Objects.requireNonNull(this.gate, "Maven did not inject the gate"),
            Objects.requireNonNull(this.command, "Maven did not inject the command")
        );
        boolean owned = NodeGateOwnership.bound(this.project().getBuildPlugins()).contains(bound.id());
        if (this.fromCommandLine() && owned) {
            return List.of(
                new Findings(
                    "Node gate " + bound.id() + " from the command line: a gate id no bound execution owns",
                    List.of(
                        "a bound execution owns the gate " + bound.id() + ", so a command-line run would rewrite "
                            + "its result; run mvn verify, or name another gate id"
                    )
                )
            );
        }
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
            int exit = tools.pnpm(
                bound.arguments(result), project.directory(), log,
                Map.of(HarnessVersions.VARIABLE, HarnessVersions.of(this.project()).value())
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

    // The previous result and every evidence file it listed, so a gate cannot pass on what an earlier run left.
    private static void clear(Path result) throws IOException {
        for (Path stale : NodeGateResult.previousEvidence(result)) {
            Files.deleteIfExists(stale);
        }
        Files.deleteIfExists(result);
    }

    // Maven names the execution of a goal given on the command line default-cli.
    private boolean fromCommandLine() {
        return "default-cli".equals(
            Objects.requireNonNull(this.execution, "Maven did not inject the mojo execution").getExecutionId()
        );
    }

    private NodeProjects projects() {
        return NodeProjects.of(this.project().getBasedir().toPath(), this.declared);
    }
}
