package com.example.starter.media;

import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * The bucket every stored file and rendition is written into. Keys are paths of the application's own, so
 * everything one file left behind sits under one prefix and is removed together.
 */
@Component
final class MediaStorage {

    private final S3Client client;

    private final String bucket;

    /**
     * Writes into the bucket the settings name through the given client.
     *
     * @param client   the client of the object store
     * @param settings where the object store is and which bucket the application writes into
     */
    MediaStorage(S3Client client, StorageSettings settings) {
        this.client = client;
        this.bucket = settings.bucket();
    }

    /**
     * Answers the bucket, which the renderer reads stored files from too.
     *
     * @return the name of the bucket
     */
    String bucket() {
        return this.bucket;
    }

    /**
     * Writes the given file under the given key.
     *
     * @param key         where the object is written
     * @param file        what it holds
     * @param contentType the media type it is served as
     */
    void put(String key, Path file, String contentType) {
        this.client.putObject(request -> request.bucket(this.bucket).key(key).contentType(contentType), file);
    }

    /**
     * Writes the given bytes under the given key.
     *
     * @param key         where the object is written
     * @param bytes       what it holds
     * @param contentType the media type it is served as
     */
    void put(String key, byte[] bytes, String contentType) {
        this.client.putObject(
            request -> request.bucket(this.bucket).key(key).contentType(contentType), RequestBody.fromBytes(bytes)
        );
    }

    /**
     * Opens the object under the given key.
     *
     * @param key where the object was written
     * @return its bytes, which the caller closes
     */
    StoredObject open(String key) {
        ResponseInputStream<GetObjectResponse> object = this.client.getObject(
            request -> request.bucket(this.bucket).key(key)
        );
        return new StoredObject(object, object.response().contentLength());
    }

    /**
     * Removes the object under the given key, where there is one.
     *
     * @param key where the object was written
     */
    void remove(String key) {
        this.client.deleteObject(request -> request.bucket(this.bucket).key(key));
    }

    /**
     * Removes every object whose key starts with the given prefix.
     *
     * @param prefix the prefix, ending with a slash so a neighbour sharing its first characters is kept
     */
    void removeUnder(String prefix) {
        this.client.listObjectsV2Paginator(request -> request.bucket(this.bucket).prefix(prefix))
            .stream()
            .map(page -> page.contents().stream().map(S3Object::key).map(this::identified).toList())
            .filter(keys -> !keys.isEmpty())
            .forEach(this::removed);
    }

    private ObjectIdentifier identified(String key) {
        return ObjectIdentifier.builder().key(key).build();
    }

    private void removed(List<ObjectIdentifier> keys) {
        this.client.deleteObjects(
            request -> request.bucket(this.bucket).delete(Delete.builder().objects(keys).quiet(true).build())
        );
    }
}
