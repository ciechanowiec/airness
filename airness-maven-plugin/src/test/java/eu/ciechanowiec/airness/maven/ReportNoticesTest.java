package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportNoticesTest {

    private static final int OVERSIZED = 262_145;
    private static final byte INVALID_UTF8 = (byte) 0xFF;

    @TempDir
    private Path directory;

    @Test
    @SneakyThrows
    void readsWarNoticesAndIgnoresDirectoryEntries() {
        Path jar = this.archive(Map.of("LICENSE/", new byte[0], "NOTICE", bytes("war notice")));
        Path war = Files.copy(jar, this.directory.resolve("application.war"));
        assertEquals(Map.of("NOTICE", "war notice"), ReportNotices.read(war));
    }

    @Test
    void readsOnlyTextualLicenseAndNoticeEntries() {
        Path jar = this.archive(
            Map.of(
                "META-INF/LICENSE", bytes("Published license"), "NOTICE.txt", bytes("Notice"),
                "license.key", bytes("private material"), "LICENSE-secret.txt", bytes("private material"),
                "../LICENSE", bytes("unsafe path"), "COPYING", new byte[] {0, 1}
            )
        );
        assertEquals(Map.of("META-INF/LICENSE", "Published license", "NOTICE.txt", "Notice"), ReportNotices.read(jar));
    }

    @Test
    @SneakyThrows
    void refusesExternalLinksAndIgnoresNonArchives() {
        Path plain = Files.writeString(this.directory.resolve("library.pom"), "<project/>");
        assertEquals(Map.of(), ReportNotices.read(plain));
        Path link = Files.createSymbolicLink(this.directory.resolve("linked.jar"), plain);
        assertThrows(IllegalStateException.class, () -> ReportNotices.read(link));
        assertThrows(IllegalStateException.class, () -> ReportNotices.read(this.directory.resolve("missing.jar")));
    }

    @Test
    void omitsOversizedAndUndecodableEntries() {
        Path jar = this.archive(Map.of("LICENSE", new byte[OVERSIZED], "NOTICE", new byte[] {INVALID_UTF8}));
        assertEquals(Map.of(), ReportNotices.read(jar));
    }

    @SneakyThrows
    private Path archive(Map<String, byte[]> entries) {
        Path file = this.directory.resolve("library.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                write(zip, entry);
            }
        }
        return file;
    }

    @SneakyThrows
    private static void write(ZipOutputStream zip, Map.Entry<String, byte[]> entry) {
        zip.putNextEntry(new ZipEntry(entry.getKey()));
        zip.write(entry.getValue());
        zip.closeEntry();
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
