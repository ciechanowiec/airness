package eu.ciechanowiec.airness.governance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Rejects ignored repository files that the build selected as inputs.
 *
 * <p>Git decides what is ignored, while the build tool decides which files it consumes. Neither
 * answer stands in for the other. Inputs beneath declared build directories are generated output,
 * and new files that are not ignored remain valid work before staging.
 */
public final class IgnoredBuildInputs {

    private final Path root;
    private final List<BuildInput> inputs;
    private final List<Path> outputs;

    /**
     * Describes the repository inputs and the reactor's generated-output directories.
     *
     * @param root    the Git working tree
     * @param inputs  the build tool's file selections
     * @param outputs the configured build directories
     */
    public IgnoredBuildInputs(Path root, Collection<BuildInput> inputs, Collection<Path> outputs) {
        this.root = root.toAbsolutePath().normalize();
        this.inputs = List.copyOf(inputs);
        this.outputs = outputs.stream().map(path -> path.toAbsolutePath().normalize()).toList();
    }

    /**
     * Names every ignored selected file once, with its repair.
     *
     * @return deterministic findings without file contents
     * @throws IllegalStateException when the supplied root is not the Git working tree root
     */
    public List<String> problems() {
        if (!Repository.rootFrom(this.root).equals(this.root)) {
            throw new IllegalStateException("Build input check requires the exact Git working tree root: " + this.root);
        }
        return this.inputs.stream().filter(this::eligible).flatMap(this::offences).distinct()
            .sorted(Comparator.comparing(path -> relative(this.root, path)))
            .map(this::message).toList();
    }

    private boolean eligible(BuildInput input) {
        Path directory = input.directory();
        boolean generated = this.outputs.stream().anyMatch(directory::startsWith);
        return directory.startsWith(this.root) && !generated && !input.selected().isEmpty();
    }

    private Stream<Path> offences(BuildInput input) {
        if (!Files.isDirectory(input.directory())) {
            throw new IllegalStateException("Build input directory is unreadable or absent: " + input.directory());
        }
        return this.ignored(input.directory()).filter(path -> selected(input, path));
    }

    private Stream<Path> ignored(Path directory) {
        Stream<String> command = Stream.of("ls-files", "-z", "--others", "--ignored", "--exclude-standard", "--");
        Stream<String> scope = Stream.of(":(literal)" + relative(this.root, directory));
        Stream<String> generated = this.outputs.stream().filter(path -> path.startsWith(this.root))
            .map(path -> ":(exclude,literal)" + relative(this.root, path));
        List<String> arguments = Stream.of(command, scope, generated).flatMap(stream -> stream).toList();
        String listing = GitPlumbing.run(this.root, arguments);
        return Arrays.stream(listing.split("\0", -1)).filter(name -> !name.isEmpty()).map(this.root::resolve);
    }

    private static boolean selected(BuildInput input, Path path) {
        return path.startsWith(input.directory()) && input.selected().contains(relative(input.directory(), path));
    }

    private String message(Path path) {
        String kinds = this.inputs.stream().filter(input -> selected(input, path)).map(BuildInput::kind)
            .distinct().sorted().collect(Collectors.joining(", "));
        return "%s: ignored build input %s would be absent from a checkout. %s".formatted(
            kinds, relative(this.root, path),
            "Track this input or generate it under a Maven build directory."
        );
    }

    private static String relative(Path directory, Path path) {
        String name = directory.relativize(path).toString().replace('\\', '/');
        return name.isEmpty() ? "." : name;
    }
}
