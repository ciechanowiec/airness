package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import eu.ciechanowiec.airness.governance.ManagedVersions;
import eu.ciechanowiec.airness.governance.ProjectProperties;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.maven.plugin.descriptor.Parameter;
import org.junit.jupiter.api.Test;

/**
 * Every property a goal of this plugin reads in place of an unconfigured parameter is one a project file may
 * not declare, read from the descriptor this module's build writes.
 *
 * <p>The governance module classifies the properties of the third-party plugins from their descriptors, and
 * cannot read this one, which is built after it. A goal added here with a parameter bound to a property outside
 * the Airness namespace would otherwise be a setting any child could change unreported.
 */
class PluginParameterPropertiesTest {

    private static final Pattern EXPRESSION = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern BUILT_IN = Pattern.compile(
        "^(?:(?:project|session|settings|mojoExecution|mojo|plugin|reactorProjects|localRepository|basedir"
            + "|executedProject|repositorySystemSession|pluginDescriptor|env)(?:\\.|$)"
            + "|maven\\.(?:home|version|build\\.))"
    );

    @Test
    void keepsEveryPropertyAGoalReadsFromAProjectFile() {
        Set<String> properties = properties();
        assertFalse(properties.isEmpty(), "the descriptor binds parameters to properties, skipTests among them");
        Set<String> images = Set.copyOf(ManagedVersions.imageProperties());
        assertEquals(
            Set.of(),
            properties.stream()
                .filter(property -> !ProjectProperties.reserves(property) && !images.contains(property))
                .filter(property -> !ProjectProperties.ownedByProject(property))
                .collect(Collectors.toCollection(TreeSet::new)),
            "reserve these in ProjectProperties, or name them as the project's own and document them"
        );
    }

    // The properties the editable parameters of every goal fall back to, as their expressions name them.
    private static Set<String> properties() {
        return PluginGoals.all().stream()
            .flatMap(mojo -> Optional.ofNullable(mojo.getParameters()).orElse(List.of()).stream())
            .filter(Parameter::isEditable)
            .map(Parameter::getExpression)
            .filter(Objects::nonNull)
            .map(expression -> EXPRESSION.matcher(expression.strip()))
            .filter(Matcher::matches)
            .map(matcher -> matcher.group(1))
            .filter(property -> !BUILT_IN.matcher(property).find())
            .collect(Collectors.toCollection(TreeSet::new));
    }
}
