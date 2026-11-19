package com.example.starter.media;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * A file somebody handed over, read once into a temporary file, which is removed when the spool is closed.
 * The spool knows how many bytes the file holds, their SHA-256, and what their first bytes say they are.
 */
final class SpooledUpload implements AutoCloseable {

    private final Path file;

    private SpooledUpload(Path file) {
        this.file = file;
    }

    /**
     * Reads the given file into a temporary file of its own.
     *
     * @param upload the file somebody handed over
     * @return the spool, which the caller closes
     * @throws UncheckedIOException when the file cannot be read or the temporary file written
     */
    static SpooledUpload of(Upload upload) {
        try (InputStream content = upload.source().getInputStream()) {
            Path file = Files.createTempFile("media-", ".upload");
            Files.copy(content, file, StandardCopyOption.REPLACE_EXISTING);
            return new SpooledUpload(file);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * Answers the temporary file the upload was read into.
     *
     * @return its path
     */
    Path file() {
        return this.file;
    }

    /**
     * Answers how many bytes the file holds.
     *
     * @return the count
     * @throws IOException when the temporary file cannot be read
     */
    long size() throws IOException {
        return Files.size(this.file);
    }

    /**
     * Answers what the first bytes of the file say it is.
     *
     * @return what the file is, and nothing where it is none of the kinds the application draws
     * @throws IOException when the temporary file cannot be read
     */
    Optional<Detected> detected() throws IOException {
        try (InputStream content = Files.newInputStream(this.file)) {
            return MediaTypes.of(content.readNBytes(MediaTypes.HEAD));
        }
    }

    /**
     * Answers the SHA-256 of the bytes.
     *
     * @return the digest in lowercase hexadecimal
     * @throws IOException when the temporary file cannot be read
     */
    String sha256() throws IOException {
        MessageDigest digest = digester();
        try (InputStream content = new DigestInputStream(Files.newInputStream(this.file), digest)) {
            content.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Answers what the file is, under the name it was handed over under.
     *
     * @param name the name the file was handed over under, kept to the length the column holds and named
     *             {@code file} where it is blank
     * @return what the file is
     * @throws IOException           when the temporary file cannot be read
     * @throws MediaRefusedException when the file is empty, too large or none of the kinds the application draws
     */
    MediaFile described(String name) throws IOException {
        long size = this.size();
        refuseIf(size == 0, MediaRefusedException.Reason.EMPTY);
        refuseIf(size > MediaLibrary.MAX_BYTES, MediaRefusedException.Reason.TOO_LARGE);
        Detected detected = this.detected().orElseThrow(
            () -> new MediaRefusedException(MediaRefusedException.Reason.UNSUPPORTED)
        );
        return new MediaFile(trimmed(name), detected.kind(), detected.contentType(), size, this.sha256());
    }

    /**
     * Draws the first page of the file, which is a PDF.
     *
     * @return the picture of its first page, and nothing where the document cannot be read
     */
    Optional<byte[]> firstPage() {
        return PdfPages.firstPage(this.file);
    }

    /**
     * Removes the temporary file.
     *
     * @throws UncheckedIOException when the temporary file cannot be removed
     */
    @Override
    public void close() {
        try {
            Files.deleteIfExists(this.file);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void refuseIf(boolean refused, MediaRefusedException.Reason reason) {
        if (refused) {
            throw new MediaRefusedException(reason);
        }
    }

    private static String trimmed(String name) {
        String plain = name.strip();
        String named = plain.isEmpty() ? "file" : plain;
        return named.length() > MediaFile.NAME ? named.substring(0, MediaFile.NAME) : named;
    }

    private static MessageDigest digester() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is missing from this runtime", exception);
        }
    }
}
