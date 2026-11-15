package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The base a shipped image is built on is the registry image beneath the last stage, however many stages it
 * passes through and however its version is stated.
 */
class DockerfileBaseTest {

    private static final String NODE = "node:26.8.2-alpine@sha256:" + "a".repeat(64);

    @Test
    void readsTheImageASingleStageStartsFrom() {
        assertEquals(Optional.of(NODE), DockerfileBase.of("FROM " + NODE + "\nCMD [\"node\"]\n"));
    }

    @Test
    void readsTheImageTheLastStageStartsFromRatherThanTheFirst() {
        String dockerfile = """
            FROM maven:3.9-eclipse-temurin-25 AS build
            RUN mvn package
            FROM eclipse-temurin:25-jre-alpine
            COPY --from=build /target/app.jar /app.jar
            """;
        assertEquals(Optional.of("eclipse-temurin:25-jre-alpine"), DockerfileBase.of(dockerfile));
    }

    @Test
    void followsTheLastStageThroughEveryStageItNames() {
        String dockerfile = """
            FROM %s AS base
            FROM base AS deps
            RUN pnpm install
            FROM Deps AS runtime
            CMD ["node", "server.js"]
            """.formatted(NODE);
        assertEquals(Optional.of(NODE), DockerfileBase.of(dockerfile));
    }

    @Test
    void substitutesTheArgumentDefaultsAReferenceUses() {
        String braced = "ARG NODE_VERSION=26.8.2\nFROM node:${NODE_VERSION}-alpine\n";
        String bare = "ARG TAG=3.24\nFROM alpine:$TAG\n";
        String unset = "ARG NODE_VERSION\nFROM node:${NODE_VERSION}\n";
        assertAll(
            () -> assertEquals(Optional.of("node:26.8.2-alpine"), DockerfileBase.of(braced)),
            () -> assertEquals(Optional.of("alpine:3.24"), DockerfileBase.of(bare)),
            () -> assertEquals(
                Optional.of("node:${NODE_VERSION}"), DockerfileBase.of(unset),
                "a variable no default gives is returned as written, for the caller to refuse"
            )
        );
    }

    @Test
    void readsTheReferencePastAPlatformOption() {
        assertEquals(
            Optional.of("alpine:3.24"), DockerfileBase.of("FROM --platform=$BUILDPLATFORM alpine:3.24 AS final\n")
        );
    }

    @Test
    void namesNoBaseForAnImageBuiltFromScratchOrAFileWithoutStages() {
        String scratch = "FROM golang:1.25 AS build\nFROM scratch\nCOPY --from=build /app /app\n";
        String scratchStage = "FROM scratch AS empty\nFROM empty\n";
        assertAll(
            () -> assertEquals(Optional.empty(), DockerfileBase.of(scratch)),
            () -> assertEquals(Optional.empty(), DockerfileBase.of(scratchStage)),
            () -> assertEquals(Optional.empty(), DockerfileBase.of("# FROM node:26\nCMD [\"true\"]\n"))
        );
    }
}
