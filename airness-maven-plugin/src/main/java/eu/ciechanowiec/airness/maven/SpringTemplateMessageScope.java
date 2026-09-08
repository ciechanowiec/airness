package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.SpringContextCheck;
import eu.ciechanowiec.airness.governance.TemplateMessageIndex;
import eu.ciechanowiec.airness.governance.TemplateMessageInputs;
import eu.ciechanowiec.airness.governance.TemplateMessageReference;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;

/**
 * Module-local production inputs and the existing test-evidence destination.
 */
final class SpringTemplateMessageScope {

    private final Path root;
    private final MavenProject project;
    private final long started;

    SpringTemplateMessageScope(Path root, MavenProject project, long started) {
        this.root = root;
        this.project = project;
        this.started = started;
    }

    static boolean includesThymeleaf(MavenProject project) {
        return project.getArtifacts().stream().anyMatch(
            artifact -> "org.thymeleaf".equals(artifact.getGroupId())
                && "thymeleaf-spring6".equals(artifact.getArtifactId()) && !"test".equals(artifact.getScope())
        );
    }

    TemplateMessageInputs inputs() {
        List<Path> sources = this.project.getCompileSourceRoots().stream().map(Path::of).toList();
        List<Path> resources = this.project.getResources().stream().map(Resource::getDirectory).map(Path::of).toList();
        SpringContextCheck context = new SpringContextCheck(this.root, sources, this.evidence(), this.started);
        List<String> applications = includesThymeleaf(this.project) ? context.applicationNames() : List.of();
        List<TemplateMessageReference> references = applications.isEmpty() ? List.of()
            : new TemplateMessageIndex(this.root, resources).references();
        return new TemplateMessageInputs(this.started, applications, references);
    }

    Path evidence() {
        return Path.of(this.project.getBuild().getDirectory(), "airness", "spring-context.evidence");
    }
}
