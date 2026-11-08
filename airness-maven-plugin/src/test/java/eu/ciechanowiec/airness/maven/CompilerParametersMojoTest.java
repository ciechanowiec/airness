package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CompilerParametersMojoTest {

    @Test
    void appliesOnlyWhereMavenBindsJavaCompilation() {
        assertAll(
            () -> assertTrue(CompilerParametersMojo.compilationBound("jar")),
            () -> assertFalse(CompilerParametersMojo.compilationBound("pom"))
        );
    }
}
