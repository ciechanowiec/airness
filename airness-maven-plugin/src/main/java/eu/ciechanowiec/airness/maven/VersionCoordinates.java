package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.DeclaredCoordinate;
import eu.ciechanowiec.airness.governance.DeclaredCoordinates;
import eu.ciechanowiec.airness.governance.OwnedCoordinate;
import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

/**
 * Finds every raw pom that owns versions affecting the current build: the reactor and every direct
 * or indirect parent. Maven's resolved project lineage supplies each parent's actual file and the
 * effective properties used to interpolate raw declarations. Coordinates produced by the same reactor
 * are excluded because no registry release can replace an artifact being built in this session.
 */
@UtilityClass
final class VersionCoordinates {

    private static final String SEPARATOR = ":";
    private static final String POM_SUFFIX = ".pom";
    private static final Pattern EXPRESSION = Pattern.compile("\\$\\{([^}]+)}");

    static List<OwnedCoordinate> from(MavenSession session) {
        Path repository = session.getRepositorySession().getLocalRepositoryManager()
            .getRepository().getBasedir().toPath();
        return from(session.getAllProjects(), repository);
    }

    static List<OwnedCoordinate> from(Collection<MavenProject> projects, Path repository) {
        Map<String, VersionPom> reactor = projects.stream()
            .map(project -> versionPom(project, repository))
            .collect(Collectors.toUnmodifiableMap(VersionPom::key, Function.identity()));
        Set<String> reactorCoordinates = Set.copyOf(reactor.keySet());
        return projects.stream()
            .flatMap(VersionCoordinates::lineage)
            .map(project -> versionPom(project, repository))
            .distinct()
            .flatMap(VersionCoordinates::declared)
            .filter(declared -> !reactorCoordinates.contains(versionedKey(declared.coordinate())))
            .distinct()
            .toList();
    }

    private static Stream<MavenProject> lineage(MavenProject project) {
        return Stream.iterate(project, Objects::nonNull, MavenProject::getParent);
    }

    private static Stream<OwnedCoordinate> declared(VersionPom pom) {
        return DeclaredCoordinates.from(pom.file()).stream()
            .map(coordinate -> resolved(coordinate, pom))
            .map(coordinate -> new OwnedCoordinate(pom.owner(), coordinate));
    }

    private static VersionPom versionPom(MavenProject project, Path repository) {
        String owner = project.getGroupId() + SEPARATOR + project.getArtifactId();
        Path file = Optional.ofNullable(project.getFile())
            .map(File::toPath)
            .orElseGet(() -> localPom(project, repository));
        return new VersionPom(owner, versionedKey(project), file, properties(project));
    }

    private static Path localPom(MavenProject project, Path repository) {
        String group = project.getGroupId().replace('.', File.separatorChar);
        String filename = project.getArtifactId() + '-' + project.getVersion() + POM_SUFFIX;
        return repository.resolve(group)
            .resolve(project.getArtifactId())
            .resolve(project.getVersion())
            .resolve(filename);
    }

    private static DeclaredCoordinate resolved(DeclaredCoordinate coordinate, VersionPom pom) {
        String version = resolve(coordinate.version(), pom.properties(), new HashSet<>());
        return new DeclaredCoordinate(coordinate.groupId(), coordinate.artifactId(), version);
    }

    private static String resolve(String version, Map<String, String> properties, Set<String> seen) {
        Matcher expressions = EXPRESSION.matcher(version);
        StringBuilder resolved = new StringBuilder();
        boolean changed = false;
        while (expressions.find()) {
            String property = expressions.group(1);
            Optional<String> held = Optional.ofNullable(properties.get(property));
            String replacement = held
                .map(value -> resolvedProperty(property, value, properties, seen))
                .orElseGet(expressions::group);
            changed = changed || held.isPresent();
            expressions.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
        }
        if (resolved.isEmpty()) {
            return version;
        }
        expressions.appendTail(resolved);
        return changed ? resolve(resolved.toString(), properties, seen) : resolved.toString();
    }

    private static String resolvedProperty(
        String property, String value, Map<String, String> properties, Set<String> seen
    ) {
        if (!seen.add(property)) {
            throw new IllegalStateException("Cyclic Maven version property: " + property);
        }
        String resolved = resolve(value, properties, seen);
        seen.remove(property);
        return resolved;
    }

    private static Map<String, String> properties(MavenProject project) {
        Properties held = project.getProperties();
        Map<String, String> properties = held.stringPropertyNames().stream()
            .collect(Collectors.toMap(Function.identity(), held::getProperty));
        properties.put("project.groupId", project.getGroupId());
        properties.put("pom.groupId", project.getGroupId());
        properties.put("project.artifactId", project.getArtifactId());
        properties.put("pom.artifactId", project.getArtifactId());
        properties.put("project.version", project.getVersion());
        properties.put("pom.version", project.getVersion());
        return Map.copyOf(properties);
    }

    private static String owner(DeclaredCoordinate coordinate) {
        return coordinate.groupId() + SEPARATOR + coordinate.artifactId();
    }

    private static String versionedKey(DeclaredCoordinate coordinate) {
        return owner(coordinate) + SEPARATOR + coordinate.version();
    }

    private static String versionedKey(MavenProject project) {
        return project.getGroupId() + SEPARATOR + project.getArtifactId() + SEPARATOR
            + project.getVersion();
    }

    private record VersionPom(String owner, String key, Path file, Map<String, String> properties) {
    }
}
