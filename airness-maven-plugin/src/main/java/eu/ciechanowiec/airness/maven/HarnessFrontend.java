package eu.ciechanowiec.airness.maven;

import java.util.Map;
import lombok.experimental.UtilityClass;

/**
 * The authored frontend files of the harness's own modules that no node project can check, each with the
 * reason, which the frontend inventory excuses only in the module that ships them.
 *
 * <p>The table is part of the harness rather than a setting, so a consumer cannot add to it. Each entry is
 * held to its file: an entry whose file is gone, or which a node project now checks, is stale and refused.
 */
@UtilityClass
final class HarnessFrontend {

    private static final String REPORT = "src/main/resources/eu/ciechanowiec/airness/compliance/";
    private static final String CYCLE = "the compliance report ships it inside airness-governance, which the "
        + "maven plugin needs before airness-node, the package its node checks would run, can be built";
    private static final Map<String, Map<String, String>> EXCUSED = Map.of(
        "eu.ciechanowiec:airness-governance", Map.of(
            REPORT + "report.css", CYCLE,
            REPORT + "report.js", CYCLE
        )
    );

    /**
     * The files excused in one module.
     *
     * @param groupId    the module's group
     * @param artifactId the module's artifact
     * @return each module-relative path with its reason, and nothing for any module but the harness's own
     */
    static Map<String, String> excused(String groupId, String artifactId) {
        return EXCUSED.getOrDefault(groupId + ':' + artifactId, Map.of());
    }
}
