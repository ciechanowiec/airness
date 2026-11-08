package eu.ciechanowiec.airness.maven;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.maven.project.MavenProject;

/**
 * A module holding Java inherits the parent that carries the Java rules.
 *
 * <p>The generic {@code airness-parent} binds only the rules that make sense in a repository with no code.
 * A module with Java sources that inherits it still compiles and can pass, and nothing in the log says that
 * no compiler check, analyzer, test rule or coverage floor ran. That is the case of a consumer that named
 * {@code airness-parent} before the Java rules moved into {@code airness-parent-java}, so the refusal names
 * the parent to inherit instead.
 */
@UtilityClass
final class JavaParents {

    private static final String JAVA_SUFFIX = ".java";

    static Optional<String> problem(MavenProject project) {
        return Optional.of(
            "This module has Java sources but does not inherit " + AirnessCoordinates.GROUP + ':'
                + AirnessCoordinates.JAVA_PARENT + ", so no Java rule applies to it. Name "
                + AirnessCoordinates.JAVA_PARENT
                + " (or airness-parent-spring-boot for a Spring Boot project) as its parent instead of airness-parent"
        ).filter(
            _ -> holdsJava(project.getBasedir().toPath())
                && !AirnessCoordinates.inherits(project, AirnessCoordinates.JAVA_PARENT)
        );
    }

    private static boolean holdsJava(Path basedir) {
        return Stream.of("src/main/java", "src/test/java")
            .map(basedir::resolve)
            .filter(Files::isDirectory)
            .anyMatch(JavaParents::containsJava);
    }

    /**
     * Whether a directory holds a Java source file anywhere below it.
     *
     * @param root the directory to search
     * @return whether a regular file below it ends in {@code .java}
     */
    @SneakyThrows
    static boolean containsJava(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.anyMatch(
                file -> file.getFileName().toString().endsWith(JAVA_SUFFIX) && Files.isRegularFile(file)
            );
        }
    }
}
