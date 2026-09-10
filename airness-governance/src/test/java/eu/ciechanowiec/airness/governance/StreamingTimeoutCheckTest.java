package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StreamingTimeoutCheckTest {

    private static final String APPLICATION = "example.Application";
    private static final StreamingInput DECLARATION = new StreamingInput(
        "type", "example.Options", "file:/classes/", "timeout"
    );
    @TempDir
    private Path directory;

    @Test
    @SneakyThrows
    void acceptsCurrentEvidenceAndKeepsEveryFailure() {
        StreamingTimeoutInputs inputs = inputs(1);
        Path evidence = this.directory.resolve("context.evidence");
        inputs.write(StreamingTimeoutInputs.beside(evidence));
        Files.write(evidence, List.of(line(inputs, "assessed", "1")));
        assertTrue(Verdicts.clean(check(inputs, evidence).findings()));
        Files.write(
            evidence, List.of(
                line(inputs, "assessed", "1"), line(inputs, "missing", "download: missing policy"),
                line(inputs, "unsupported", "custom adapter"), line(inputs, "inherited", "container default"),
                line(inputs, "error", "unreadable field"), line(inputs, "assessed", "1")
            )
        );
        StreamingTimeoutCheck check = check(inputs, evidence);
        assertFalse(Verdicts.clean(check.findings()));
        assertEquals(2, check.assessed());
        assertTrue(Verdicts.offences(check.findings(), "cannot be verified").getFirst().contains(APPLICATION));
    }

    @Test
    @SneakyThrows
    void rejectsMissingStaleAndChangedInputs() {
        StreamingTimeoutInputs inputs = inputs(2);
        Path evidence = this.directory.resolve("context.evidence");
        assertFalse(Verdicts.clean(check(inputs, evidence).findings()));
        inputs.write(StreamingTimeoutInputs.beside(evidence));
        assertFalse(Verdicts.clean(check(inputs, evidence).findings()));
        Files.write(evidence, List.of(line(inputs(1), "assessed", "0")));
        assertFalse(Verdicts.clean(check(inputs, evidence).findings()));
        Files.write(evidence, List.of(line(inputs, "assessed", "0")));
        StreamingTimeoutInputs changed = new StreamingTimeoutInputs(2, inputs.applications(), List.of());
        assertNotEquals(inputs.digest(), changed.digest());
        changed.write(StreamingTimeoutInputs.beside(evidence));
        assertFalse(Verdicts.clean(check(inputs, evidence).findings()));
    }

    @Test
    @SneakyThrows
    void malformedCurrentEvidenceIsAnError() {
        StreamingTimeoutInputs inputs = inputs(1);
        Path evidence = this.directory.resolve("context.evidence");
        inputs.write(StreamingTimeoutInputs.beside(evidence));
        Files.writeString(evidence, "streaming 1 " + inputs.digest() + " invalid\n");
        assertThrows(IllegalArgumentException.class, () -> check(inputs, evidence));
        Files.writeString(evidence, line(inputs, "assessed", "-1"));
        assertThrows(IllegalArgumentException.class, () -> check(inputs, evidence));
    }

    @Test
    void unrelatedModulesNeedNoStreamingEvidence() {
        StreamingTimeoutInputs inputs = new StreamingTimeoutInputs(1, List.of(), List.of());
        assertTrue(Verdicts.clean(check(inputs, this.directory.resolve("missing")).findings()));
    }

    @Test
    @SneakyThrows
    void refusesToHideAnUnwritableManifest() {
        Path blocker = this.directory.resolve("file");
        Files.writeString(blocker, "not a directory");
        StreamingTimeoutInputs inputs = inputs(1);
        assertThrows(UncheckedIOException.class, () -> inputs.write(blocker.resolve("manifest")));
    }

    @Test
    void canonicalizesInventoryWithoutRetainingMutableInputs() {
        StreamingTimeoutInputs first = new StreamingTimeoutInputs(
            1, List.of(APPLICATION, APPLICATION),
            List.of(DECLARATION, DECLARATION)
        );
        assertEquals(inputs(1).digest(), first.digest());
        assertThrows(UnsupportedOperationException.class, () -> first.applications().add("other"));
        assertThrows(UnsupportedOperationException.class, () -> first.declarations().clear());
    }

    private static StreamingTimeoutInputs inputs(long started) {
        return new StreamingTimeoutInputs(started, List.of(APPLICATION), List.of(DECLARATION));
    }

    private static StreamingTimeoutCheck check(StreamingTimeoutInputs inputs, Path evidence) {
        return new StreamingTimeoutCheck(inputs, StreamingTimeoutInputs.beside(evidence), evidence);
    }

    private static String line(StreamingTimeoutInputs inputs, String status, String detail) {
        return "streaming " + inputs.started() + " " + inputs.digest() + " " + encoded(APPLICATION)
            + " " + encoded("context") + " " + status + " " + encoded(detail);
    }

    private static String encoded(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
