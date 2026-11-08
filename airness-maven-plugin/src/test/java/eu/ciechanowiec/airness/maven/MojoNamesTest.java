package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.junit.jupiter.api.Test;

/**
 * Every goal of this plugin is implemented by a class named after it, read from the descriptor this module's
 * build writes.
 *
 * <p>A build log names the goal that failed, so a maintainer chasing the failure looks for the class of that
 * name. The descriptor rather than the source is read, because it is what Maven itself binds, whatever spelling
 * the annotation is written in.
 */
class MojoNamesTest {

    @Test
    void namesEveryGoalClassAfterItsGoal() {
        List<MojoDescriptor> mojos = PluginGoals.all();
        assertFalse(mojos.isEmpty(), "the descriptor declares the goals of this plugin");
        assertEquals(
            Map.of(),
            mojos.stream()
                .filter(mojo -> !simpleName(mojo.getImplementation()).equals(className(mojo.getGoal())))
                .collect(
                    Collectors.toMap(
                        MojoDescriptor::getGoal, mojo -> simpleName(mojo.getImplementation()),
                        (first, _) -> first, TreeMap::new
                    )
                ),
            "rename each class to its goal in PascalCase followed by Mojo"
        );
    }

    @Test
    void spellsAGoalInPascalCaseFollowedByMojo() {
        assertEquals("WebAccessibilityMojo", className("web-accessibility"));
    }

    private static String className(String goal) {
        return Arrays.stream(goal.split("-"))
            .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
            .collect(Collectors.joining("", "", "Mojo"));
    }

    private static String simpleName(String implementation) {
        return implementation.substring(implementation.lastIndexOf('.') + 1);
    }
}
