package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;

/**
 * The year a repository's history begins.
 *
 * <p>A history that is unborn has no first commit, and a shallow clone has one git merely stopped at, so neither
 * answers. A history may hold several first commits, when unrelated histories were merged, and the earliest of
 * them is where the project began.
 */
@UtilityClass
public final class FirstCommit {

    /**
     * The year of the earliest commit without a parent that HEAD reaches.
     *
     * @param root the working tree root
     * @return that year, or nothing when the history is unborn or truncated
     */
    public static Optional<Year> year(Path root) {
        if (!Repository.hasCommits(root) || Repository.isShallow(root)) {
            return Optional.empty();
        }
        return GitPlumbing.run(root, List.of("log", "--max-parents=0", "--format=%ad", "--date=format:%Y", "HEAD"))
            .lines()
            .map(String::strip)
            .filter(line -> !line.isEmpty())
            .map(Year::parse)
            .min(Year::compareTo);
    }
}
