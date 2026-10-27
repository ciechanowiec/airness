package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.maven.CompletionOrder.Step;
import java.util.List;
import java.util.function.Predicate;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Profile;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

/**
 * Completing a module is its last execution: nothing in verify and no command-line goal runs after
 * it, and a project binds nothing in verify at all.
 */
class CompletionOrderTest {

    private static final String AIRNESS = "eu.ciechanowiec:airness-maven-plugin";
    private static final String VERIFY = "verify";
    private static final Step COMPLETE = new Step(AIRNESS, CompletionOrder.GOAL, "airness-module-complete", VERIFY);
    private static final Step TREE = new Step(AIRNESS, "tree-verify", "airness-governance", VERIFY);

    @Test
    void acceptsCompletionAsTheLastVerifyExecution() {
        CompletionOrder order = new CompletionOrder(
            List.of(
                new Step(AIRNESS, "node-gate", "node-gate-e2e", "post-integration-test"), TREE, COMPLETE,
                new Step("org.apache.maven.plugins:maven-install-plugin", "install", "default-install", "install")
            )
        );
        assertTrue(order.completes());
        assertEquals(List.of(), order.late(), "a later phase acts on a module the verdict already covers");
    }

    @Test
    void refusesAGateBoundOrNamedAfterCompletion() {
        CompletionOrder order = new CompletionOrder(
            List.of(
                COMPLETE, new Step(AIRNESS, "node-gate", "node-gate-late", VERIFY),
                new Step(AIRNESS, "node-gate", "node-gate-e2e", null),
                new Step("org.sonarsource.scanner.maven:sonar-maven-plugin", "sonar", "default-cli", null)
            )
        );
        assertEquals(
            List.of(
                "airness-maven-plugin:node-gate (node-gate-late) runs after airness:module-complete, so after the"
                    + " closing step judged the build; bind it to a phase before verify, such as post-integration-test",
                "airness-maven-plugin:node-gate (node-gate-e2e) runs after airness:module-complete, so after the"
                    + " closing step judged the build; name it before the lifecycle (mvn"
                    + " airness:node-gate@node-gate-e2e verify), or run it in an invocation of its own after the build"
                    + " (mvn verify, then mvn airness:node-gate@node-gate-e2e)",
                "sonar-maven-plugin:sonar (default-cli) runs after airness:module-complete, so after the closing step"
                    + " judged the build; name it before the lifecycle (mvn sonar:sonar verify), or run it in an"
                    + " invocation of its own after the build (mvn verify, then mvn sonar:sonar)"
            ),
            order.late()
        );
    }

    @Test
    void refusesWhatAProfileOrAChildParentBindsInVerifyAfterCompletion() {
        // Maven merges a profile's executions, and a child parent's, after every one of the generic build, so
        // their verify executions come after module-complete in the plan it calculates.
        Step profile = new Step(AIRNESS, "node-gate", "node-gate-build", VERIFY);
        Step child = new Step(AIRNESS, "spring-context", "airness-spring-context", VERIFY);
        Step earlier = new Step(AIRNESS, "spring-context", "airness-spring-context", "post-integration-test");
        assertEquals(2, new CompletionOrder(List.of(TREE, COMPLETE, profile, child)).late().size());
        assertEquals(List.of(), new CompletionOrder(List.of(earlier, TREE, COMPLETE)).late());
    }

    @Test
    void letsOnlySigningFollowCompletion() {
        Step sign = new Step("org.apache.maven.plugins:maven-gpg-plugin", "sign", "sign-artifacts", VERIFY);
        Step other = new Step("org.apache.maven.plugins:maven-gpg-plugin", "sign-and-deploy-file", "x", VERIFY);
        assertEquals(List.of(), new CompletionOrder(List.of(COMPLETE, sign)).late());
        assertEquals(1, new CompletionOrder(List.of(COMPLETE, other)).late().size());
    }

