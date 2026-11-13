package eu.ciechanowiec.airness.maven;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Writes an application from a starter: every file of the starter under its own path, with the starter's names
 * replaced by the application's.
 *
 * <p>A starter is a working application called Starter in the package com.example.starter, so it reads as
 * ordinary code. Its code moves to the application's package, the word starter, wherever it names the
 * application, becomes the artifact, and the word Starter becomes the name. A starter that a hyphen joins to
 * what precedes it, as in spring-boot-starter-web, names a Spring Boot module rather than the application, and
 * is kept. The pom marks each value with a placeholder instead, because its comments speak of Spring Boot
 * starters too. Every file is read for the harness version placeholder, and a file stored under an added
 * fixture suffix loses it. A file that is not UTF-8 text, such as an image, names nothing and is copied byte
 * for byte.
 *
 * <p>A region between a line naming {@value #SNAPSHOT_BEGIN} and a line naming {@value #SNAPSHOT_END} serves a
 * SNAPSHOT harness alone, whose npm packages are never published: for one the two marker lines are dropped and
 * the region kept, and for a release the region goes with them, so the application installs the harness packages
 * from the public registry.
 */
@UtilityClass
final class StarterCopy {

    /**
     * The suffix a starter file is stored under when its own name would take effect where it is stored.
     */
    static final String FIXTURE = ".fixture";

    static final String VERSION = "@AIRNESS_VERSION@";

    /**
     * The marker on the line that opens a region kept for a SNAPSHOT harness alone.
     */
    static final String SNAPSHOT_BEGIN = "@SNAPSHOT_ONLY_BEGIN@";

    /**
     * The marker on the line that closes a region kept for a SNAPSHOT harness alone.
     */
    static final String SNAPSHOT_END = "@SNAPSHOT_ONLY_END@";

    private static final String POM = "pom.xml" + FIXTURE;
    private static final String PACKAGE = "com.example.starter";
    private static final String PACKAGE_PATH = "/com/example/starter/";
    private static final String SEPARATOR = "/";
    private static final Pattern ARTIFACT = Pattern.compile("(?<![\\w-])starter(?!\\w)");
    private static final Pattern NAME = Pattern.compile("(?<![\\w-])Starter(?!\\w)");
    private static final Pattern SNAPSHOT_REGION = Pattern.compile(
        "^[^\\n]*" + SNAPSHOT_BEGIN + "[^\\n]*\\n(.*?)^[^\\n]*" + SNAPSHOT_END + "[^\\n]*\\n",
        Pattern.MULTILINE | Pattern.DOTALL
    );

    /**
     * Writes every file of the starter into the destination under the application's names.
     *
     * @param starter     the directory of the starter
     * @param destination the directory the application is written into
     * @param request     the names of the application
     * @param version     the harness version the application builds against
     * @throws IOException when a file cannot be read or written
     */
    static void write(Path starter, Path destination, StarterRequest request, String version) throws IOException {
        for (Path file : files(starter)) {
            String relative = starter.relativize(file).toString().replace(
                file.getFileSystem().getSeparator(), SEPARATOR
            );
            Path target = destination.resolve(target(relative, request));
            Files.createDirectories(Objects.requireNonNull(target.getParent(), "a starter file lives in a directory"));
            Files.write(target, copied(relative, Files.readAllBytes(file), request, version));
        }
    }

    /**
     * Answers what a starter file holds once it is written inside the application: a UTF-8 text under the
     * application's names, and any other file, such as an image, byte for byte.
     *
     * @param relative the path of the file inside the starter, with forward slashes
     * @param bytes    what the file holds in the starter
     * @param request  the names of the application
     * @param version  the harness version the application builds against
     * @return what the file holds in the application
     */
    static byte[] copied(String relative, byte[] bytes, StarterRequest request, String version) {
        return text(bytes)
            .map(text -> content(relative, text, request, version).getBytes(StandardCharsets.UTF_8))
            .orElse(bytes);
    }

    // A strict decoder, which refuses a byte sequence UTF-8 does not allow rather than replacing it, so an
    // image is never mistaken for text and rewritten.
    private static Optional<String> text(byte[] bytes) {
        try {
            return Optional.of(
                StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            );
        } catch (CharacterCodingException _) {
            return Optional.empty();
        }
    }

    private static List<Path> files(Path starter) throws IOException {
        try (Stream<Path> walk = Files.walk(starter)) {
            return walk.filter(Files::isRegularFile).sorted().toList();
        }
    }

    /**
     * Answers where a starter file is written inside the application.
     *
     * @param relative the path of the file inside the starter, with forward slashes
     * @param request  the names of the application
     * @return the path of the file inside the application, with forward slashes
     */
    static String target(String relative, StarterRequest request) {
        String moved = (SEPARATOR + relative)
            .replace(PACKAGE_PATH, SEPARATOR + request.packagePath() + SEPARATOR)
            .substring(1);
        String unfixed = moved.endsWith(FIXTURE) ? moved.substring(0, moved.length() - FIXTURE.length()) : moved;
        int directory = unfixed.lastIndexOf(SEPARATOR) + 1;
        return unfixed.substring(0, directory) + renamed(unfixed.substring(directory), request);
    }

    /**
     * Answers what a starter file holds once it is written inside the application.
     *
     * @param relative the path of the file inside the starter, with forward slashes
     * @param text     what the file holds in the starter
     * @param request  the names of the application
     * @param version  the harness version the application builds against
     * @return what the file holds in the application
     */
    static String content(String relative, String text, StarterRequest request, String version) {
        String versioned = regions(text, version).replace(VERSION, version);
        if (POM.equals(relative)) {
            return versioned.replace("@GROUP_ID@", request.groupId())
                .replace("@ARTIFACT_ID@", request.artifactId())
                .replace("@PACKAGE@", request.javaPackage());
        }
        return renamed(versioned.replace(PACKAGE, request.javaPackage()), request);
    }

    /**
     * Whether a harness version is a SNAPSHOT, whose npm packages are never published.
     *
     * @param version the harness version the application builds against
     * @return whether the version is a SNAPSHOT
     */
    static boolean snapshot(String version) {
        return SnapshotVersions.snapshot(version);
    }

    private static String regions(String text, String version) {
        return SNAPSHOT_REGION.matcher(text).replaceAll(snapshot(version) ? "$1" : "");
    }

    private static String renamed(String text, StarterRequest request) {
        String artifact = ARTIFACT.matcher(text).replaceAll(Matcher.quoteReplacement(request.artifactId()));
        return NAME.matcher(artifact).replaceAll(Matcher.quoteReplacement(request.name()));
    }
}
