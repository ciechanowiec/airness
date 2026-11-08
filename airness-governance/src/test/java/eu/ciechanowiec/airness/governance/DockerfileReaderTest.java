package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
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
    void inventoriesExternalCopySourcesAndPreservesTheirLine() {
        String text = """
            FROM scratch
            COPY --link --from=mongo:8.0 /usr/bin/mongod /mongod
            COPY --from=alpine /etc/os-release /release
            """;
        assertEquals(List.of("mongo:8.0", "alpine"), images(text));
        assertEquals(List.of(2, 3), DockerfileReader.images(text).stream().map(Located::line).toList());
    }

    @Test
    void distinguishesCopyStageNamesAndIndexesFromImages() {
        String text = """
            FROM scratch AS output
            COPY --from=builder /built /built
            FROM alpine:3.22 AS builder
            COPY --from=alpine:3.21 /etc/os-release /release
            FROM scratch AS final
            COPY --from=0 /built /built
            COPY --from=output /built /built
            """;
        assertEquals(List.of("alpine:3.22", "alpine:3.21"), images(text));
    }

    @Test
    void readsContinuedCopyOptionsWithoutMistakingSourceNamesForOptions() {
        String text = """
            FROM scratch
            COPY \\
                --from=mongo:8.0 \\
                /usr/bin/mongod /mongod
            COPY local --from=not-an-option /output/
            """;
        assertEquals(List.of("mongo:8.0"), images(text));
    }

    @Test
    void readsCopyOptionsContainingQuotedOrEscapedWhitespace() {
        for (String options : List.of("--exclude=\"file name\"", "--exclude='file name'", "--exclude=file\\ name")) {
            assertEquals(
                List.of("mongo:8.0"), images(
                    "FROM scratch\nCOPY " + options
                        + " --from=\"mongo\":8.0 /usr/bin/mongod /mongod\n"
                )
            );
        }
    }

    @Test
    void stopsAtTheCopyOptionTerminatorOrAQuotedSourceName() {
        assertEquals(List.of(), images("FROM scratch\nCOPY -- --from=local-file /output/\n"));
        assertEquals(List.of(), images("FROM scratch\nCOPY \"--from=local-file\" /output/\n"));
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
    void readsPackagesAfterOptionsWrittenBeforeTheInstallVerb() {
        List<String> installs = List.of(
            "apt-get -y install", "apt-get -qq -y install", "apt -y install", "yum -y install", "dnf -y install",
            "microdnf -y install", "apk --no-cache add", "zypper -n install", "zypper --non-interactive install"
        );
        for (String install : installs) {
            assertEquals(
                List.of("ghostscript"), packages("RUN " + install + " --quiet ghostscript\n"),
                "an option before the verb still leaves an install: " + install
            );
        }
    }

    @Test
    void readsNoPackagesFromAVerbThatInstallsNothing() {
        String text = """
            RUN apt-get -y update && apt-get -y remove ghostscript && apk --no-cache del mupdf
            RUN apt-get -y --no-install-recommends upgrade
            """;
        assertEquals(List.of(), packages(text));
    }

    @Test
    void readsPastACommentOrABlankLineInsideAContinuedInstruction() {
        String text = """
            FROM debian:13
            RUN apt-get update && \\
            # the renderer
                apt-get install -y \\
                  # ghostscript renders the previews
                  ghostscript \\

                  mupdf
            RUN echo done
            FROM redis:8
            """;
        assertEquals(List.of("ghostscript", "mupdf"), packages(text), "Docker drops those lines, not the rest");
        assertEquals(List.of(1, 10), DockerfileReader.images(text).stream().map(Located::line).toList());
    }

    @Test
    void endsNoInstructionAtACommentLineThatEndsWithABackslash() {
        String text = """
            # the base \\
            FROM postgres:18
            """;
        assertEquals(List.of("postgres:18"), images(text), "a comment continues nothing");
    }

    @Test
    void readsTheBodyOfEveryHereDocumentSpelling() {
        // Only <<- ends a body at a terminator indented by tabs. Every other marker reads on to the terminator
        // that follows at the start of a line.
        Map<String, String> terminators = Map.of(
            "<<EOT", "\tEOT\nEOT\n", "<<\"EOT\"", "\tEOT\nEOT\n", "<<'EOT'", "\tEOT\nEOT\n", "<<-EOT", "\tEOT\n"
        );
        terminators.forEach(
            (marker, terminator) -> {
                String text = """
                    RUN %s
                    \tset -e
                    \t# apt-get install -y commented-out
                    \tapt-get update
                    \tapt-get install -y \\
                    \t  ghostscript
                    \tapk add mupdf
                    %sFROM redis:8
                    """.formatted(marker, terminator);
                assertEquals(
                    List.of("ghostscript", "mupdf"), packages(text), "the body is the script it runs: " + marker
                );
                assertEquals(
                    List.of(Math.toIntExact(text.lines().count())),
                    DockerfileReader.images(text).stream().map(Located::line).toList(),
                    "the instruction after the body keeps its own line: " + marker
                );
            }
        );
    }

    @Test
    void readsEveryHereDocumentOneInstructionOpens() {
        String text = """
            RUN <<FIRST bash && 3<<SECOND cat
            apt-get install -y ghostscript
            FIRST
            yum install mongodb-org
            SECOND
            COPY <<NOTES /notes.txt
            FROM mongo:7 is no instruction here
            NOTES
            FROM redis:8
            """;
        assertEquals(List.of("ghostscript", "mongodb-org"), packages(text));
        assertEquals(List.of("redis:8"), images(text), "a body is never read as an instruction of its own");
    }

    @Test
    void substitutesEachVariableByItsWholeName() {
        String text = """
            ARG REDIS=redis
            ARG REDIS_VERSION=7.2.4
            ARG TAG=7
            FROM $REDIS:$REDIS_VERSION
            FROM ${REDIS}:${REDIS_VERSION}
            FROM $REDIS_VERSION:$REDIS
            FROM redis:$TAG_X
            FROM redis:${TAG_X}
            """;
        assertEquals(
            List.of("redis:7.2.4", "redis:7.2.4", "7.2.4:redis", "redis:$TAG_X", "redis:${TAG_X}"), images(text),
            "a name is never resolved as a prefix of a longer one"
        );
    }

    @Test
    void ignoresACommentedInstruction() {
        assertEquals(List.of(), images("# FROM mongo:7\n  # FROM redis:8\n"), "a comment pulls nothing");
    }
}
