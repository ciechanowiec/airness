package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Nothing the repository names outside its poms is software Airness refuses: not an image a Dockerfile,
 * a compose file, or a workflow pulls, not a system package a Dockerfile installs, not a build
 * extension, and not a JDK a workflow, a version manager, or this very build runs on.
 *
 * <p>Read once for the whole repository, because none of these files belongs to a module. The module
 * half, the coordinates and the Testcontainers literals, is {@link BlocklistCheck}.
 *
 * <p>Every image reference is judged three ways, in the order a reader repairs them: a reference that
 * still carries a variable is named as one nothing can judge, a refused image is named with its
 * replacement, and a reference nothing pins is named last, because an image is replaced before its tag
 * is chosen.
 */
public final class RepositoryBlocklistCheck {

    private static final String HEADLINE = "Software the repository names that Airness refuses by name";
    private static final String BUILD_JDK = "the build JDK";
    private static final Pattern SDKMAN_JAVA = Pattern.compile("^\\s*java\\s*=\\s*(?<version>\\S+)");
    private static final char VENDOR_SEPARATOR = '-';
    private static final char LINE_SEPARATOR = ':';

    private final int scanned;
    private final List<String> offences;

    /**
     * Reads every file that names an image or a JDK once, so every rule is answered from one pass.
     *
     * @param root        the working tree root
     * @param runtimeName the {@code java.runtime.name} of the JDK running the build
     */
    public RepositoryBlocklistCheck(Path root, String runtimeName) {
        List<Path> tracked = Repository.trackedFiles(root);
        List<Path> dockerfiles = BlocklistFiles.dockerfiles(root, tracked);
        List<Path> composeFiles = BlocklistFiles.composeFiles(root, tracked);
        List<Path> workflows = BlocklistFiles.workflows(root, tracked);
        this.scanned = dockerfiles.size() + composeFiles.size() + workflows.size() + 1;
        this.offences = Stream.of(
            dockerfiles.stream().flatMap(file -> dockerfile(root, file)),
            composeFiles.stream().flatMap(file -> compose(root, file)),
            workflows.stream().flatMap(file -> workflow(root, file)),
            extensions(root),
            sdkman(root),
            Blocklist.runtime(runtimeName).map(refusal -> refusal.at(BUILD_JDK)).stream()
        ).flatMap(Function.identity()).toList();
    }

    /**
     * How many files the check read, counting the running JDK as one, which a caller logs so the reach
     * of a clean verdict is on the record.
     *
     * @return the number of subjects read
     */
    public int scanned() {
        return this.scanned;
    }

    /**
     * Every refused image, package, extension, and JDK the repository names.
     *
     * @return the single verdict this check produces
     */
    public List<Findings> findings() {
        return List.of(new Findings(HEADLINE, this.offences));
    }

    private static Stream<String> dockerfile(Path root, Path file) {
        String text = text(file);
        String relative = relative(root, file);
        Stream<String> images = DockerfileReader.images(text).stream()
            .flatMap(image -> at(Blocklist.judgeImage(image.value()), relative, image.line()));
        Stream<String> packages = DockerfileReader.installedPackages(text).stream()
            .flatMap(name -> at(Blocklist.systemPackage(name.value()), relative, name.line()));
        return Stream.concat(images, packages);
    }

    private static Stream<String> compose(Path root, Path file) {
        String relative = relative(root, file);
        return ComposeFile.images(text(file)).stream()
            .flatMap(
                image -> at(
                    Blocklist.judgeImage(image.value().image()),
                    "%s:%d (service %s)".formatted(relative, image.line(), image.value().service())
                )
            );
    }

    private static Stream<String> workflow(Path root, Path file) {
        String text = text(file);
        String relative = relative(root, file);
        Stream<String> images = WorkflowFile.images(text).stream()
            .flatMap(image -> at(Blocklist.judgeImage(image.value()), relative, image.line()));
        Stream<String> distributions = WorkflowFile.distributions(text).stream()
            .flatMap(name -> at(Blocklist.distribution(name.value()), relative, name.line()));
        return Stream.concat(images, distributions);
    }

    private static Stream<String> extensions(Path root) {
        Path file = root.resolve(BlocklistFiles.EXTENSIONS);
        String relative = relative(root, file);
        return MavenExtensions.in(file).stream().flatMap(extension -> at(Blocklist.coordinate(extension), relative));
    }

    // The vendor is the suffix after the last hyphen of the java entry, as in 25.0.1-tem or 25-oracle.
    private static Stream<String> sdkman(Path root) {
        Path file = root.resolve(BlocklistFiles.SDKMANRC);
        String relative = relative(root, file);
        List<String> lines = Repository.readText(file).map(text -> List.of(text.split("\n", -1))).orElse(List.of());
        return IntStream.range(0, lines.size())
            .boxed()
            .flatMap(index -> vendor(lines.get(index)).stream().map(held -> new Located<>(index + 1, held)))
            .flatMap(vendor -> at(Blocklist.sdkmanVendor(vendor.value()), relative, vendor.line()));
    }

    private static Optional<String> vendor(String line) {
        Matcher matched = SDKMAN_JAVA.matcher(line);
        return matched.find()
            ? Optional.of(matched.group("version"))
                .filter(version -> version.indexOf(VENDOR_SEPARATOR) >= 0)
                .map(version -> version.substring(version.lastIndexOf(VENDOR_SEPARATOR) + 1))
            : Optional.empty();
    }

    private static Stream<String> at(Optional<Refusal> refusal, String relative, int line) {
        return at(refusal, relative + LINE_SEPARATOR + line);
    }

    private static Stream<String> at(Optional<Refusal> refusal, String location) {
        return refusal.map(held -> held.at(location)).stream();
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString();
    }

    private static String text(Path file) {
        return Repository.readText(file).orElse("");
    }
}
