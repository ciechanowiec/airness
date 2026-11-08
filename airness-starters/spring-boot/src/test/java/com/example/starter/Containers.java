package com.example.starter;

import lombok.experimental.UtilityClass;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The services the suite runs against. The database is the engine a deployment reads, started once for
 * the whole suite and handed to Spring as a service connection. The image carries an exact tag, so an
 * upstream release cannot change a verdict while this repository stays as it is.
 */
@UtilityClass
public final class Containers {

    /**
     * The image of the database, which compose.yaml and the verification wrapper run too.
     */
    public static final String DATABASE_IMAGE = "postgres:18-alpine";

    // Read by Spring rather than by this file, which is why it is not private.
    @ServiceConnection
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer(DATABASE_IMAGE);
}
