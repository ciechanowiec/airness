package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The findings and actual evaluated files in a complete Checkov report.
 *
 * @param findings   rejected security configurations
 * @param files      files with policy evaluations
 * @param frameworks frameworks with complete scanner evidence
 */
record CheckovReport(List<String> findings, Set<String> files, Set<String> frameworks) {

    private static final String VERSION = "3.3.17";
    private static final String VERSION_FIELD = "checkov_version";
    private static final String SKIPPED = "skipped";
    private static final String PARSING_ERRORS = "parsing_errors";
    private static final String PASSED = "passed";
    private static final String FAILED = "failed";

    CheckovReport {
        findings = List.copyOf(findings);
        files = Set.copyOf(files);
        frameworks = Set.copyOf(frameworks);
    }

    static CheckovReport read(Path file) throws IOException {
        return read(file, "");
    }

    static CheckovReport read(Path file, String framework) throws IOException {
        JsonNode root = ScannerJson.read(file);
        if (root.has(VERSION_FIELD)) {
            return empty(root, framework);
        }
        List<String> findings = new ArrayList<>();
        List<String> files = new ArrayList<>();
        List<String> frameworks = new ArrayList<>();
        List<JsonNode> reports = root.isArray() ? root.valueStream().toList() : List.of(root);
        for (JsonNode report : reports) {
            collect(report, findings, files);
            frameworks.add(ScannerJson.text(report, "check_type"));
        }
        return new CheckovReport(List.copyOf(findings), Set.copyOf(files), Set.copyOf(frameworks));
    }

    private static CheckovReport empty(JsonNode summary, String framework) throws IOException {
        emptyVersion(summary, framework);
        for (String field : List.of(PASSED, FAILED, SKIPPED, PARSING_ERRORS, "resource_count")) {
            if (ScannerJson.integer(summary, field) != 0) {
                throw new IOException("Checkov summary omits nonempty result details: " + field);
            }
        }
        return new CheckovReport(List.of(), Set.of(), Set.of(framework));
    }

    private static void emptyVersion(JsonNode summary, String framework) throws IOException {
        if (
            !CheckovPolicy.frameworks().contains(framework) || !VERSION.equals(ScannerJson.text(summary, VERSION_FIELD))
        ) {
            throw new IOException("An empty Checkov summary requires the pinned version and an explicit framework");
        }
    }

    private static void collect(JsonNode report, List<String> findings, List<String> files) throws IOException {
        String framework = ScannerJson.text(report, "check_type");
        if (!CheckovPolicy.frameworks().contains(framework)) {
            throw new IOException("Unexpected Checkov framework: " + framework);
        }
        JsonNode summary = report.path("summary");
        if (!VERSION.equals(ScannerJson.text(summary, VERSION_FIELD))) {
            throw new IOException("Checkov did not run the pinned version");
        }
        JsonNode results = report.path("results");
        JsonNode passed = ScannerJson.array(results, "passed_checks");
        JsonNode failed = ScannerJson.array(results, "failed_checks");
        requireComplete(results, summary);
        counts(passed, failed, summary);
        for (JsonNode result : failed) {
            findings.add(describe(result));
        }
        Set<String> evaluated = Stream.concat(passed.valueStream(), failed.valueStream())
            .map(result -> result.path("file_path").asText()).collect(Collectors.toUnmodifiableSet());
        files.addAll(evaluated);
    }

    private static void requireComplete(JsonNode results, JsonNode summary) throws IOException {
        for (String field : List.of("skipped_checks", PARSING_ERRORS)) {
            if (!ScannerJson.array(results, field).isEmpty()) {
                throw new IOException("Checkov reported " + field + "; incomplete scans cannot pass");
            }
        }
        zeroCounts(summary);
    }

    private static void counts(JsonNode passed, JsonNode failed, JsonNode summary) throws IOException {
        if (passed.size() != summary.path(PASSED).asInt(-1) || failed.size() != summary.path(FAILED).asInt(-1)) {
            throw new IOException("Checkov result counts disagree with its summary");
        }
    }

    private static String describe(JsonNode result) throws IOException {
        return "%s %s (%s): %s".formatted(
            ScannerJson.text(result, "file_path"), ScannerJson.text(result, "check_id"),
            ScannerJson.text(result, "resource"), ScannerJson.text(result, "check_name")
        );
    }

    private static void zeroCounts(JsonNode summary) throws IOException {
        if (summary.path(SKIPPED).asInt(-1) != 0 || summary.path(PARSING_ERRORS).asInt(-1) != 0) {
            throw new IOException("Checkov did not establish zero skipped checks and parsing errors");
        }
    }
}
