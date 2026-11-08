package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;

/**
 * The roles the file linter's configuration exempts are read from the model and matched as its directory
 * scanner matches them.
 */
class LinterExemptionsTest {

    private static final String TSV = "**/*.tsv";
    private static final String TARGET = "**/target/**";

    @Test
    void readsTheExcludesTheLinterIsConfiguredWith() {
        assertEquals(
            List.of(TSV, TARGET),
            LinterExemptions.from(project(configuration(" " + TSV + " ", TARGET))).patterns()
        );
    }

    @Test
    void matchesAPatternAtTheRootAndAtAnyDepth() {
        LinterExemptions exemptions = new LinterExemptions(List.of(TSV, TARGET, ".idea/**"));
        assertAll(
            () -> assertTrue(exemptions.exempts("table.tsv")),
            () -> assertTrue(exemptions.exempts("data/nested/table.tsv")),
            () -> assertTrue(exemptions.exempts("target/notes.txt")),
            () -> assertTrue(exemptions.exempts("module/target/classes/notes.txt")),
            () -> assertTrue(exemptions.exempts(".idea/workspace.xml")),
            () -> assertFalse(exemptions.exempts("module/.idea/workspace.xml")),
            () -> assertFalse(exemptions.exempts("targets/notes.txt")),
            () -> assertFalse(exemptions.exempts("table.tsv.md"))
        );
    }

    @Test
    void exemptsNothingWhenTheLinterDeclaresNoExcludes() {
        LinterExemptions exemptions = LinterExemptions.from(project(new Xpp3Dom("configuration")));
        assertAll(
            () -> assertEquals(List.of(), exemptions.patterns()),
            () -> assertFalse(exemptions.exempts("table.tsv"))
        );
    }

    @Test
    void refusesAProjectThatConfiguresNoLinter() {
        MavenProject project = new MavenProject(new Model());
        project.getModel().setBuild(new Build());
        IllegalStateException refusal = assertThrows(
            IllegalStateException.class, () -> LinterExemptions.from(project)
        );
        assertEquals(
            "Missing inherited file linter configuration org.ec4j.maven:editorconfig-maven-plugin",
            refusal.getMessage()
        );
    }

    private static MavenProject project(Xpp3Dom configuration) {
        Plugin linter = new Plugin();
        linter.setGroupId("org.ec4j.maven");
        linter.setArtifactId("editorconfig-maven-plugin");
        linter.setConfiguration(configuration);
        Build build = new Build();
        build.addPlugin(linter);
        Model model = new Model();
        model.setBuild(build);
        return new MavenProject(model);
    }

    private static Xpp3Dom configuration(String... patterns) {
        Xpp3Dom excludes = new Xpp3Dom("excludes");
        Arrays.stream(patterns).map(LinterExemptionsTest::exclude).forEach(excludes::addChild);
        Xpp3Dom configuration = new Xpp3Dom("configuration");
        configuration.addChild(excludes);
        return configuration;
    }

    private static Xpp3Dom exclude(String pattern) {
        Xpp3Dom exclude = new Xpp3Dom("exclude");
        exclude.setValue(pattern);
        return exclude;
    }
}
