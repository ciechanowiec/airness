package eu.ciechanowiec.airness.governance;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Collection;
import java.util.List;

/**
 * The files a commit from a working tree would carry, and the part of them somebody writes rather than a
 * generator.
 *
 * <p>The checks that read every committed text, typography and indentation among them, read this one set. A
 * generated file carries whatever its generator writes, and the agent runtime is denied editing it, so a
 * finding there could not be repaired where it is reported. The generated files arrive as repository-relative
 * globs, matched against the path from the working tree root. A glob that matches nothing is not stale: a
 * layer declares the files its stack may write, whether or not this repository has them yet.
 *
 * @param tracked  every committable file
 * @param authored the committable files no generated glob matches
 */
record AuthoredFiles(List<Path> tracked, List<Path> authored) {

    /**
     * Copies both lists, so a caller holding its own cannot change a selection already made.
     *
     * @param tracked  every committable file
     * @param authored the committable files no generated glob matches
     */
    AuthoredFiles {
        tracked = List.copyOf(tracked);
        authored = List.copyOf(authored);
    }

    /**
     * Lists the committable files of a working tree and sets apart the ones a generator owns.
     *
     * @param root      the working tree root
     * @param generated repository-relative globs of the files a generator owns
     * @return the selection
     */
    static AuthoredFiles of(Path root, Collection<String> generated) {
        List<PathMatcher> owned = generated.stream()
            .map(glob -> FileSystems.getDefault().getPathMatcher("glob:" + glob))
            .toList();
        List<Path> tracked = Repository.trackedFiles(root);
        List<Path> authored = tracked.stream()
            .filter(file -> owned.stream().noneMatch(matcher -> matcher.matches(root.relativize(file))))
            .toList();
        return new AuthoredFiles(tracked, authored);
    }

    /**
     * How many committable files a generator owns.
     *
     * @return the count the selection left out
     */
    long generated() {
        return (long) this.tracked.size() - this.authored.size();
    }
}
