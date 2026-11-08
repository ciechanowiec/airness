package eu.ciechanowiec.airness.maven;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
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
 * fixture suffix loses it.
 */
@UtilityClass
final class StarterCopy {

    /**
     * The suffix a starter file is stored under when its own name would take effect where it is stored.
     */
    static final String FIXTURE = ".fixture";

    static final String VERSION = "@AIRNESS_VERSION@";

    private static final String POM = "pom.xml" + FIXTURE;
    private static final String PACKAGE = "com.example.starter";
    private static final String PACKAGE_PATH = "/com/example/starter/";
    private static final String SEPARATOR = "/";
    private static final Pattern ARTIFACT = Pattern.compile("(?<![\\w-])starter(?!\\w)");
    private static final Pattern NAME = Pattern.compile("(?<![\\w-])Starter(?!\\w)");

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
            Files.writeString(target, content(relative, Files.readString(file), request, version));
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
        String versioned = text.replace(VERSION, version);
        if (POM.equals(relative)) {
            return versioned.replace("@GROUP_ID@", request.groupId())
                .replace("@ARTIFACT_ID@", request.artifactId())
                .replace("@PACKAGE@", request.javaPackage());
        }
        return renamed(versioned.replace(PACKAGE, request.javaPackage()), request);
    }

    private static String renamed(String text, StarterRequest request) {
        String artifact = ARTIFACT.matcher(text).replaceAll(Matcher.quoteReplacement(request.artifactId()));
        return NAME.matcher(artifact).replaceAll(Matcher.quoteReplacement(request.name()));
    }
}
