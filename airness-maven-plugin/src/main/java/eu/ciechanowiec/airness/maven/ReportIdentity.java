package eu.ciechanowiec.airness.maven;

import java.util.Map;

/**
 * The project name and exact tool identities recorded in the report.
 *
 * @param name  root artifact identifier
 * @param tools harness and renderer versions
 */
record ReportIdentity(String name, Map<String, String> tools) {

    ReportIdentity {
        tools = Map.copyOf(tools);
    }
}
