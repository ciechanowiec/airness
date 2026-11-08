package eu.ciechanowiec.airness.maven;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.apache.maven.execution.MavenSession;

/**
 * The local Maven repository a build reads from: the head it installs into, followed by the read-only
 * tails that {@code maven.repo.local.tail} chains behind it.
 *
 * <p>Maven resolves an artifact from the first of them that holds it, so the same order answers where an
 * installed file is. A clone that installs into a head of its own and chains the shared repository as its
 * tail installs nothing another clone reads, and downloads nothing the shared repository already holds.
 *
 * @param head  the repository the build installs into, which Maven names as the local one
 * @param tails the repositories read after the head, in the order Maven reads them
 */
record LocalRepository(Path head, List<Path> tails) {

    /**
     * The property that chains tails behind the head, a comma-separated list of directories.
     */
    static final String TAIL = "maven.repo.local.tail";

    LocalRepository {
        tails = List.copyOf(tails);
    }

    /**
     * Answers a repository with no tail, which is what a build sees unless it chains one.
     *
     * @param head the repository the build installs into
     * @return the repository
     */
    static LocalRepository at(Path head) {
        return new LocalRepository(head, List.of());
    }

    /**
     * Answers the local repository of a running build.
     *
     * @param session the session, which names the head and carries the property that chains the tails
     * @return the repository
     */
    static LocalRepository of(MavenSession session) {
        Path head = session.getRepositorySession().getLocalRepositoryManager().getRepository().getBasePath();
        return of(head, session.getSystemProperties(), session.getUserProperties());
    }

    /**
     * Answers the local repository the given properties chain behind the head. A property set on the
     * command line wins over the same property of the system, as it does for Maven.
     *
     * @param head   the repository the build installs into
     * @param system the system properties of the build
     * @param user   the properties set on the command line
     * @return the repository
     */
    static LocalRepository of(Path head, Properties system, Properties user) {
        String declared = Optional.ofNullable(user.getProperty(TAIL))
            .or(() -> Optional.ofNullable(system.getProperty(TAIL)))
            .orElse("");
        List<Path> tails = Arrays.stream(declared.split(","))
            .map(String::strip)
            .filter(Predicate.not(String::isEmpty))
            .map(tail -> Path.of(tail).toAbsolutePath().normalize())
            .toList();
        return new LocalRepository(head, tails);
    }

    /**
     * Answers the head followed by the tails, in the order Maven reads them.
     *
     * @return every repository of the chain
     */
    List<Path> layers() {
        return Stream.concat(Stream.of(this.head), this.tails.stream()).toList();
    }

    /**
     * Answers where the file at the given path inside a repository is: in the first repository of the
     * chain that holds it, or in the head when none does.
     *
     * @param relative the path of the file inside a repository
     * @return the file
     */
    Path file(Path relative) {
        return this.layers().stream()
            .map(layer -> layer.resolve(relative))
            .filter(Files::exists)
            .findFirst()
            .orElseGet(() -> this.head.resolve(relative));
    }
}
