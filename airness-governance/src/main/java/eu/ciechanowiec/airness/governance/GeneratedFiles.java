package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The files a generator owns, as the stack layers present in a repository declare them.
 *
 * <p>A layer declares its generated files in {@code airness/generated/<capability>.txt} on the plugin
 * classpath, one path per line, optionally followed by a tab and the location it applies to ({@code ROOT}
 * unless stated), exactly as a managed-file manifest places a file. A layer with no such file declares
 * none, which is how a layer published from another repository adds its own. Each declaration is placed
 * at every site carrying its capability at its location, so a generated file of a node project is denied
 * in each node project rather than once at the root. One file per capability is read, so a capability has
 * one owner: a second jar shipping the same declaration would be shadowed by the first.
 *
 * @param classes the classloader carrying the layer declarations
 */
public record GeneratedFiles(ClassLoader classes) {

    private static final String DECLARATIONS = "airness/generated/";
    private static final String TEXT = ".txt";
    private static final String COMMENT = "#";
    private static final String SEPARATOR = "\t";
    private static final int MOST_FIELDS = 2;

    /**
     * Every repository-relative path a generator owns: those the layers declare across the given sites,
     * then those the project declares itself.
     *
     * <p>A project can only add to the set, so its declaration makes the denial stricter and never looser.
     *
     * @param sites    the directories of the repository, with the capabilities present in each
     * @param declared repository-relative paths the project declares as generated
     * @return the paths, layer declarations first, each once
     */
    public List<String> paths(Collection<AssetSite> sites, Collection<String> declared) {
        return Stream.concat(
            this.paths(sites).stream(),
            declared.stream().map(String::strip).filter(path -> !path.isEmpty()).map(GeneratedFiles::relative)
        ).distinct().toList();
    }

    /**
     * Every repository-relative path a generator owns across the given sites.
     *
     * @param sites the directories of the repository, with the capabilities present in each
     * @return the paths, in site order and then in declaration order
     */
    public List<String> paths(Collection<AssetSite> sites) {
        return sites.stream()
            .flatMap(
                site -> site.capabilities().stream()
                    .sorted()
                    .flatMap(this::declared)
                    .filter(declaration -> declaration.location() == site.location())
                    .map(declaration -> placed(site, declaration.path()))
            ).distinct()
            .toList();
    }

    private Stream<Declaration> declared(Capability capability) {
        String resource = DECLARATIONS + capability.id() + TEXT;
        return this.read(resource).stream()
            .flatMap(String::lines)
            .map(String::strip)
            .filter(line -> !line.isEmpty() && !line.startsWith(COMMENT))
            .map(line -> Declaration.of(resource, line));
    }

    private Optional<String> read(String resource) {
        try (InputStream stream = this.classes.getResourceAsStream(resource)) {
            return Optional.ofNullable(stream).map(GeneratedFiles::drain);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + resource, exception);
        }
    }

    private static String drain(InputStream stream) {
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read a generated-file declaration", exception);
        }
    }

    private static String relative(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private static String placed(AssetSite site, String path) {
        return site.directory().isEmpty() ? path : site.directory() + '/' + path;
    }

    /**
     * One declared generated file.
     *
     * @param path     the path, relative to its location
     * @param location where the path applies
     */
    private record Declaration(String path, AssetLocation location) {

        static Declaration of(String resource, String line) {
            String[] fields = line.split(SEPARATOR, -1);
            if (fields.length > MOST_FIELDS) {
                throw new IllegalStateException(
                    resource + " declares a path and optionally a location on each line, not: " + line
                );
            }
            AssetLocation location = fields.length == MOST_FIELDS
                ? AssetLocation.valueOf(fields[1].strip())
                : AssetLocation.ROOT;
            return new Declaration(fields[0].strip(), location);
        }
    }
}
