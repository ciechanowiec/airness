package com.example.starter.media;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Asks the imgproxy server for a rendition of a stored file, or for a clean copy of it. The server reads the
 * file from the object store itself, so the application never decodes a picture: it names the stored file, the
 * width and the format, and receives the encoded picture. The server turns the picture upright by its
 * orientation tag, keeps none of its metadata, and never enlarges it, so a rendition asked for wider than the
 * picture comes back at the picture's own width, which the server reports in the headers of its answer. A clean
 * copy is the picture at its own size and in its own encoding, which is what a reader downloads in place of the
 * file as it was handed over, since that file carries whatever the camera wrote into it, such as where the
 * picture was taken.
 */
@Component
final class ImageProcessor implements AutoCloseable {

    private static final Duration CONNECTING = Duration.ofSeconds(5);

    private static final Duration RENDERING = Duration.ofSeconds(60);

    private static final String ALGORITHM = "HmacSHA256";

    private static final int OK = 200;

    // A clean copy stands for the file itself, so it is encoded where no loss can be seen.
    private static final int FULL_QUALITY = 90;

    // The encoding the server writes a clean copy in, by the media type of the file it is a copy of.
    private static final Map<String, String> ENCODINGS = Map.of(
        "image/jpeg", "jpg",
        "image/png", "png",
        "image/webp", "webp",
        "image/avif", "avif",
        "image/heic", "heic",
        "image/heif", "heic"
    );

    private final HttpClient client;

    private final ImagingSettings settings;

    private final String bucket;

    /**
     * Renders through the server the settings name, from files in the given bucket.
     *
     * @param settings where the server is and what its addresses are signed with
     * @param storage  which bucket the stored files are in
     */
    ImageProcessor(ImagingSettings settings, StorageSettings storage) {
        this.client = HttpClient.newBuilder().connectTimeout(CONNECTING).build();
        this.settings = settings;
        this.bucket = storage.bucket();
    }

    /**
     * Answers the signature of an imgproxy path: an HMAC-SHA256 of the salt followed by the path, under the
     * key, encoded as unpadded URL-safe {@link Base64}.
     *
     * @param key  the signing key
     * @param salt the signing salt
     * @param path the path to sign, starting with a slash
     * @return the signature
     */
    static String signature(byte[] key, byte[] salt, String path) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            mac.update(salt);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal(path.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("HMAC-SHA256 is missing from this runtime", exception);
        }
    }

    /**
     * Answers the processing path of a rendition: the stored file fitted into the given width, at the
     * format's quality, in that format.
     *
     * @param key    the key of the stored file
     * @param width  the width to fit the picture into
     * @param format the format to encode it in
     * @return the unsigned path
     */
    String path(String key, int width, ImageFormat format) {
        return "/rs:fit:%d:0/q:%d/f:%s/plain/s3://%s/%s".formatted(
            width, format.quality(), format.extension(), this.bucket, key
        );
    }

    /**
     * Answers the processing path of a clean copy: the stored file at its own size, in the encoding it is
     * stored in.
     *
     * @param key         the key of the stored file
     * @param contentType the media type of the stored file
     * @return the unsigned path
     * @throws IllegalArgumentException when the server writes no picture of that media type
     */
    String cleanPath(String key, String contentType) {
        String encoding = Optional.ofNullable(ENCODINGS.get(contentType)).orElseThrow(
            () -> new IllegalArgumentException("No clean copy is written as " + contentType)
        );
        return "/q:%d/f:%s/plain/s3://%s/%s".formatted(FULL_QUALITY, encoding, this.bucket, key);
    }

    /**
     * Renders the stored file under the given key.
     *
     * @param key    the key of the stored file
     * @param width  the width to fit the picture into, which a narrower picture is never enlarged to
     * @param format the format to encode it in
     * @return the rendition with its size
     * @throws IOException when the server cannot be reached or refuses the file
     */
    Rendition render(String key, int width, ImageFormat format) throws IOException {
        HttpResponse<byte[]> response = this.fetched(this.path(key, width, format), key);
        return new Rendition(
            response.body(), dimension(response, "X-Result-Width"), dimension(response, "X-Result-Height")
        );
    }

    /**
     * Writes a clean copy of the stored file under the given key: at its own size and in its own encoding, turned
     * upright, and keeping none of its metadata.
     *
     * @param key         the key of the stored file
     * @param contentType the media type of the stored file, which the copy keeps
     * @return the bytes of the copy
     * @throws IOException when the server cannot be reached or refuses the file
     */
    byte[] cleaned(String key, String contentType) throws IOException {
        return this.fetched(this.cleanPath(key, contentType), key).body();
    }

    @Override
    public void close() {
        this.client.close();
    }

    private HttpResponse<byte[]> fetched(String path, String key) throws IOException {
        HexFormat hex = HexFormat.of();
        String signed = "/" + signature(hex.parseHex(this.settings.key()), hex.parseHex(this.settings.salt()), path)
            + path;
        HttpRequest request = HttpRequest.newBuilder(URI.create(this.settings.url() + signed))
            .timeout(RENDERING)
            .GET()
            .build();
        HttpResponse<byte[]> response = this.send(request);
        if (response.statusCode() != OK) {
            throw new IOException("imgproxy answered %d for %s".formatted(response.statusCode(), key));
        }
        return response;
    }

    private HttpResponse<byte[]> send(HttpRequest request) throws IOException {
        try {
            return this.client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while rendering", exception);
        }
    }

    private static int dimension(HttpResponse<byte[]> response, String header) throws IOException {
        String value = response.headers().firstValue(header).orElseThrow(
            () -> new IOException("imgproxy answered without " + header + "; enable its debug headers")
        );
        return Integer.parseInt(value);
    }
}
