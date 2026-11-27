package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RootLicensesTest {

    @TempDir
    private Path root;

    @Test
    void readsTheRootProjectUpTheParentChain() {
        Model raw = new Model();
        License license = new License();
        license.setName(" Apache-2.0 ");
        raw.addLicense(license);
        raw.addLicense(new License());
        License blank = new License();
        blank.setName("  ");
        raw.addLicense(blank);
        MavenProject rootProject = new MavenProject();
        rootProject.setOriginalModel(raw);
        rootProject.setFile(this.root.resolve("pom.xml").toFile());
        MavenProject module = new MavenProject();
        module.setFile(new File(this.root.resolve("web").toFile(), "pom.xml"));
        module.setParent(rootProject);
        assertEquals(List.of("Apache-2.0"), RootLicenses.of(module, this.root));
    }

    @Test
    @SneakyThrows
    void readsTheRootPomFileForAModuleThatInheritsAnAirnessParentDirectly() {
        Files.writeString(
            this.root.resolve("pom.xml"),
            """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <licenses>
                        <license>
                            <name>LicenseRef-Proprietary</name>
                        </license>
                    </licenses>
                </project>
                """
        );
        MavenProject module = new MavenProject();
        module.setFile(new File(this.root.resolve("web").toFile(), "pom.xml"));
        assertEquals(List.of("LicenseRef-Proprietary"), RootLicenses.of(module, this.root));
    }

    @Test
    void answersNoneWithoutARootPom() {
        assertEquals(List.of(), RootLicenses.of(new MavenProject(), this.root));
    }
}
