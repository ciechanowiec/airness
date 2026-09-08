package eu.ciechanowiec.airness.maven;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.codehaus.plexus.util.DirectoryScanner;

/**
 * Maven-compatible file selection using the scanner's own pattern and exclusion semantics.
 *
 * @param directory the resolved input directory
 * @param includes  inclusion patterns, empty for everything
 * @param excludes  exclusion patterns
 * @param defaults  whether Maven's default exclusions apply
 */
record InputSelection(Path directory, List<String> includes, List<String> excludes, boolean defaults) {

    InputSelection {
        includes = List.copyOf(includes);
        excludes = List.copyOf(excludes);
    }

    Set<String> files(Collection<Path> outputs) {
        if (Files.notExists(this.directory)) {
            return Set.of();
        }
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(this.directory.toFile());
        scanner.setCaseSensitive(true);
        scanner.setIncludes(this.includes.isEmpty() ? new String[] {"**/*"} : this.includes.toArray(String[]::new));
        Stream<String> generated = outputs.stream().filter(path -> path.startsWith(this.directory))
            .map(path -> this.directory.relativize(path).toString().replace('\\', '/') + "/**");
        scanner.setExcludes(Stream.concat(this.excludes.stream(), generated).toArray(String[]::new));
        this.defaults(scanner);
        scanner.scan();
        return Arrays.stream(scanner.getIncludedFiles()).map(name -> name.replace('\\', '/'))
            .collect(Collectors.toUnmodifiableSet());
    }

    private void defaults(DirectoryScanner scanner) {
        if (this.defaults) {
            scanner.addDefaultExcludes();
        }
    }
}
