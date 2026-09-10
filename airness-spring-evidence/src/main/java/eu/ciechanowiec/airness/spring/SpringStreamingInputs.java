package eu.ciechanowiec.airness.spring;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.List;
import java.util.Optional;

record SpringStreamingInputs(
    long started, String digest, List<String> applications, List<SpringStreamingInput> declarations
) {

    private static final String FINGERPRINT = "[0-9a-f]{64}";

    SpringStreamingInputs {
        applications = List.copyOf(applications);
        declarations = List.copyOf(declarations);
    }

    static SpringStreamingInputs read(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            String digest = single(lines, "digest ");
            if (!digest.matches(FINGERPRINT)) {
                throw new IllegalArgumentException("Malformed streaming input fingerprint");
            }
            return new SpringStreamingInputs(
                Long.parseLong(single(lines, "started ")), digest,
                values(lines, "application "), values(lines, "input ").stream().map(SpringStreamingInput::parse)
                    .toList()
            );
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read streaming production inputs", exception);
        }
    }

    boolean owns(Class<?> type) {
        return origin(type).map(
            address -> this.declarations.stream()
                .anyMatch(input -> "type".equals(input.kind()) && input.at(address))
        ).orElse(false);
    }

    Optional<SpringStreamingInput> declared(Class<?> type) {
        return origin(type).flatMap(
            address -> this.declarations.stream()
                .filter(
                    input -> "type".equals(input.kind()) && input.name().equals(type.getName()) && input.at(address)
                )
                .findFirst()
        );
    }

    private static Optional<String> origin(Class<?> type) {
        return Optional.ofNullable(type.getProtectionDomain().getCodeSource()).map(CodeSource::getLocation)
            .map(URL::toExternalForm);
    }

    private static List<String> values(List<String> lines, String prefix) {
        return lines.stream().filter(line -> line.startsWith(prefix)).map(line -> line.substring(prefix.length()))
            .toList();
    }

    private static String single(List<String> lines, String prefix) {
        List<String> found = values(lines, prefix);
        if (found.size() != 1) {
            throw new IllegalArgumentException("Streaming inputs require one " + prefix.strip());
        }
        return found.getFirst();
    }
}