    @Test
    void completesOnlyThroughTheAirnessGoal() {
        assertFalse(new Step("com.example:other-plugin", CompletionOrder.GOAL, "x", VERIFY).completes());
        assertFalse(new Step(AIRNESS, "tree-verify", "x", VERIFY).completes());
        assertTrue(COMPLETE.completes());
    }

    @Test
    void checksNothingLateInAPlanThatNeverCompletes() {
        CompletionOrder order = new CompletionOrder(List.of(new Step(AIRNESS, "blocklist", "airness-blocklist", null)));
        assertFalse(order.completes());
        assertEquals(List.of(), order.late());
    }

    @Test
    void readsThePlanMavenProduced() {
        PluginDescriptor plugin = new PluginDescriptor();
        plugin.setGroupId("eu.ciechanowiec");
        plugin.setArtifactId("airness-maven-plugin");
        MojoDescriptor descriptor = new MojoDescriptor();
        descriptor.setGoal(CompletionOrder.GOAL);
        descriptor.setPluginDescriptor(plugin);
        MojoExecution execution = new MojoExecution(descriptor, "airness-module-complete");
        execution.setLifecyclePhase(VERIFY);
        assertEquals(new CompletionOrder(List.of(COMPLETE)), CompletionOrder.of(List.of(execution)));
    }

    @Test
    void refusesAVerifyExecutionTheProjectDeclaresWhereverItIsDeclared() {
        Step failsafe = new Step("org.apache.maven.plugins:maven-failsafe-plugin", VERIFY, "own-verify", VERIFY);
        Step inherited = new Step("org.apache.maven.plugins:maven-failsafe-plugin", VERIFY, "inherited", VERIFY);
        Step earlier = new Step(
            "org.apache.maven.plugins:maven-failsafe-plugin", "integration-test", "own-verify",
            "integration-test"
        );
        CompletionOrder order = new CompletionOrder(List.of(failsafe, inherited, earlier, COMPLETE));
        for (MavenProject project : List.of(declaring(false, false), declaring(true, false), declaring(false, true))) {
            Predicate<Step> declared = CompletionOrder.declaredBy(project);
            assertEquals(
                List.of(
                    "maven-failsafe-plugin:verify (own-verify) is a verify execution the project declares; bind it to"
                        + " a phase before verify, such as post-integration-test"
                ),
                order.declared(declared),
                "the build, plugin management and a profile of a project model all declare"
            );
        }
    }

    @Test
    void trustsWhatAnAirnessParentDeclares() {
        MavenProject parent = declaring(false, false);
        parent.setGroupId("eu.ciechanowiec");
        parent.setArtifactId("airness-parent");
        MavenProject child = new MavenProject();
        child.setParent(parent);
        Step failsafe = new Step("org.apache.maven.plugins:maven-failsafe-plugin", VERIFY, "own-verify", VERIFY);
        assertEquals(List.of(), new CompletionOrder(List.of(failsafe)).declared(CompletionOrder.declaredBy(child)));
    }

    private static Build build(boolean managed) {
        Plugin plugin = new Plugin();
        plugin.setArtifactId("maven-failsafe-plugin");
        PluginExecution execution = new PluginExecution();
        execution.setId("own-verify");
        plugin.addExecution(execution);
        Build build = new Build();
        if (managed) {
            PluginManagement management = new PluginManagement();
            management.addPlugin(plugin);
            build.setPluginManagement(management);
        } else {
            build.addPlugin(plugin);
        }
        return build;
    }

    private static MavenProject declaring(boolean managed, boolean profiled) {
        Build build = build(managed);
        Model model = new Model();
        model.setGroupId("com.example");
        model.setArtifactId("consumer");
        if (profiled) {
            Profile profile = new Profile();
            profile.setId("own");
            profile.setBuild(build);
            model.addProfile(profile);
        } else {
            model.setBuild(build);
        }
        MavenProject project = new MavenProject(model);
        project.setOriginalModel(model);
        return project;
    }
}
