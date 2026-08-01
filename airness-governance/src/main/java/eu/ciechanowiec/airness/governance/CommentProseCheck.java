package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * No comment in a tracked Java source joins two clauses with a semicolon, and no {@code @return} tag
 * carries a full stop. A comment is read under time pressure, so the sentence that survives is the short
 * one: a full stop where both clauses stand alone, a comma where the second is subordinate. A
 * {@code @return} is the exception in both directions, being a fragment that completes "returns ...":
 * it takes no full stop, and with none available a semicolon is what joins a second clause to it.
 *
 * <p>Main and test sources are held to this equally, which is why the roots arrive as a collection
 * rather than being the production root alone. A rule that reached only production Javadoc would leave
 * four fifths of the comments anyone reads outside it, which is a rule that says more than it does.
 */
public final class CommentProseCheck {

    private static final String SEMICOLONS =
        "Comment prose uses a semicolon where a full stop or a comma reads shorter";
    private static final String PERIODS = "A @return completes \"returns ...\", so it takes no full stop";

    private final Path root;
    private final List<Path> sources;

    /**
     * Reads the sources once, so both rules are answered from one pass over the tree.
     *
     * @param root        the working tree root
     * @param sourceRoots the directories whose Java sources are read
     */
    public CommentProseCheck(Path root, Collection<Path> sourceRoots) {
        this.root = root;
        this.sources = JavaSources.under(root, sourceRoots);
    }

    /**
     * How many sources the check read, which a caller refuses when it is zero.
     *
     * @return the number of Java sources in scope
     */
    public int scanned() {
        return this.sources.size();
    }

    /**
     * Both prose rules, each reported separately so a failure names which one was broken.
     *
     * @return one verdict per rule
     */
    public List<Findings> findings() {
        return List.of(
            new Findings(SEMICOLONS, this.offences(CommentProseRules::semicolons)),
            new Findings(PERIODS, this.offences(CommentProseRules::returnPeriods))
        );
    }

    private List<String> offences(ProseRule rule) {
        return this.sources.stream().flatMap(source -> this.offencesIn(source, rule)).toList();
    }

    private Stream<String> offencesIn(Path source, ProseRule rule) {
        return Repository.readText(source).stream()
            .flatMap(text -> rule.offences(text).stream())
            .map(line -> "%s: %s".formatted(this.root.relativize(source), line));
    }

    @FunctionalInterface
    private interface ProseRule {

        List<String> offences(CharSequence source);
    }
}
