package eu.ciechanowiec.airness.maven;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.License;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;

/**
 * Shares verified supplier metadata between the native license check and compliance reports.
 *
 * @param builder  Maven model builder
 * @param request  active Maven model-building settings
 * @param registry verified artifact metadata supplied by the harness
 */
record MavenLicenseMetadata(ProjectBuilder builder, ProjectBuildingRequest request, List<LicenseMetadata> registry) {

    MavenLicenseMetadata {
        registry = List.copyOf(registry);
    }

    MavenLicenseMetadata(ProjectBuilder builder, ProjectBuildingRequest request) {
        this(builder, request, LicenseMetadata.entries());
    }

    @SneakyThrows
    List<String> declarations(Artifact artifact) {
        ProjectBuildingRequest settings = new DefaultProjectBuildingRequest(this.request);
        settings.setProcessPlugins(false);
        settings.setResolveDependencies(false);
        // Supplier build declarations are metadata, not this project's build contract.
        settings.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
        MavenProject model = this.builder.build(artifact, settings).getProject();
        return model.getLicenses().stream().map(MavenLicenseMetadata::license).toList();
    }

    static Optional<LicenseMetadata> verified(Artifact artifact, List<String> declared) {
        return verified(artifact, declared, LicenseMetadata.entries());
    }

    private static Optional<LicenseMetadata> verified(
        Artifact artifact, List<String> declared, List<LicenseMetadata> registry
    ) {
        Optional<LicenseMetadata> metadata = registry.stream()
            .filter(entry -> entry.coordinate().equals(MavenReportArtifacts.identity(artifact))).findFirst();
        metadata.ifPresent(entry -> entry.verify(Objects.requireNonNull(artifact.getFile()).toPath(), declared));
        return metadata;
    }

    @SneakyThrows
    void prepare(MavenProject project) {
        Set<Artifact> artifacts = MavenReportArtifacts.resolved(project);
        String entries = artifacts.stream()
            .filter(this::registered)
            .flatMap(artifact -> this.correction(artifact, artifacts).stream())
            .filter(entry -> entry.evidence().declared().isEmpty())
            .map(MavenLicenseMetadata::property).sorted().collect(Collectors.joining("\n"));
        Path output = Path.of(project.getBuild().getDirectory()).resolve("airness/licenses/THIRD-PARTY.properties");
        Files.createDirectories(output.getParent());
        Files.writeString(
            output, "# Verified by Airness against exact artifact bytes and supplier metadata.\n" + entries + "\n"
        );
    }

    private boolean registered(Artifact artifact) {
        String coordinate = MavenReportArtifacts.identity(artifact);
        return this.registry.stream().map(LicenseMetadata::coordinate).anyMatch(coordinate::equals);
    }

    private Optional<LicenseMetadata> correction(Artifact artifact, Set<Artifact> artifacts) {
        String coordinate = MavenReportArtifacts.gav(artifact);
        long matches = artifacts.stream().map(MavenReportArtifacts::gav).filter(coordinate::equals).count();
        // The native properties format cannot distinguish classifiers or artifact types sharing one GAV.
        if (matches != 1) {
            throw new IllegalStateException(
                "Ambiguous artifact types or classifiers for license evidence: " + coordinate
            );
        }
        return verified(artifact, this.declarations(artifact), this.registry);
    }

    private static String property(LicenseMetadata metadata) {
        String[] parts = metadata.coordinate().split(":", -1);
        return parts[0] + "--" + parts[1] + "--" + parts[parts.length - 1] + '=' + metadata.license();
    }

    private static String license(License license) {
        return Optional.ofNullable(license.getName()).filter(value -> !value.isBlank())
            .orElseGet(() -> Optional.ofNullable(license.getUrl()).orElse("Unknown"));
    }
}
