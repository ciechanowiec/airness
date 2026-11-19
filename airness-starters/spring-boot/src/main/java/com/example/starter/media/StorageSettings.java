package com.example.starter.media;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Where the bytes of every stored file live: an S3-compatible object store, such as RustFS, reached at an
 * endpoint of the deployment's own with credentials of its own. Each value comes from the environment, and a
 * start that is missing one fails rather than writing files somewhere else.
 *
 * @param endpoint    the address of the object store
 * @param bucket      the bucket every file is written into
 * @param region      the region the store answers for, which an S3-compatible store accepts as given
 * @param credentials what the application signs its requests with
 */
@Validated
@ConfigurationProperties(prefix = "app.storage", ignoreUnknownFields = false)
public record StorageSettings(
    @NotNull URI endpoint,
    @NotBlank String bucket,
    @NotBlank String region,
    @NotNull @Valid StorageCredentials credentials
) {

    /**
     * The key pair the application signs its requests to the object store with.
     *
     * @param accessKey the public half
     * @param secretKey the secret half
     */
    public record StorageCredentials(@NotBlank String accessKey, @NotBlank String secretKey) {
    }
}
