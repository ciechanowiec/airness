package eu.ciechanowiec.airness.maven;

import java.util.List;
import java.util.stream.Stream;
import javax.inject.Inject;
import lombok.SneakyThrows;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.internal.GoalTask;
import org.apache.maven.lifecycle.internal.LifecycleExecutionPlanCalculator;
import org.apache.maven.lifecycle.internal.LifecycleTask;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;

/**
 * Refuses, before anything is built, a module whose planned order would let an execution run after closing.
 *
 * <p>The closing step runs inside {@code airness:module-complete}, the last execution an Airness parent binds in
 * {@code verify}. Maven runs a project's own {@code verify} executions after every inherited one, and a goal
 * named on the command line after the whole lifecycle, so either would run after the closing step judged the
 * build. This reads the order Maven planned for the module and refuses both, and the same check runs again at
 * completion. A project binds its own steps to an earlier phase, such as {@code post-integration-test}.
 */
@Mojo(name = "completion-order", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public final class CompletionOrderMojo extends AbstractPreflightMojo {

    private final LifecycleExecutionPlanCalculator plans;

    /**
     * Uses Maven's own execution planner.
     *
     * @param plans Maven's execution planner
     */
    @Inject
    public CompletionOrderMojo(LifecycleExecutionPlanCalculator plans) {
        this.plans = plans;
    }

    @Override
    boolean applies() {
        return true;
    }

    @Override
    List<String> problems() {
        return problems(this.plans, this.session(), this.project());
    }

    /**
     * Everything that would run after the module completes, and every {@code verify} execution the project
     * declares, in the order Maven planned for this invocation.
     *
     * @param plans   Maven's execution planner
     * @param session the session
     * @param project the module
     * @return one problem per execution, empty when completion is last
     */
    @SneakyThrows
    static List<String> problems(LifecycleExecutionPlanCalculator plans, MavenSession session, MavenProject project) {
        List<Object> tasks = session.getGoals().stream().map(CompletionOrderMojo::task).toList();
        CompletionOrder order = CompletionOrder.of(
            plans.calculateExecutionPlan(session, project, tasks, false).getMojoExecutions()
        );
        return Stream.concat(order.late().stream(), order.declared(CompletionOrder.declaredBy(project)).stream())
            .toList();
    }

    private static Object task(String goal) {
        return goal.contains(":") ? new GoalTask(goal) : new LifecycleTask(goal);
    }
}
