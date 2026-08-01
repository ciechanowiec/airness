package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Every type a Javadoc comment names is reached by a link rather than left as prose. The name of a type
 * is the one part of a comment a reader can follow to its definition, and the Javadoc tool already fails
 * the build on a link that does not resolve, so a resolvable name left unlinked is the one documentation
 * defect nothing else here would catch.
 *
 * <p>Only a name that resolves from the file it appears in is reported, which {@link JavadocScope}
 * decides. A comment may name a type it has no way to reach, and asking it to link one would be asking
 * for a link the Javadoc tool then rejects.
 */
public final class JavadocLinkCheck {

    private static final String HEADLINE = "Javadoc names types it does not link, though they resolve from that file";

    private final Path root;
    private final List<Path> sources;
    private final JavadocScope scope;

    /**
     * Reads the sources once and derives the resolution scope from that same set.
     *
     * @param root        the working tree root
     * @param sourceRoots the directories whose Java sources are read
     */
    public JavadocLinkCheck(Path root, Collection<Path> sourceRoots) {
        this.root = root;
        this.sources = JavaSources.under(root, sourceRoots);
        this.scope = JavadocScope.over(this.sources);
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
     * The unlinked names, each naming its file.
     *
     * @return the single verdict this check produces
     */
    public List<Findings> findings() {
        return List.of(new Findings(HEADLINE, this.sources.stream().flatMap(this::unlinkedIn).toList()));
    }

    private Stream<String> unlinkedIn(Path source) {
        return Repository.readText(source).stream().flatMap(text -> this.report(source, text));
    }

    private Stream<String> report(Path source, CharSequence text) {
        Predicate<String> resolves = this.scope.of(source, text);
        return JavadocLinkRules.unlinked(text, resolves).stream()
            .map(name -> "%s: %s".formatted(this.root.relativize(source), name));
    }
}
