package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class InputConfigurationTest {

    @Test
    void defaultsToMavensDefaultExclusions() {
        assertTrue(new InputConfiguration(Optional.empty()).defaultExcludes());
    }

    @Test
    void honorsTheResolvedBooleanSetting() {
        InputConfiguration configuration = new InputConfiguration(
            Optional.of(
                InputProjects.xml(
                    "<configuration><addDefaultExcludes>false</addDefaultExcludes></configuration>"
                )
            )
        );
        assertFalse(configuration.defaultExcludes());
    }

    @Test
    void refusesAnUnresolvedBooleanInsteadOfSilentlyTreatingItAsFalse() {
        InputConfiguration configuration = new InputConfiguration(
            Optional.of(
                InputProjects.xml(
                    "<configuration><addDefaultExcludes>${unknown}</addDefaultExcludes></configuration>"
                )
            )
        );
        assertThrows(IllegalStateException.class, configuration::defaultExcludes);
    }

    @Test
    void refusesAConfigurationThatMavenDidNotResolveAsXml() {
        assertThrows(IllegalStateException.class, () -> new InputConfiguration(Optional.of("not XML")));
    }
}
