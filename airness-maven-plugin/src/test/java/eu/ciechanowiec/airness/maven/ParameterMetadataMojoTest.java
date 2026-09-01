package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ParameterMetadataMojoTest {

    @Test
    void appliesOnlyWhereMavenBindsJavaCompilation() {
        assertAll(
            () -> assertTrue(ParameterMetadataMojo.compilationBound("jar")),
            () -> assertFalse(ParameterMetadataMojo.compilationBound("pom"))
        );
    }
}
