package eu.ciechanowiec.airness.spring;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The manifest of this Maven invocation, never application configuration.
 *
 * @param started      the Maven start time
 * @param digest       the reference fingerprint
 * @param applications the production application identities
 * @param references   the source references
 */
record SpringMessageInputs(
    long started, String digest, List<String> applications, List<SpringMessageInput> references
) {

    private static final String DIGEST_PATTERN = "[0-9a-f]{64}";

    SpringMessageInputs {
        applications = List.copyOf(applications);
        references = List.copyOf(references);
    }

    static SpringMessageInputs read(Path path) {
        try {
            return parse(Files.readAllLines(path, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read template message inputs " + path, exception);
        }
    }

    private static SpringMessageInputs parse(List<String> lines) {
        String digest = single(lines, "digest ");
        if (!digest.matches(DIGEST_PATTERN)) {
            throw new IllegalArgumentException("Invalid template message fingerprint");
        }
        return new SpringMessageInputs(
            Long.parseLong(single(lines, "started ")), digest,
            values(lines, "application "), values(lines, "reference ").stream().map(SpringMessageInput::parse).toList()
        );
    }

    private static String single(List<String> lines, String prefix) {
        List<String> values = values(lines, prefix);
        if (values.size() != 1) {
            throw new IllegalArgumentException("Template message manifest must declare exactly one " + prefix.strip());
        }
        return values.getFirst();
    }

    private static List<String> values(List<String> lines, String prefix) {
        return lines.stream().filter(line -> line.startsWith(prefix)).map(line -> line.substring(prefix.length()))
            .toList();
    }
}
