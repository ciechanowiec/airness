package com.example.starter.media;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Where the renditions of a picture are made: an imgproxy server that reads the stored file from the object
 * store and answers it resized and re-encoded. Every address the application asks for is signed with the
 * key and salt both sides share, so nothing else can make the server render.
 *
 * @param url  the address of the imgproxy server
 * @param key  the signing key, in hexadecimal
 * @param salt the signing salt, in hexadecimal
 */
@Validated
@ConfigurationProperties(prefix = "app.images", ignoreUnknownFields = false)
public record ImagingSettings(
    @NotNull URI url,
    @NotBlank @Pattern(regexp = "(?:[0-9a-fA-F]{2})+") String key,
    @NotBlank @Pattern(regexp = "(?:[0-9a-fA-F]{2})+") String salt
) {
}
