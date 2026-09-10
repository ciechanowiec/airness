package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.SpringContextCheck;
import eu.ciechanowiec.airness.governance.StreamingInput;
import eu.ciechanowiec.airness.governance.StreamingTimeoutInputs;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;

/**
 * The current application and its production reactor dependency closure.
 */
final class StreamingTimeoutScope {

    private final Path root;
    private final MavenProject project;
    private final List<MavenProject> reactor;
    private final long started;

    StreamingTimeoutScope(Path root, MavenProject project, Collection<MavenProject> reactor, long started) {
        this.root = root;
        this.project = project;
        this.reactor = List.copyOf(reactor);
        this.started = started;
    }

    StreamingTimeoutInputs inputs() {
        List<Path> sources = this.project.getCompileSourceRoots().stream().map(Path::of).toList();
        SpringContextCheck check = new SpringContextCheck(this.root, sources, this.evidence(), this.started);
        List<String> applications = mvc() ? check.applicationNames() : List.of();
        List<StreamingInput> declarations = applications.isEmpty() ? List.of() : this.modules().stream()
            .flatMap(module -> new StreamingModuleInputs(this.root, module).read().stream()).toList();
        return new StreamingTimeoutInputs(this.started, applications, declarations);
    }

    Path evidence() {
        return Path.of(this.project.getBuild().getDirectory(), "airness", "spring-context.evidence");
    }

    private boolean mvc() {
        return this.project.getArtifacts().stream().filter(StreamingTimeoutScope::production)
            .anyMatch(
                artifact -> "org.springframework".equals(artifact.getGroupId())
                    && "spring-webmvc".equals(artifact.getArtifactId())
            );
    }

    private List<MavenProject> modules() {
        Set<String> dependencies = this.project.getArtifacts().stream().filter(StreamingTimeoutScope::production)
            .map(StreamingTimeoutScope::coordinates).collect(Collectors.toUnmodifiableSet());
        return this.reactor.stream().filter(
            module -> module.equals(this.project)
                || dependencies.contains(coordinates(module.getArtifact()))
        ).toList();
    }

    private static boolean production(Artifact artifact) {
        return !Artifact.SCOPE_TEST.equals(artifact.getScope());
    }

    private static String coordinates(Artifact artifact) {
        return artifact.getGroupId() + ':' + artifact.getArtifactId() + ':' + artifact.getVersion();
    }
}
