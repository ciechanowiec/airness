package eu.ciechanowiec.airness.maven;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginContainer;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.Profile;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

/**
 * Holds module completion last, so closing judges a module only after every execution it has.
 *
 * <p>Maven orders the executions of one phase by plugin and then by declaration, a child parent's after its
 * parent's and a profile's after the build's, so no binding position can be trusted on its own to stay last.
 * This reads the order Maven actually planned instead. Nothing may run after {@code module-complete} in
 * {@code verify}, and no goal named on the command line may run after it either, because whatever ran there
 * would come after the closing comparison and the report. A project binds nothing in {@code verify} at all:
 * its own steps belong in an earlier phase, such as {@code post-integration-test}. Only signing may follow,
 * because it reads the archives the verdict covers and writes nothing but their signatures.
 *
 * @param plan the executions Maven planned for one module, in the order it runs them
 */
record CompletionOrder(List<Step> plan) {

    static final String GOAL = "module-complete";
    private static final String VERIFY = "verify";
    private static final String AIRNESS_PLUGIN = "eu.ciechanowiec:airness-maven-plugin";
    private static final String MAVEN_PLUGINS = "org.apache.maven.plugins";
    private static final String SIGNER = "org.apache.maven.plugins:maven-gpg-plugin";
    private static final String REPAIR = "; bind it to a phase before verify, such as post-integration-test";

    /**
     * Copies the plan, so it cannot change after it was read.
     *
     * @param plan the planned executions
     */
    CompletionOrder {
        plan = List.copyOf(plan);
    }

    /**
     * Reads the order from the executions Maven planned.
     *
     * @param executions the planned executions
     * @return the order
     */
    static CompletionOrder of(List<MojoExecution> executions) {
        return new CompletionOrder(executions.stream().map(Step::of).toList());
    }

    /**
     * Whether the plan reaches module completion, which only a run through {@code verify} does.
     *
     * @return whether any planned execution completes the module
     */
    boolean completes() {
        return this.completion().isPresent();
    }

    /**
     * Everything that would run after the module completed: an execution in {@code verify} or a goal named on
     * the command line. Later phases, such as {@code install}, act on a module the verdict already covers.
     *
     * @return one problem per late execution, empty when completion is last or absent
     */
    List<String> late() {
        int completion = this.completion().orElse(this.plan.size());
        return IntStream.range(completion + 1, this.plan.size())
            .mapToObj(this.plan::get)
            .filter(step -> Optional.ofNullable(step.phase()).map(VERIFY::equals).orElse(true))
            .filter(step -> !step.signs())
            .map(CompletionOrder::lateProblem).toList();
    }

    // A goal named on the command line can move before the lifecycle or into its own invocation. A bound one
    // moves to an earlier phase.
    private static String lateProblem(Step step) {
        String late = step.label() + " runs after airness:" + GOAL + ", so after the closing step judged the build";
        return Optional.ofNullable(step.phase())
            .map(_ -> late + REPAIR)
            .orElseGet(
                () -> late + "; name it before the lifecycle (mvn " + step.goalPrefix() + " verify), or run it in an"
                    + " invocation of its own after the build (mvn verify, then mvn " + step.goalPrefix() + ')'
            );
    }

    /**
     * The {@code verify} executions a project model declares rather than an Airness parent.
     *
     * @param declared whether a project model declares an execution
     * @return one problem per such execution
     */
    List<String> declared(Predicate<Step> declared) {
        return this.plan.stream()
            .filter(step -> VERIFY.equals(step.phase()))
            .filter(declared)
            .map(step -> step.label() + " is a verify execution the project declares" + REPAIR)
            .toList();
    }

    /**
     * Whether a project model, rather than an Airness parent, declares an execution: the project and every
     * parent up to the first Airness parent, in their builds, plugin management and profiles.
     *
     * @param project the project
     * @return the test
     */
    static Predicate<Step> declaredBy(MavenProject project) {
        List<Model> models = Stream.iterate(project, Objects::nonNull, MavenProject::getParent)
            .takeWhile(model -> !RepositoryProjects.harnessParent(model))
            .map(MavenProject::getOriginalModel)
            .filter(Objects::nonNull)
            .toList();
        return step -> models.stream().flatMap(CompletionOrder::containers)
            .flatMap(container -> container.getPlugins().stream())
            .filter(plugin -> step.plugin().equals(key(plugin)))
            .flatMap(plugin -> plugin.getExecutions().stream())
            .map(PluginExecution::getId)
            .anyMatch(step.execution()::equals);
    }

    private Optional<Integer> completion() {
        return IntStream.range(0, this.plan.size()).filter(index -> this.plan.get(index).completes())
            .boxed().findFirst();
    }

    private static Stream<PluginContainer> containers(Model model) {
        Stream<Profile> profiles = model.getProfiles().stream();
        return Stream.concat(
            Stream.ofNullable(model.getBuild()).flatMap(
                build -> Stream.concat(Stream.of(build), Stream.ofNullable(build.getPluginManagement()))
            ),
            profiles.map(Profile::getBuild).filter(Objects::nonNull).flatMap(
                build -> Stream.concat(Stream.of(build), Stream.ofNullable(build.getPluginManagement()))
            )
        );
    }

    private static String key(Plugin plugin) {
        String group = Optional.ofNullable(plugin.getGroupId()).orElse(MAVEN_PLUGINS);
        return group + ':' + plugin.getArtifactId();
    }

    /**
     * One planned execution.
     *
     * @param plugin    the plugin's {@code groupId:artifactId}
     * @param goal      the goal
     * @param execution the execution id
     * @param phase     the phase it is bound to, or nothing for a goal named on the command line
     */
    public record Step(String plugin, String goal, String execution, @Nullable String phase) {

        static Step of(MojoExecution execution) {
            return new Step(
                execution.getGroupId() + ':' + execution.getArtifactId(), execution.getGoal(),
                execution.getExecutionId(), execution.getLifecyclePhase()
            );
        }

        boolean completes() {
            return AIRNESS_PLUGIN.equals(this.plugin) && GOAL.equals(this.goal);
        }

        // Signing reads the archives the verdict covers and writes only their signatures, so it follows the verdict.
        boolean signs() {
            return SIGNER.equals(this.plugin) && "sign".equals(this.goal);
        }

        String goalPrefix() {
            String artifact = this.plugin.substring(this.plugin.indexOf(':') + 1);
            String prefix = artifact.replace("-maven-plugin", "").replace("maven-", "").replace("-plugin", "");
            return prefix + ':' + this.goal + (this.execution.startsWith("default-") ? "" : '@' + this.execution);
        }

        String label() {
            return this.plugin.substring(this.plugin.indexOf(':') + 1) + ':' + this.goal + " (" + this.execution
                + ")";
        }
    }
}
