package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.MetadataSource;
import eu.ciechanowiec.airness.governance.ModuleIdentity;
import eu.ciechanowiec.airness.governance.ProjectMetadata;
import eu.ciechanowiec.airness.governance.ProjectOwnership;
import eu.ciechanowiec.airness.governance.ScmCoordinates;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.maven.model.Developer;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.Organization;
import org.apache.maven.model.Scm;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

/**
 * Copies what a Maven model says about a project into the records the governance rules read, which know no Maven
 * type.
 */
@UtilityClass
final class ProjectFacts {

    // Every metadata field Maven hands a child, with how a model declares it. The name is never inherited.
    private static final Map<String, Predicate<ProjectMetadata>> INHERITED = Map.of(
        "description", metadata -> !metadata.description().isBlank(),
        "url", metadata -> !metadata.url().isBlank(),
        "inceptionYear", metadata -> !metadata.inceptionYear().isBlank(),
        "licenses", metadata -> !metadata.ownership().licenses().isEmpty(),
        "developers", metadata -> !metadata.ownership().developers().isEmpty(),
        "organization", metadata -> !metadata.ownership().organization().isBlank(),
        "scm/url", metadata -> !metadata.scm().url().isBlank(),
        "scm/connection", metadata -> !metadata.scm().connection().isBlank(),
        "scm/developerConnection", metadata -> !metadata.scm().developerConnection().isBlank()
    );

    /**
     * The metadata one model states, each value it leaves out read as empty.
     *
     * @param model the raw or the effective model
     * @return the metadata
     */
    static ProjectMetadata metadata(Model model) {
        Optional<Scm> scm = Optional.ofNullable(model.getScm());
        return new ProjectMetadata(
            text(model.getName()), text(model.getDescription()), text(model.getUrl()), text(model.getInceptionYear()),
            new ProjectOwnership(
                model.getLicenses().stream().map(License::getName).map(ProjectFacts::text).toList(),
                model.getDevelopers().stream().map(Developer::getName).map(ProjectFacts::text).toList(),
                Optional.ofNullable(model.getOrganization()).map(Organization::getName).map(ProjectFacts::text)
                    .orElse("")
            ),
            new ScmCoordinates(
                scm.map(Scm::getUrl).map(ProjectFacts::text).orElse(""),
                scm.map(Scm::getConnection).map(ProjectFacts::text).orElse(""),
                scm.map(Scm::getDeveloperConnection).map(ProjectFacts::text).orElse("")
            )
        );
    }

    /**
     * The identity of a module: the model version its own pom writes, and its effective coordinates.
     *
     * @param project the module
     * @param root    whether the module lies at the repository root
     * @return the identity
     */
    static ModuleIdentity identity(MavenProject project, boolean root) {
        String modelVersion = Optional.ofNullable(project.getOriginalModel())
            .map(Model::getModelVersion)
            .map(ProjectFacts::text)
            .orElse("");
        String directory = Optional.ofNullable(project.getBasedir()).map(File::getName).orElse("");
        return new ModuleIdentity(
            modelVersion, project.getGroupId(), project.getArtifactId(), project.getVersion(), directory, root
        );
    }

    /**
     * The metadata each Airness pom up a module's parent chain declares itself.
     *
     * @param project the module
     * @return one entry per Airness pom, nearest first
     */
    static List<ProjectMetadata> harness(MavenProject project) {
        return lineage(project.getParent())
            .filter(ProjectFacts::airness)
            .map(MavenProject::getOriginalModel)
            .filter(Objects::nonNull)
            .map(ProjectFacts::metadata)
            .toList();
    }

    /**
     * Where each metadata field a module inherits comes from: the nearest pom up its chain, itself included, whose
     * own model declares it. A field no pom of the chain declares has no source.
     *
     * @param project the module
     * @return one source per declared field, ordered by field
     */
    static List<MetadataSource> sources(MavenProject project) {
        List<MavenProject> chain = lineage(project).filter(ProjectFacts::modelled).toList();
        return INHERITED.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .flatMap(field -> source(field.getKey(), field.getValue(), chain).stream())
            .toList();
    }

    private static Optional<MetadataSource> source(
        String field, Predicate<ProjectMetadata> declares, List<MavenProject> chain
    ) {
        return chain.stream()
            .filter(model -> declares.test(metadata(model.getOriginalModel())))
            .findFirst()
            .map(model -> new MetadataSource(field, model.getGroupId() + ':' + model.getArtifactId(), airness(model)));
    }

    private static boolean modelled(MavenProject project) {
        return Optional.ofNullable(project.getOriginalModel()).isPresent();
    }

    private static boolean airness(MavenProject project) {
        return AirnessCoordinates.isAggregator(project) || RepositoryProjects.harnessParent(project);
    }

    private static Stream<MavenProject> lineage(@Nullable MavenProject start) {
        return Stream.iterate(start, Objects::nonNull, MavenProject::getParent);
    }

    private static String text(@Nullable String value) {
        return Optional.ofNullable(value).map(String::strip).orElse("");
    }
}
