package eu.ciechanowiec.airness.maven;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import lombok.SneakyThrows;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;

/**
 * A local HTTP server holding gzipped tar archives, which is where the pinned toolchain downloads from in a
 * test, and the archives themselves, written entry by entry.
 */
final class ArchiveServer implements AutoCloseable {

    private static final int NOT_FOUND = 404;
    private static final int SUCCESS = 200;
    private static final long NO_BODY = -1;

    private final HttpServer server;
    private final Map<String, byte[]> archives;
    private final AtomicInteger requests;
    private final Map<String, Integer> statuses;
    private final List<Runnable> beforeAnswer;

    @SneakyThrows
    ArchiveServer() {
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.archives = new ConcurrentHashMap<>();
        this.requests = new AtomicInteger();
        this.statuses = new ConcurrentHashMap<>();
        this.beforeAnswer = new CopyOnWriteArrayList<>();
        this.server.createContext("/", this::answer);
        this.server.start();
    }

    URI serve(String path, byte[] archive) {
        this.archives.put(path, archive.clone());
        return this.uri(path);
    }

    void refuse(String path, int status) {
        this.statuses.put(path, status);
    }

    // Runs before every answer, which is where a test lets another build finish installing first.
    void beforeAnswer(Runnable action) {
        this.beforeAnswer.add(action);
    }

    URI uri(String path) {
        InetSocketAddress address = this.server.getAddress();
        return URI.create("http://" + address.getHostString() + ':' + address.getPort() + path);
    }

    int requests() {
        return this.requests.get();
    }

    @Override
    public void close() {
        this.server.stop(0);
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static byte[] archive(List<Entry> entries) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new GZIPOutputStream(bytes))) {
            for (Entry entry : entries) {
                entry.writeTo(tar);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return bytes.toByteArray();
    }

    private void answer(HttpExchange exchange) throws IOException {
        this.requests.incrementAndGet();
        this.beforeAnswer.forEach(Runnable::run);
        Optional<byte[]> body = Optional.ofNullable(this.archives.get(exchange.getRequestURI().getPath()));
        if (body.isEmpty()) {
            exchange.sendResponseHeaders(
                this.statuses.getOrDefault(exchange.getRequestURI().getPath(), NOT_FOUND), NO_BODY
            );
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(SUCCESS, body.get().length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body.get());
        }
    }

    /**
     * One archive entry.
     *
     * @param name    the entry name
     * @param flag    the tar type flag
     * @param mode    the permission bits
     * @param content the file content, empty for anything but a file
     * @param link    the link target, empty for anything but a link
     */
    public record Entry(String name, byte flag, int mode, String content, String link) {

        // rwxr-xr-x and rw-r--r--, written 0755 and 0644 in octal.
        private static final int EXECUTABLE = 0x1ED;
        private static final int READABLE = 0x1A4;

        static Entry directory(String name) {
            return new Entry(name, TarConstants.LF_DIR, EXECUTABLE, "", "");
        }

        static Entry file(String name, String content) {
            return new Entry(name, TarConstants.LF_NORMAL, READABLE, content, "");
        }

        static Entry executable(String name, String content) {
            return new Entry(name, TarConstants.LF_NORMAL, EXECUTABLE, content, "");
        }

        static Entry symbolicLink(String name, String target) {
            return new Entry(name, TarConstants.LF_SYMLINK, EXECUTABLE, "", target);
        }

        static Entry hardLink() {
            return new Entry("tool-1.0.0/hard", TarConstants.LF_LINK, READABLE, "", "tool-1.0.0/README");
        }

        void writeTo(TarArchiveOutputStream tar) throws IOException {
            byte[] bytes = this.content.getBytes(StandardCharsets.UTF_8);
            TarArchiveEntry entry = new TarArchiveEntry(this.name, this.flag);
            entry.setMode(this.mode);
            entry.setLinkName(this.link);
            entry.setSize(bytes.length);
            tar.putArchiveEntry(entry);
            tar.write(bytes);
            tar.closeArchiveEntry();
        }
    }
}
