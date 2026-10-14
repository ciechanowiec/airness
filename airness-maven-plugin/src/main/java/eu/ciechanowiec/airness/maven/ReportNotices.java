package eu.ciechanowiec.airness.maven;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Bounded textual license entries read inside an archive without extracting paths.
 */
@UtilityClass
final class ReportNotices {

    private static final List<String> ARCHIVES = List.of(".jar", ".war");
    private static final int MAX_BYTES = 262_144;
    private static final Pattern NAME = Pattern.compile(
        "(?i)(?:.*/)?(?:licen[cs]e|copying|notice)(?:-[a-z0-9]{1,20}){0,3}"
            + "(?:\\.(?:txt|md|markdown|rst|mit|bsd|apache))?"
    );
    private static final Pattern PRIVATE = Pattern.compile(
        "(?i).*?(?:key|secret|token|private|password|credential|cert).*?"
    );

    @SneakyThrows
    static Map<String, String> read(Path archive) {
        if (!Files.isRegularFile(archive) || Files.isSymbolicLink(archive)) {
            throw new IllegalStateException("Unreadable dependency archive: " + archive);
        }
        String filename = archive.toString();
        if (ARCHIVES.stream().noneMatch(filename::endsWith)) {
            return Map.of();
        }
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            return zip.stream().filter(ReportNotices::notice)
                .flatMap(entry -> text(zip, entry).stream().map(value -> Map.entry(entry.getName(), value)))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }

    private static boolean notice(ZipEntry entry) {
        String name = entry.getName();
        boolean candidate = !entry.isDirectory() && entry.getSize() <= MAX_BYTES && NAME.matcher(name).matches();
        return candidate && safe(name);
    }

    private static boolean safe(String name) {
        return !PRIVATE.matcher(name).matches() && !name.contains("..") && !name.startsWith("/");
    }

    @SneakyThrows
    private static Optional<String> text(ZipFile zip, ZipEntry entry) {
        try (InputStream input = zip.getInputStream(entry)) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) {
                return Optional.empty();
            }
            return decoded(bytes);
        }
    }

    private static Optional<String> decoded(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            return text.indexOf('\0') >= 0 ? Optional.empty() : Optional.of(text);
        } catch (CharacterCodingException _) {
            return Optional.empty();
        }
    }
}
