package eu.ciechanowiec.airness.maven;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;

/**
 * One gzipped tar archive the harness downloads, holds to a pinned SHA-256, and unpacks once into a cache
 * that every later build on the machine reuses.
 *
 * <p>The unpacked directory reaches the cache only by an atomic rename from a staging directory beside it,
 * after the hash matched and every entry was written. Its existence is therefore the proof that it is
 * complete, and a build interrupted halfway leaves only a staging directory nothing reads. Two builds that
 * race to install the same archive both verify their own copy, and the one that renames second discards it.
 *
 * <p>The archive's single top-level directory is dropped, so the executables sit at the paths the pins name
 * whatever the archive called its root. An entry that would land outside the unpacked directory, by its
 * name or as the target of a link, fails the installation.
 *
 * @param name   the directory the archive unpacks to inside the cache
 * @param source where the archive is downloaded from
 * @param sha256 the lowercase hexadecimal SHA-256 the downloaded bytes must have
 */
record PinnedArchive(String name, URI source, String sha256) {

    private static final Duration CONNECTION = Duration.ofSeconds(30);
    private static final Duration TRANSFER = Duration.ofMinutes(10);
    private static final int SUCCESS = 200;
    private static final String DIGEST = "SHA-256";
    private static final String EXECUTABLE = "rwxr-xr-x";
    private static final String READABLE = "rw-r--r--";
    // The owner's execute bit of a tar mode, written 0100 in octal.
    private static final int OWNER_EXECUTES = 0x40;
    private static final Set<Byte> REGULAR = Set.of(TarConstants.LF_NORMAL, TarConstants.LF_OLDNORM);

    /**
     * The unpacked archive, installed into the cache first when it is not there yet.
     *
     * @param cache the directory every pinned archive is unpacked into
     * @return the unpacked directory
     * @throws IOException when the download fails, its hash differs from the pin, or an entry cannot be written
     */
    Path unpackedIn(Path cache) throws IOException {
        Path target = cache.resolve(this.name);
        if (Files.isDirectory(target)) {
            return target;
        }
        Files.createDirectories(cache);
        Path staging = Files.createTempDirectory(cache, ".staging-" + this.name + '-');
        try {
            Path archive = staging.resolve("archive");
            this.download(archive);
            this.verify(archive);
            Path unpacked = staging.resolve("unpacked");
            unpack(archive, unpacked);
            publish(unpacked, target);
            return target;
        } finally {
            delete(staging);
        }
    }

    private void download(Path archive) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(this.source).timeout(TRANSFER).GET().build();
        try (
            HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(CONNECTION)
                .build()
        ) {
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(archive));
            if (response.statusCode() != SUCCESS) {
                throw new IOException("Downloading " + this.source + " answered HTTP " + response.statusCode());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading " + this.source, exception);
        }
    }

    private void verify(Path archive) throws IOException {
        String actual = digest(archive);
        if (!this.sha256.equals(actual)) {
            throw new IOException(
                "The archive downloaded from " + this.source + " has SHA-256 " + actual + ", and Airness pins "
                    + this.sha256 + ", so nothing was installed"
            );
        }
    }

    private static String digest(Path archive) throws IOException {
        try (
            DigestInputStream input = new DigestInputStream(
                Files.newInputStream(archive), MessageDigest.getInstance(DIGEST)
            )
        ) {
            input.transferTo(OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(input.getMessageDigest().digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Every Java runtime provides " + DIGEST, exception);
        }
    }

    private static void unpack(Path archive, Path into) throws IOException {
        Files.createDirectories(into);
        try (
            InputStream file = Files.newInputStream(archive);
            TarArchiveInputStream tar = new TarArchiveInputStream(
                new GZIPInputStream(new BufferedInputStream(file))
            )
        ) {
            for (Optional<TarArchiveEntry> entry = next(tar); entry.isPresent(); entry = next(tar)) {
                write(tar, entry.get(), into);
            }
        }
    }

    private static Optional<TarArchiveEntry> next(TarArchiveInputStream tar) throws IOException {
        return Optional.ofNullable(tar.getNextEntry());
    }

    private static void write(InputStream tar, TarArchiveEntry entry, Path into) throws IOException {
        Optional<String> relative = belowRoot(entry.getName());
        if (relative.isPresent()) {
            place(tar, entry, inside(into, into.resolve(relative.get()), entry.getName()), into);
        }
    }

    // The archive's own top-level directory is dropped, and an entry beside it rather than below it, such as
    // a global pax header written out as a file, has no place in the unpacked tree.
    private static Optional<String> belowRoot(String name) {
        int separator = name.indexOf('/');
        return Optional.of(name.substring(separator + 1)).filter(rest -> separator >= 0 && !rest.isEmpty());
    }

    private static void place(InputStream tar, TarArchiveEntry entry, Path destination, Path into) throws IOException {
        if (entry.isDirectory()) {
            Files.createDirectories(destination);
        } else if (entry.isSymbolicLink()) {
            link(entry, destination, into);
        } else {
            copy(tar, entry, destination);
        }
    }

    private static void link(TarArchiveEntry entry, Path destination, Path into) throws IOException {
        Path link = Path.of(entry.getLinkName());
        inside(into, destination.resolveSibling(link), entry.getName());
        Files.createDirectories(destination.resolveSibling(""));
        Files.createSymbolicLink(destination, link);
    }

    private static void copy(InputStream tar, TarArchiveEntry entry, Path destination) throws IOException {
        if (!REGULAR.contains(entry.getLinkFlag())) {
            throw new IOException(
                "The archive entry " + entry.getName() + " is neither a file, a directory nor a symbolic link"
            );
        }
        Files.createDirectories(destination.resolveSibling(""));
        Files.copy(tar, destination);
        String permissions = (entry.getMode() & OWNER_EXECUTES) == 0 ? READABLE : EXECUTABLE;
        Files.setPosixFilePermissions(destination, PosixFilePermissions.fromString(permissions));
    }

    private static Path inside(Path into, Path candidate, String name) throws IOException {
        Path normalized = candidate.normalize();
        if (!normalized.startsWith(into)) {
            throw new IOException("The archive entry " + name + " reaches outside the directory it unpacks into");
        }
        return normalized;
    }

    // Another build may have installed the same archive while this one was staging it. Its copy passed the
    // same verification, so this one is discarded rather than reported.
    private static void publish(Path unpacked, Path target) throws IOException {
        try {
            Files.move(unpacked, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException | DirectoryNotEmptyException exception) {
            if (!Files.isDirectory(target)) {
                throw exception;
            }
        }
    }

    private static void delete(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(PinnedArchive::deleteOne);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not remove the staging directory " + directory, exception);
        }
    }

    private static void deleteOne(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not remove " + path, exception);
        }
    }
}
