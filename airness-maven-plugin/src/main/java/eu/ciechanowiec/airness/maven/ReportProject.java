package eu.ciechanowiec.airness.maven;

import com.microsoft.playwright.Playwright;
import eu.ciechanowiec.airness.governance.ReportLicensePolicy;
import eu.ciechanowiec.airness.governance.Repository;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.apache.maven.project.MavenProject;

/**
 * Project facts needed by the report after Maven has supplied its effective model.
 *
 * @param root     repository root
 * @param build    root project's build directory
 * @param identity project and tool identities
 * @param policy   inherited license policy
 */
record ReportProject(Path root, Path build, ReportIdentity identity, ReportLicensePolicy policy) {

    static ReportProject from(MavenProject top, MavenProject owner) {
        return new ReportProject(
            Repository.rootFrom(top.getBasedir().toPath()), Path.of(top.getBuild().getDirectory()),
            identity(top.getArtifactId(), owner), MavenReportPolicy.from(owner)
        );
    }

    private static ReportIdentity identity(String name, MavenProject owner) {
        return new ReportIdentity(
            name, Map.of(
                "Airness version", required(owner, "airness.version"),
                "PDF renderer", "Playwright " + Optional.ofNullable(
                    Playwright.class.getPackage().getImplementationVersion()
                ).orElseThrow(() -> new IllegalStateException("The PDF renderer has no published version")),
                "Vulnerability scanner", "Dependency-Check " + required(owner, "dependency-check-maven.version"),
                "Published SBOM generator", "CycloneDX Maven " + required(owner, "cyclonedx-maven-plugin.version")
            )
        );
    }

    private static String required(MavenProject project, String property) {
        return Optional.ofNullable(project.getProperties().getProperty(property))
            .orElseThrow(() -> new IllegalStateException("Missing inherited tool version: " + property));
    }
}
