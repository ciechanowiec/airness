package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.maven.model.Developer;
import org.apache.maven.model.License;
import org.apache.maven.model.Scm;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Validates the metadata and immutable coordinates required for a public Maven release.
 */
@Mojo(name = "publication-metadata", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public final class PublicationMetadataMojo extends AbstractPublicationMojo {

    private static final String SNAPSHOT = "-SNAPSHOT";

    @Override
    List<Findings> findings() {
        return List.of(
            new Findings("Released Maven coordinates", this.snapshotCoordinates()),
            new Findings("Maven publication project metadata", this.projectMetadata()),
            new Findings("Maven publication license metadata", this.licenseMetadata()),
            new Findings("Maven publication developer metadata", this.developerMetadata()),
            new Findings("Maven publication SCM metadata", this.scmMetadata())
        );
    }

    private List<String> snapshotCoordinates() {
        List<String> coordinates = Optional.ofNullable(this.project().getParent())
            .filter(parent -> snapshot(parent.getVersion()))
            .map(
                parent -> List.of(
                    "parent " + parent.getGroupId() + ':' + parent.getArtifactId()
                        + ':' + parent.getVersion()
                )
            )
            .orElseGet(List::of);
        return snapshot(this.project().getVersion())
            ? List.of("project " + this.project().getVersion())
            : coordinates;
    }

    private List<String> projectMetadata() {
        return Stream.of(
            missing("name", this.project().getName()),
            missing("description", this.project().getDescription()),
            missing("url", this.project().getUrl())
        ).flatMap(Optional::stream).toList();
    }

    private List<String> licenseMetadata() {
        List<License> licenses = this.project().getLicenses();
        if (licenses.isEmpty()) {
            return List.of("licenses");
        }
        return licenses.stream().flatMap(
            license -> Stream.of(
                missing("license name", license.getName()), missing("license url", license.getUrl())
            )
        ).flatMap(Optional::stream).toList();
    }

    private List<String> developerMetadata() {
        List<Developer> developers = this.project().getDevelopers();
        if (developers.isEmpty()) {
            return List.of("developers");
        }
        return developers.stream().map(Developer::getName)
            .map(name -> missing("developer name", name))
            .flatMap(Optional::stream)
            .toList();
    }

    private List<String> scmMetadata() {
        Optional<Scm> scm = Optional.ofNullable(this.project().getScm());
        if (scm.isEmpty()) {
            return List.of("scm");
        }
        return Stream.of(
            missing("scm connection", scm.orElseThrow().getConnection()),
            missing("scm developerConnection", scm.orElseThrow().getDeveloperConnection()),
            missing("scm url", scm.orElseThrow().getUrl())
        ).flatMap(Optional::stream).toList();
    }

    private static Optional<String> missing(String name, String value) {
        return Optional.of(name).filter(_ -> Optional.ofNullable(value).orElse("").isBlank());
    }

    private static boolean snapshot(String version) {
        return Optional.ofNullable(version).orElse("").endsWith(SNAPSHOT);
    }
}
