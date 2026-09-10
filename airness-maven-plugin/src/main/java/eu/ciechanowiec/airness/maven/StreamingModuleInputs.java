package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.StreamingInput;
import eu.ciechanowiec.airness.governance.StreamingJavaIndex;
import eu.ciechanowiec.airness.governance.StreamingResourceIndex;
import eu.ciechanowiec.airness.governance.StreamingTimeoutInputs;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;

/**
 * Production Java and copied configuration from one actual reactor dependency.
 */
final class StreamingModuleInputs {

    private final Path root;
    private final MavenProject project;

    StreamingModuleInputs(Path root, MavenProject project) {
        this.root = root;
        this.project = project;
    }

    List<StreamingInput> read() {
        return Stream.concat(this.sources(), this.project.getResources().stream().flatMap(this::resources)).toList();
    }

    private Stream<StreamingInput> sources() {
        return this.project.getCompileSourceRoots().stream().map(this::path)
            .flatMap(
                directory -> new InputSelection(directory, List.of("**/*.java"), List.of(), true)
                    .files(List.of()).stream().map(directory::resolve)
            )
            .flatMap(source -> StreamingJavaIndex.read(source, this.location(source), this.origins()).stream());
    }

    private List<String> origins() {
        return Stream.concat(
            Stream.of(this.output().toUri().toString()),
            Optional.ofNullable(this.project.getArtifact()).flatMap(artifact -> Optional.ofNullable(artifact.getFile()))
                .map(file -> file.toPath().toAbsolutePath().normalize().toUri().toString()).stream()
        ).distinct().toList();
    }

    private Stream<StreamingInput> resources(Resource resource) {
        Path directory = this.path(resource.getDirectory());
        Path target = this.output().resolve(Optional.ofNullable(resource.getTargetPath()).orElse(""));
        return new InputSelection(directory, resource.getIncludes(), resource.getExcludes(), true).files(List.of())
            .stream().filter(StreamingModuleInputs::configuration)
            .flatMap(name -> this.resource(directory.resolve(name), target.resolve(name)));
    }

    @SneakyThrows
    private Stream<StreamingInput> resource(Path source, Path copied) {
        StreamingInput input = new StreamingInput(
            "source", this.location(source), source.toUri().toString(),
            StreamingTimeoutInputs.fingerprint(Files.readString(source))
        );
        return Stream.concat(
            Stream.of(input), Files.isRegularFile(copied) ? this.copied(source, copied) : Stream.empty()
        );
    }

    @SneakyThrows
    private Stream<StreamingInput> copied(Path source, Path copied) {
        String content = Files.readString(copied);
        if (!StreamingResourceIndex.declares(copied.toString(), content)) {
            return Stream.empty();
        }
        String digest = StreamingTimeoutInputs.fingerprint(content);
        String relative = this.output().toUri().relativize(copied.toUri()).toASCIIString();
        Stream<String> addresses = this.origins().stream().map(
            origin -> origin.endsWith("/")
                ? origin + relative : "jar:" + origin + "!/" + relative
        );
        return addresses.map(uri -> new StreamingInput("resource", this.location(source), uri, digest));
    }

    private static boolean configuration(String name) {
        return name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".properties");
    }

    private Path path(String name) {
        return this.project.getBasedir().toPath().resolve(name).toAbsolutePath().normalize();
    }

    private Path output() {
        return this.path(this.project.getBuild().getOutputDirectory());
    }

    private String location(Path file) {
        return this.root.relativize(file.toAbsolutePath().normalize()).toString();
    }
}
