package eu.ciechanowiec.airness.governance;

import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * The shared start of a secret scan configuration, and the questions a test asks of what it reports.
 */
@UtilityClass
final class SecretScanFixtures {

    /**
     * A configuration that runs the shared rule set and holds no exception.
     */
    static final String SHARED = """
        title = "Secret scan configuration"

        [extend]
        useDefault = true
        """;

    /**
     * Whether any offence the configuration is reported for contains a fragment.
     *
     * @param content  the configuration
     * @param fragment a distinguishing part of the offence
     * @return whether it was reported
     */
    static boolean reported(String content, CharSequence fragment) {
        return String.join("\n", broken(content)).contains(fragment);
    }

    /**
     * The reports of every rule the configuration breaks.
     *
     * @param content the configuration
     * @return one report per broken rule
     */
    static List<String> broken(String content) {
        return new SecretScanConfiguration(content).findings().stream()
            .filter(verdict -> !verdict.clean())
            .map(Findings::report)
            .toList();
    }
}
