package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * A current application context's assessment, without translated values.
 *
 * @param application the production application
 * @param context     the context and profile label
 * @param status      the assessment kind
 * @param detail      the count, reference or reason
 */
record TemplateMessageEvidence(String application, String context, String status, String detail) {

    private static final Set<String> STATUSES = Set.of("assessed", "missing", "unassessed", "error");

    static List<TemplateMessageEvidence> read(Path path, TemplateMessageInputs inputs) {
        String prefix = "messages " + inputs.started() + " " + inputs.digest() + " ";
        try {
            return Files.isRegularFile(path) ? Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                .filter(line -> line.startsWith(prefix)).map(line -> parse(line.substring(prefix.length())))
                .filter(result -> inputs.applications().contains(result.application())).toList() : List.of();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read message evidence " + path, exception);
        }
    }

    boolean complete() {
        return "assessed".equals(this.status) || "unassessed".equals(this.status);
    }

    int checked() {
        return "assessed".equals(this.status) ? Integer.parseInt(this.detail) : 0;
    }

    private static TemplateMessageEvidence parse(String payload) {
        String[] parts = payload.split(" ", -1);
        if (parts.length != 4 || !STATUSES.contains(parts[2])) {
            throw new IllegalArgumentException("Malformed current template message evidence");
        }
        return new TemplateMessageEvidence(decode(parts[0]), decode(parts[1]), parts[2], decode(parts[3]));
    }

    private static String decode(String encoded) {
        return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
    }
}
