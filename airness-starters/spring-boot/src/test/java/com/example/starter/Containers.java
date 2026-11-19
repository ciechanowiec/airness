package com.example.starter;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The services the suite runs against. The database is the engine a deployment reads, started once for
 * the whole suite and handed to Spring as a service connection. The object store and the renderer are the
 * ones a deployment runs too, on a network of their own, so the renderer reads stored files from the store
 * by its name there, as it does under compose. Each image carries an exact tag, so an upstream release
 * cannot change a verdict while this repository stays as it is, and every credential is drawn afresh for
 * each run.
 */
@UtilityClass
public final class Containers {

    /**
     * The image of the database, which compose.yaml and the verification wrapper run too.
     */
    public static final String DATABASE_IMAGE = "postgres:18-alpine";

    /**
     * The image of the object store, which compose.yaml and the verification wrapper run too.
     */
    public static final String STORAGE_IMAGE = "rustfs/rustfs:1.0.6";

    /**
     * The image of the renderer, which compose.yaml and the verification wrapper run too.
     */
    public static final String IMAGING_IMAGE = "darthsim/imgproxy:v4.0.24";

    // Read by Spring rather than by this file, which is why it is not private.
    @ServiceConnection
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer(DATABASE_IMAGE);

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String BUCKET = "starter";

    private static final String ACCESS = "starter";

    private static final int STORAGE_PORT = 9000;

    private static final int IMAGING_PORT = 8080;

    private static final String SECRET = hex(16);

    private static final String KEY = hex(32);

    private static final String SALT = hex(32);

    private static final Network NETWORK = Network.newNetwork();

    private static final List<String> SETTINGS = List.of(
        "app.storage.endpoint", "app.storage.bucket", "app.storage.region", "app.storage.credentials.access-key",
        "app.storage.credentials.secret-key", "app.images.url", "app.images.key", "app.images.salt"
    );

    // The image's own client creates the bucket, which also proves the store answers.
    private static final GenericContainer<?> OBJECTS = objects();

    private static final GenericContainer<?> IMAGES = images();

    /**
     * Answers the settings that point the application at the object store and the renderer the suite runs,
     * under the names the application reads them by.
     *
     * @return each setting under its name
     */
    public static Map<String, String> mediaSettings() {
        return Map.of(
            "app.storage.endpoint", "http://%s:%d".formatted(OBJECTS.getHost(), OBJECTS.getMappedPort(STORAGE_PORT)),
            "app.storage.bucket", BUCKET,
            "app.storage.region", "us-east-1",
            "app.storage.credentials.access-key", ACCESS,
            "app.storage.credentials.secret-key", SECRET,
            "app.images.url", "http://%s:%d".formatted(IMAGES.getHost(), IMAGES.getMappedPort(IMAGING_PORT)),
            "app.images.key", KEY,
            "app.images.salt", SALT
        );
    }

    // The settings are read once the containers have started, which is after the registry is asked for them.
    @DynamicPropertySource
    static void media(DynamicPropertyRegistry registry) {
        SETTINGS.forEach(name -> registry.add(name, () -> mediaSettings().get(name)));
    }

    private static GenericContainer<?> objects() {
        GenericContainer<?> objects = new GenericContainer<>(DockerImageName.parse(STORAGE_IMAGE));
        objects.withNetwork(NETWORK)
            .withNetworkAliases("objects")
            .withEnv(Map.of("RUSTFS_ACCESS_KEY", ACCESS, "RUSTFS_SECRET_KEY", SECRET))
            .withCommand("/data")
            .withExposedPorts(STORAGE_PORT)
            .waitingFor(
                Wait.forSuccessfulCommand(
                    "RC_CONFIG_DIR=/tmp/rc RC_HOST_local=\"http://$RUSTFS_ACCESS_KEY:$RUSTFS_SECRET_KEY"
                        + "@127.0.0.1:9000\" rc bucket create local/" + BUCKET + " --ignore-existing"
                )
            );
        return objects;
    }

    private static GenericContainer<?> images() {
        GenericContainer<?> images = new GenericContainer<>(DockerImageName.parse(IMAGING_IMAGE));
        images.withNetwork(NETWORK)
            .withNetworkAliases("images")
            .withEnv(
                Map.ofEntries(
                    Map.entry("IMGPROXY_KEY", KEY),
                    Map.entry("IMGPROXY_SALT", SALT),
                    Map.entry("IMGPROXY_USE_S3", "true"),
                    Map.entry("IMGPROXY_S3_ENDPOINT", "http://objects:" + STORAGE_PORT),
                    Map.entry("IMGPROXY_S3_REGION", "us-east-1"),
                    Map.entry("IMGPROXY_S3_ALLOWED_BUCKETS", BUCKET),
                    Map.entry("AWS_ACCESS_KEY_ID", ACCESS),
                    Map.entry("AWS_SECRET_ACCESS_KEY", SECRET),
                    Map.entry("IMGPROXY_ALLOWED_SOURCES", "s3://"),
                    Map.entry("IMGPROXY_KEEP_COPYRIGHT", "false"),
                    Map.entry("IMGPROXY_MAX_SRC_FILE_SIZE", "26214400"),
                    Map.entry("IMGPROXY_ENABLE_DEBUG_HEADERS", "true")
                )
            ).withExposedPorts(IMAGING_PORT)
            .dependsOn(OBJECTS)
            .waitingFor(Wait.forHttp("/health"));
        return images;
    }

    private static String hex(int bytes) {
        byte[] drawn = new byte[bytes];
        RANDOM.nextBytes(drawn);
        return HexFormat.of().formatHex(drawn);
    }
}
