package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.Justification;
import eu.ciechanowiec.airness.governance.ReportPackage;
import eu.ciechanowiec.airness.governance.ReportUse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.model.License;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;

/**
 * Resolves published POM license declarations through Maven's own effective model builder.
 *
 * @param builder Maven model builder
 * @param request active Maven model-building settings
 */
record MavenReportArtifacts(ProjectBuilder builder, ProjectBuildingRequest request) {

    @SneakyThrows
    ReportPackage tool(Artifact artifact, String module) {
        ProjectBuildingRequest settings = new DefaultProjectBuildingRequest(this.request);
        settings.setProcessPlugins(false);
        settings.setResolveDependencies(false);
        // Dependency POMs describe suppliers. Their build declarations are not this project's build contract.
        settings.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
        MavenProject model = this.builder.build(artifact, settings).getProject();
        List<String> licenses = model.getLicenses().stream().map(MavenReportArtifacts::license).toList();
        return new ReportPackage(
            identity(artifact), licenses, List.of(new ReportUse(module, "build", "Transitive")),
            Map.of()
        );
    }

    static List<ReportPackage> dependencies(MavenProject project, Map<String, List<String>> licenses) {
        Set<Artifact> artifacts = resolved(project);
        Set<String> coordinates = artifacts.stream().map(MavenReportArtifacts::gav)
            .collect(Collectors.toUnmodifiableSet());
        List<String> absent = licenses.keySet().stream().filter(coordinate -> !coordinates.contains(coordinate))
            .sorted().toList();
        if (!absent.isEmpty()) {
            throw new IllegalStateException("Resolved model omitted licensed dependencies: " + absent);
        }
        return artifacts.stream().map(artifact -> dependency(project, artifact, licenses)).toList();
    }

    @Justification(
        "Maven's copy constructor omits retained resolved artifacts. Its clone preserves the checked graph"
            + " so the report can restore a private filter without mutating reactor projects or resolving again."
    )
    @SuppressWarnings("UseOfClone")
    static Set<Artifact> resolved(MavenProject project) {
        // Maven clears a finished module's filter. Restore the complete view without mutating its model.
        MavenProject view = project.clone();
        view.setArtifactFilter(new ScopeArtifactFilter(Artifact.SCOPE_TEST));
        return Set.copyOf(view.getArtifacts());
    }

    static String identity(Artifact artifact) {
        String classifier = Optional.ofNullable(artifact.getClassifier()).filter(value -> !value.isEmpty())
            .map(value -> ':' + value).orElse("");
        return artifact.getGroupId() + ':' + artifact.getArtifactId() + ':' + artifact.getType() + classifier + ':'
            + artifact.getVersion();
    }

    private static ReportPackage dependency(
        MavenProject project, Artifact artifact, Map<String, List<String>> licenses
    ) {
        String coordinate = gav(artifact);
        List<String> declarations = Optional.ofNullable(licenses.get(coordinate))
            .orElseThrow(() -> new IllegalStateException("License inventory omitted " + coordinate));
        String scope = Optional.ofNullable(artifact.getScope()).orElseThrow();
        boolean direct = project.getDependencies().stream().anyMatch(
            dependency -> artifact.getGroupId().equals(dependency.getGroupId()) && artifact.getArtifactId().equals(
                dependency.getArtifactId()
            )
                && artifact.getVersion().equals(dependency.getVersion())
        );
        ReportUse use = new ReportUse(project.getId(), scope, direct ? "Direct" : "Transitive");
        Path file = Objects.requireNonNull(artifact.getFile(), "Unresolved dependency archive").toPath();
        Map<String, String> notices = List.of("compile", "runtime").contains(scope) ? ReportNotices.read(file) : Map
            .of();
        return new ReportPackage(identity(artifact), declarations, List.of(use), notices);
    }

    private static String gav(Artifact artifact) {
        return artifact.getGroupId() + ':' + artifact.getArtifactId() + ':' + artifact.getVersion();
    }

    private static String license(License license) {
        return Optional.ofNullable(license.getName()).filter(value -> !value.isBlank())
            .orElseGet(() -> Optional.ofNullable(license.getUrl()).orElse("Unknown"));
    }
}
