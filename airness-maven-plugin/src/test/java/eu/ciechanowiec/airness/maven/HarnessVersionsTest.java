package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

/**
 * Each harness npm package is expected at the version of its matching Airness parent in the module's chain,
 * not at the version of the immediate parent.
 */
class HarnessVersionsTest {

    private static MavenProject project(String artifact, String version) {
        Model model = new Model();
        model.setGroupId("eu.ciechanowiec");
        model.setArtifactId(artifact);
        model.setVersion(version);
        return new MavenProject(model);
    }

    @Test
    void readsEachPackageFromItsOwnParentInTheChain() {
        MavenProject root = project("airness-parent", "2.0.0");
        MavenProject node = project("airness-parent-node", "2.0.0");
        node.setParent(root);
        MavenProject payload = project("airness-parent-payload", "1.4.0");
        payload.setParent(node);
        MavenProject module = project("cms", "9.0.0");
        module.setParent(payload);
        assertEquals("ploaness=1.4.0,@airness/node=2.0.0", HarnessVersions.of(module).value());
    }

    @Test
    void namesNothingForAChainWithoutAnAirnessParent() {
        assertEquals(
            "",
            HarnessVersions.of(List.of(List.of("org.example", "airness-parent", "1.0.0"))).value(),
            "only the Airness group counts"
        );
    }

    @Test
    void keepsTheNearestParentWhenOneIsNamedTwice() {
        assertEquals(
            "@airness/node=2.0.0",
            HarnessVersions.of(
                List.of(
                    List.of("eu.ciechanowiec", "airness-parent", "2.0.0"),
                    List.of("eu.ciechanowiec", "airness-parent", "1.0.0")
                )
            ).value()
        );
    }

    @Test
    void handsEveryGateTheChainsVersionsAndTheModulesOwnVersionAndIdentity() {
        MavenProject node = project("airness-parent-node", "2.0.0");
        node.setParent(project("airness-parent", "2.0.0"));
        MavenProject library = project("ploaness-node", "3.1.0");
        library.setParent(node);
        Map<String, String> environment = NodeGateMojo.environment(library);
        assertEquals("@airness/node=2.0.0", environment.get("AIRNESS_HARNESS_VERSIONS"));
        assertEquals("3.1.0", environment.get("AIRNESS_MODULE_VERSION"));
        assertEquals("eu.ciechanowiec:ploaness-node", environment.get("AIRNESS_MODULE"));
    }
}
