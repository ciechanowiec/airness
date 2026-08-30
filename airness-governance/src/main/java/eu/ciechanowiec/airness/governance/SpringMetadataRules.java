package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Reports the configuration keys the container will not read, judged against what it says it reads.
 *
 * <p>Three rules sit here and they share one shape: a key the project wrote, and a value that therefore
 * decides nothing. A key the supplier has stopped binding, a key the supplier still binds but has
 * advised against, and a key nothing on the classpath declares are the same defect met at three ages.
 * None of them fails at startup, none appears in a log, and the line reads as though it were in force.
 *
 * <p>That is why these are refused rather than warned about. The guideline already bans a call to an API
 * its supplier marks deprecated, and a configuration key is such an API. The compiler enforces that ban
 * for Java and had no counterpart here, so a project could not write the deprecated method and could
 * write the deprecated key beside it.
 *
 * <p>A classpath that published no metadata is itself a finding. Every answer this gives would otherwise
 * be that nothing is wrong, which is what a check with nothing to read always reports and never means.
 */
@UtilityClass
final class SpringMetadataRules {

    private static final int OPENING = 1;

    /**
     * Every key the container has stopped binding.
     *
     * @param path          the repository-relative path, which every offence names
     * @param configuration the file already read
     * @param metadata      what the classpath declares
     * @return one offence per unbound key
     */
    static List<String> unbound(String path, SpringConfiguration configuration, SpringMetadata metadata) {
        return withdrawn(path, configuration, metadata, true);
    }

    /**
     * Every key the container still binds and its supplier has advised against.
     *
     * @param path          the repository-relative path, which every offence names
     * @param configuration the file already read
     * @param metadata      what the classpath declares
     * @return one offence per deprecated key
     */
    static List<String> deprecated(
        String path, SpringConfiguration configuration, SpringMetadata metadata
    ) {
        return withdrawn(path, configuration, metadata, false);
    }

    /**
     * Every key that no declaration on the classpath accounts for.
     *
     * @param path          the repository-relative path, which every offence names
     * @param configuration the file already read
     * @param metadata      what the classpath declares
     * @return one offence per unaccounted key
     */
    static List<String> unknown(String path, SpringConfiguration configuration, SpringMetadata metadata) {
        if (metadata.empty()) {
            return List.of();
        }
        return configuration.settings().stream()
            .filter(setting -> !metadata.known(setting.key()))
            .flatMap(setting -> unaccounted(path, setting, metadata))
            .toList();
    }

    /**
     * Whether the goal had anything to judge the file against.
     *
     * @param path          the repository-relative path, which every offence names
     * @param configuration the file already read
     * @param metadata      what the classpath declares
     * @return the one offence a silent classpath carries, or nothing
     */
    static List<String> unread(String path, SpringConfiguration configuration, SpringMetadata metadata) {
        boolean judged = !metadata.empty() || configuration.settings().isEmpty();
        return judged
            ? List.of()
            : List.of(
                offence(
                    path, OPENING,
                    "no dependency on the compile classpath published a spring-configuration-metadata.json,"
                        + " so no key in this file was read against what the container binds; a settings file"
                        + " that nothing can account for is not a settings file known to be right"
                )
            );
    }

    private static List<String> withdrawn(
        String path, SpringConfiguration configuration, SpringMetadata metadata, boolean gone
    ) {
        return configuration.settings().stream()
            .flatMap(setting -> stated(path, setting, metadata, gone))
            .toList();
    }

    private static Stream<String> stated(
        String path, SpringConfiguration.Setting setting, SpringMetadata metadata, boolean gone
    ) {
        return metadata.deprecation(setting.key()).stream()
            .filter(deprecation -> deprecation.unbound() == gone)
            .map(deprecation -> offence(path, setting.line(), consequence(setting, deprecation, gone)));
    }

    private static String consequence(
        SpringConfiguration.Setting setting, ConfigurationProperty.Deprecation deprecation, boolean gone
    ) {
        String stated = gone
            ? " names a setting the container has stopped binding, so the value written here is read by"
                + " nothing and the line only reads as though it were in force"
            : " names a setting its supplier has deprecated, so the release that withdraws it will stop"
                + " reading this line without saying so";
        return setting.raw() + stated + when(deprecation) + remedy(deprecation);
    }

    private static String when(ConfigurationProperty.Deprecation deprecation) {
        return deprecation.since().isEmpty()
            ? ""
            : ", as of " + deprecation.since();
    }

    private static String remedy(ConfigurationProperty.Deprecation deprecation) {
        if (!deprecation.replacement().isEmpty()) {
            return "; write " + deprecation.replacement() + " instead";
        }
        return deprecation.reason().isEmpty()
            ? "; nothing was put in its place, so remove the line"
            : "; " + deprecation.reason();
    }

    private static Stream<String> unaccounted(
        String path, SpringConfiguration.Setting setting, SpringMetadata metadata
    ) {
        return metadata.anchor(setting.key()).stream()
            .map(
                anchor -> offence(
                    path, setting.line(), setting.raw()
                        + " is not a setting anything on the classpath declares, and " + anchor
                        + " is declared and knows no such key, so nothing binds this line"
                )
            );
    }

    private static String offence(String path, int line, String consequence) {
        return path + ": line " + line + ": " + consequence;
    }
}
