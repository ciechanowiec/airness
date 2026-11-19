package com.example.starter.media;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * The client of the object store. It is told its region and credentials outright rather than left to look
 * for them, because the lookup the SDK otherwise runs reads files in the home directory and asks a metadata
 * address no deployment of this application answers. The store is addressed by path rather than by a host
 * name per bucket, which is how an S3-compatible store such as RustFS serves its buckets, and checksums are
 * sent only where a request needs one, which every such store accepts.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({StorageSettings.class, ImagingSettings.class})
final class MediaConfiguration {

    private static final Duration CONNECTING = Duration.ofSeconds(5);

    private static final Duration READING = Duration.ofSeconds(60);

    @Bean(destroyMethod = "close")
    S3Client objectStore(StorageSettings settings) {
        StorageSettings.StorageCredentials credentials = settings.credentials();
        return S3Client.builder()
            .endpointOverride(settings.endpoint())
            .region(Region.of(settings.region()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(credentials.accessKey(), credentials.secretKey())
                )
            ).forcePathStyle(true)
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
            .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(CONNECTING).socketTimeout(READING))
            .build();
    }
}
