package eu.ciechanowiec.airness.governance;

import java.time.Year;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * Root pom metadata a test varies one value of at a time.
 *
 * <p>A value is named by key, the license and developer keys taking comma-separated lists, and an empty value is
 * one the pom does not state. The clean metadata passes every rule, so a test that changes one value reads the
 * verdict of the rule that value answers to alone.
 */
@UtilityClass
final class RootMetadataFixtures {

    static final LicenseCatalogue LICENSES = new LicenseCatalogue("3.29.0", Set.of("MIT-0", "Apache-2.0", "Unlicense"));
    static final Year CURRENT = Year.of(2026);
    static final Year FIRST_COMMIT = Year.of(2025);

    static final ProjectMetadata AIRNESS = metadata(
        Map.of(
            "name", "airness",
            "description", "The reusable Java build and repository governance harness",
            "url", "https://github.com/ciechanowiec/airness",
            "licenses", "MIT-0",
            "developers", "Herman Ciechanowiec",
            "scmUrl", "https://github.com/ciechanowiec/airness",
            "connection", "scm:git:https://github.com/ciechanowiec/airness.git",
            "developerConnection", "scm:git:git@github.com:ciechanowiec/airness.git"
        )
    );

    private static final Map<String, String> CLEAN = Map.of(
        "name", "Task Tracker",
        "description", "Tracks the tasks of a team.",
        "url", "https://git.sample.org/tracker",
        "inceptionYear", "2025",
        "licenses", "LicenseRef-Proprietary",
        "developers", "Sample Organization",
        "scmUrl", "https://git.sample.org/tracker",
        "connection", "scm:git:https://git.sample.org/tracker.git",
        "developerConnection", "scm:git:https://git.sample.org/tracker.git"
    );

    static ProjectMetadata clean() {
        return metadata(CLEAN);
    }

    static ProjectMetadata with(String key, String value) {
        Map<String, String> values = new HashMap<>(CLEAN);
        values.put(key, value);
        return metadata(values);
    }

    static ProjectMetadata metadata(Map<String, String> values) {
        return new ProjectMetadata(
            values.getOrDefault("name", ""), values.getOrDefault("description", ""), values.getOrDefault("url", ""),
            values.getOrDefault("inceptionYear", ""),
            new ProjectOwnership(
                list(values.getOrDefault("licenses", "")), list(values.getOrDefault("developers", "")), ""
            ),
            new ScmCoordinates(
                values.getOrDefault("scmUrl", ""), values.getOrDefault("connection", ""),
                values.getOrDefault("developerConnection", "")
            )
        );
    }

    static List<Findings> findings(ProjectMetadata declared, ProjectMetadata effective, Optional<Year> firstCommit) {
        return new RootMetadataCheck(
            declared, effective, new MetadataBounds(LICENSES, CURRENT, firstCommit, List.of(AIRNESS))
        ).findings();
    }

    static List<Findings> findings(ProjectMetadata metadata) {
        return findings(metadata, metadata, Optional.of(FIRST_COMMIT));
    }

    private static List<String> list(String values) {
        return values.isEmpty() ? List.of() : Arrays.asList(values.split(","));
    }
}
