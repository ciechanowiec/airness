package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

record StreamingTimeoutEvidence(String application, String context, String status, String detail) {

    private static final String ASSESSED = "assessed";
    private static final String COUNT = "0|[1-9][0-9]*";
    private static final Set<String> STATUSES = Set.of("assessed", "missing", "inherited", "unsupported", "error");

    static List<StreamingTimeoutEvidence> read(Path path, StreamingTimeoutInputs inputs) {
        if (!Files.isRegularFile(path)) {
            return List.of();
        }
        String prefix = "streaming " + inputs.started() + " " + inputs.digest() + " ";
        try (Stream<String> lines = Files.lines(path)) {
            return lines.filter(line -> line.startsWith(prefix))
                .map(line -> parse(line.substring(prefix.length())))
                .filter(result -> inputs.applications().contains(result.application())).toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read streaming timeout evidence", exception);
        }
    }

    private static StreamingTimeoutEvidence parse(String line) {
        String[] fields = line.split(" ", -1);
        if (fields.length != 4 || !STATUSES.contains(fields[2])) {
            throw new IllegalArgumentException("Malformed current streaming timeout evidence");
        }
        String detail = decode(fields[3]);
        if (ASSESSED.equals(fields[2]) && !detail.matches(COUNT)) {
            throw new IllegalArgumentException("Malformed streaming endpoint count");
        }
        return new StreamingTimeoutEvidence(decode(fields[0]), decode(fields[1]), fields[2], detail);
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
