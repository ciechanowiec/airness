package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Dockerfile reader finds the images FROM lines pull and the packages RUN lines install, and keeps
 * the line each instruction started on.
 */
class DockerfileReaderTest {

    private static List<String> images(String text) {
        return DockerfileReader.images(text).stream().map(Located::value).toList();
    }

    private static List<String> packages(String text) {
        return DockerfileReader.installedPackages(text).stream().map(Located::value).toList();
    }

    @Test
    void substitutesAnArgumentDefaultIntoFrom() {
        String text = """
            ARG NODE_VERSION="22.12.0"
            ARG REDIS=7.2.4
            FROM node:${NODE_VERSION} AS build
            FROM redis:$REDIS
            """;
        assertEquals(List.of("node:22.12.0", "redis:7.2.4"), images(text), "both spellings of a variable resolve");
    }

    @Test
    void skipsStagesAndScratchAndThePlatformOption() {
        String text = """
            FROM --platform=linux/amd64 eclipse-temurin:25-jdk AS build
            FROM scratch
            FROM build
            FROM eclipse-temurin:25-jre
            """;
        assertEquals(List.of("eclipse-temurin:25-jdk", "eclipse-temurin:25-jre"), images(text));
    }

    @Test
    void keepsTheLineAnInstructionStartsOn() {
        String text = """
            # the base
            FROM postgres:18

            RUN apt-get update && \\
                apt-get install -y --no-install-recommends ghostscript=10.0.0-1 curl && \\
                rm -rf /var/lib/apt/lists/*
            """;
        assertEquals(2, DockerfileReader.images(text).getFirst().line(), "the comment line counts");
        assertEquals(List.of("ghostscript", "curl"), packages(text), "the continued RUN reads as one command");
        assertEquals(4, DockerfileReader.installedPackages(text).getFirst().line(), "and is placed where it began");
    }

    @Test
    void leavesAnArgumentWithoutADefaultUnresolved() {
        assertEquals(List.of("redis:${TAG}"), images("ARG TAG\nFROM redis:${TAG}\n"), "nothing says what it holds");
        assertEquals(List.of("redis:7.2.4"), images("FROM redis:${TAG:-7.2.4}\n"), "but a written default does");
    }

    @Test
    void readsPackagesAfterEveryInstallVerb() {
        String text = """
            RUN apk add --no-cache mupdf~1.24 ca-certificates; dnf install -y mongodb-org
            RUN echo nothing here
            """;
        assertEquals(List.of("mupdf", "ca-certificates", "mongodb-org"), packages(text));
    }

    @Test
    void ignoresACommentedInstruction() {
        assertEquals(List.of(), images("# FROM mongo:7\n  # FROM redis:8\n"), "a comment pulls nothing");
    }
}
