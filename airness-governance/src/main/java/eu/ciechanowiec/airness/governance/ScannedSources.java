package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * The Java sources under a set of roots, and what a rule reading their text finds across them.
 *
 * <p>A rule of this kind is a function from the text of one source to the lines it objects to, and
 * every check built on one needs the same three things around it: the sources read once rather than
 * per rule, each offence prefixed with the path it was found under, and a count the caller refuses
 * when it is zero. Two checks wrote that separately and the duplication detector said so, which is
 * the whole argument for it living here: a third would have written it a third time.
 */
final class ScannedSources {

    private final Path root;

    private final List<Path> sources;

    ScannedSources(Path root, Collection<Path> sourceRoots) {
        this.root = root;
        this.sources = JavaSources.under(root, sourceRoots);
    }

    int scanned() {
        return this.sources.size();
    }

    List<String> offences(Function<CharSequence, List<String>> rule) {
        return this.sources.stream().flatMap(source -> this.offencesIn(source, rule)).toList();
    }

    private Stream<String> offencesIn(Path source, Function<CharSequence, List<String>> rule) {
        return Repository.readText(source).stream()
            .flatMap(text -> rule.apply(text).stream())
            .map(line -> "%s: %s".formatted(this.root.relativize(source), line));
    }
}
