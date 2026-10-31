package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.ciechanowiec.airness.governance.Findings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * What one node gate reported for one node project, read from the result file that is the only contract
 * between Maven and the npm packages.
 *
 * <p>The contract holds the schema version, the gate, a title, a status of {@code passed}, {@code failed} or
 * {@code not-applicable}, a summary, the findings and the evidence files the gate wrote beside the result.
 * Nothing about it passes silently: a missing or malformed file fails the gate, as do an unknown schema
 * version, a gate other than the one asked for, a nonzero exit beside a status that is not {@code failed}, a
 * failure that names no finding, a pass that lists some, and evidence the gate did not write.
 *
 * @param findings   the verdict, clean exactly when the gate passed or did not apply and the file was sound
 * @param line       one line for the build log stating what the gate said
 * @param applicable whether the gate judged the project at all, false only for a sound {@code not-applicable}
 */
record NodeGateResult(Findings findings, String line, boolean applicable) {

    /**
     * The one schema version this Airness reads.
     */
    static final int CONTRACT = 1;

    private static final String FAILED = "failed";
    private static final String NOT_APPLICABLE = "not-applicable";
    private static final Set<String> STATUSES = Set.of("passed", FAILED, NOT_APPLICABLE);
    private static final String FINDINGS = "findings";
    private static final String EVIDENCE = "evidence";
    private static final Pattern PLAIN = Pattern.compile("[^/\\\\]+");
    private static final Set<String> RELATIVE = Set.of(".", "..");

    /**
     * Reads the result a gate left for a project.
     *
     * @param gate    the gate that was asked for
     * @param project the node project's key
     * @param result  the result file the gate was told to write
     * @param exit    the gate command's exit code
     * @return the verdict and its log line
     */
    static NodeGateResult read(String gate, String project, Path result, int exit) {
        String subject = "Node gate " + gate + " in " + project;
        return Files.isRegularFile(result)
            ? readFile(gate, subject, result, exit)
            : broken(subject, "wrote no result file at " + result + " and exited " + exit);
    }

    private static NodeGateResult readFile(String gate, String subject, Path result, int exit) {
        try {
            JsonNode document = Optional.ofNullable(new ObjectMapper().readTree(result.toFile()))
                .filter(JsonNode::isObject)
                .orElseThrow(() -> new IOException("it is not a JSON object"));
            return parsed(new ResultSource(gate, subject, result, exit), document);
        } catch (IOException exception) {
            return broken(
                subject, "left a result file " + result + " that cannot be read: " + exception.getMessage()
            );
        }
    }

    /**
     * The evidence files a gate's previous result listed beside it, which a new run must not find left over.
     *
     * <p>Without this, a gate that lists evidence it failed to write would pass on a file an earlier build
     * left behind. A previous result that cannot be read lists nothing.
     *
     * @param result the result file the gate is about to write again
     * @return the evidence files the previous result names
     */
    static List<Path> previousEvidence(Path result) {
        try {
            JsonNode document = new ObjectMapper().readTree(result.toFile());
            return texts(document, EVIDENCE).stream()
                .filter(NodeGateResult::plain)
                .map(result::resolveSibling)
                .toList();
        } catch (IOException _) {
            return List.of();
        }
    }

    // A schema version or a gate other than the one asked for means the fields cannot be trusted to mean what
    // this reader takes them to mean, so nothing else in the file is read.
    private static Optional<String> unread(String gate, JsonNode document) throws IOException {
        JsonNode contract = document.path("contract");
        if (!contract.isInt() || contract.intValue() != CONTRACT) {
            return Optional.of("wrote contract " + contract + ", and this Airness reads contract " + CONTRACT);
        }
        String reported = text(document, "gate");
        return Optional.of("reported the gate " + reported + " rather than " + gate)
            .filter(_ -> !gate.equals(reported));
    }

    private static NodeGateResult parsed(ResultSource reading, JsonNode document) throws IOException {
        Optional<String> unread = unread(reading.gate(), document);
        if (unread.isPresent()) {
            return broken(reading.subject(), unread.get());
        }
        String status = status(document);
        List<String> findings = texts(document, FINDINGS);
        List<String> offences = new ArrayList<>(
            exitContradiction(status, reading.exit()).or(() -> listContradiction(status, findings))
                .map(List::of)
                .orElseGet(() -> FAILED.equals(status) ? findings : List.of())
        );
        evidence(document, reading.result()).forEach(offences::add);
        String summary = text(document, "summary");
        String headline = reading.subject() + ": " + text(document, "title");
        return new NodeGateResult(
            new Findings(headline, offences),
            reading.subject() + ' ' + status.replace('-', ' ') + (summary.isBlank() ? "" : ": " + summary),
            !NOT_APPLICABLE.equals(status)
        );
    }

    private static String status(JsonNode document) throws IOException {
        String status = text(document, "status");
        if (!STATUSES.contains(status)) {
            throw new IOException("status " + status + " is none of " + STATUSES);
        }
        return status;
    }

    private static Optional<String> exitContradiction(String status, int exit) {
        return Optional.of("the gate exited " + exit + " while reporting " + status)
            .filter(_ -> exit != 0 && !FAILED.equals(status));
    }

    // A failure must name what failed, and a pass must name nothing, so a findings list that disagrees with
    // the status is reported rather than believed either way.
    private static Optional<String> listContradiction(String status, List<String> findings) {
        boolean failed = FAILED.equals(status);
        if (failed != findings.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
            failed
                ? "the gate reported failed without naming a finding"
                : "the gate reported " + status + " while listing " + findings.size() + " finding(s)"
        );
    }

    // Evidence is named relative to the result's directory, because that directory is where the repository
    // rules look for it, and a name that leaves it or a file that is not there is a claim nothing backs.
    private static Stream<String> evidence(JsonNode document, Path result) throws IOException {
        Path directory = result.toAbsolutePath().normalize().resolveSibling("");
        return texts(document, EVIDENCE).stream()
            .filter(name -> unbacked(directory, name))
            .map(name -> "the gate lists the evidence " + name + ", which it did not write beside its result");
    }

    private static boolean unbacked(Path directory, String name) {
        return !plain(name) || !Files.isRegularFile(directory.resolve(name));
    }

    // Evidence is a file beside the result, so a name that reaches into another directory, such as that of a
    // project nested below this one, is no evidence of this project.
    private static boolean plain(String name) {
        return PLAIN.matcher(name).matches() && !RELATIVE.contains(name);
    }

    private static String text(JsonNode document, String field) throws IOException {
        JsonNode value = document.path(field);
        if (!value.isTextual()) {
            throw new IOException("the field " + field + " is not text");
        }
        return value.asText();
    }

    private static List<String> texts(JsonNode document, String field) throws IOException {
        JsonNode value = document.path(field);
        if (!value.isArray() || !StreamSupport.stream(value.spliterator(), false).allMatch(JsonNode::isTextual)) {
            throw new IOException("the field " + field + " is not a list of text");
        }
        return StreamSupport.stream(value.spliterator(), false).map(JsonNode::asText).toList();
    }

    private static NodeGateResult broken(String subject, String problem) {
        return new NodeGateResult(
            new Findings(subject + ": a result the contract can read", List.of(problem)), subject + " is broken",
            true
        );
    }

    /**
     * What one reading of a result file is about.
     *
     * @param gate    the gate that was asked for
     * @param subject the gate and project, as every message names them
     * @param result  the result file
     * @param exit    the gate command's exit code
     */
    private record ResultSource(String gate, String subject, Path result, int exit) {
    }
}
