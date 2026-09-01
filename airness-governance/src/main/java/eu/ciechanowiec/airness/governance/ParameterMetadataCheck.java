package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.MethodParametersAttribute;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Proves that compiled production and test methods retain their source parameter names.
 *
 * <p>The compiler configuration is necessary but not authoritative: a profile, command-line property,
 * or later compiler execution can change what the class file contains. This check reads the emitted
 * bytecode instead, which is the value reflection and frameworks eventually observe.
 *
 * <p>Synthetic methods are passed over because they declare no source parameters to retain. Javac
 * creates them for lambda bodies and bridges without a {@code MethodParameters} attribute even when it
 * receives {@code -parameters}. Every non-synthetic method and constructor with a formal parameter must
 * carry one name per descriptor parameter.
 */
public final class ParameterMetadataCheck {

    private static final String CLASS_SUFFIX = ".class";
    private static final String JAVA_SUFFIX = ".java";
    private final List<Path> productionSources;
    private final List<Path> testSources;
    private final ModuleOutput output;

    /**
     * Creates a parameter-metadata inspection of one module's compiler inputs and outputs.
     *
     * @param productionSources production Java source roots
     * @param testSources       test Java source roots
     * @param output            compiled main and test output directories
     */
    public ParameterMetadataCheck(
        Collection<Path> productionSources, Collection<Path> testSources, ModuleOutput output
    ) {
        this.productionSources = List.copyOf(productionSources);
        this.testSources = List.copyOf(testSources);
        this.output = output;
    }

    /**
     * The verdict over both compiler output trees.
     *
     * @return one finding containing every class or method that lacks required parameter metadata
     */
    public Findings findings() {
        List<String> offences = Stream.concat(
            this.offences("production", this.productionSources, this.output.main()),
            this.offences("test", this.testSources, this.output.test())
        ).sorted().toList();
        return new Findings("Compiled methods without retained formal parameter names", offences);
    }

    private Stream<String> offences(String kind, Collection<Path> sources, Path classes) {
        List<Path> compiled = files(classes, CLASS_SUFFIX);
        if (hasJavaFile(sources) && compiled.isEmpty()) {
            return Stream.of(kind + " Java sources produced no class files under " + classes);
        }
        return compiled.stream().flatMap(file -> methodOffences(kind, classes, file));
    }

    private static Stream<String> methodOffences(String kind, Path root, Path file) {
        return parse(file).methods().stream()
            .filter(method -> !method.flags().has(AccessFlag.SYNTHETIC))
            .filter(method -> method.methodTypeSymbol().parameterCount() > 0)
            .map(ParameterMetadataCheck::metadataProblem)
            .flatMap(Optional::stream)
            .map(problem -> kind + ' ' + relative(root, file) + ": " + problem);
    }

    private static Optional<String> metadataProblem(MethodModel method) {
        String signature = method.methodName().stringValue()
            + method.methodTypeSymbol().descriptorString();
        Optional<MethodParametersAttribute> metadata = method.findAttribute(Attributes.methodParameters());
        if (metadata.isEmpty()) {
            return Optional.of(signature + " has no MethodParameters attribute");
        }
        return heldMetadataProblem(method, metadata.orElseThrow(), signature);
    }

    private static Optional<String> heldMetadataProblem(
        MethodModel method, MethodParametersAttribute metadata, String signature
    ) {
        int expected = method.methodTypeSymbol().parameterCount();
        int actual = metadata.parameters().size();
        if (expected != actual) {
            return Optional.of(signature + " records " + actual + " of " + expected + " parameters");
        }
        List<String> unnamed = IntStream.range(0, actual)
            .filter(index -> metadata.parameters().get(index).name().isEmpty())
            .mapToObj(index -> Integer.toString(index + 1))
            .toList();
        return unnamed.isEmpty()
            ? Optional.empty()
            : Optional.of(signature + " has unnamed parameter positions " + String.join(", ", unnamed));
    }

    private static ClassModel parse(Path file) {
        try {
            return ClassFile.of().parse(file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read compiled class " + file, exception);
        }
    }

    private static boolean hasJavaFile(Collection<Path> roots) {
        return roots.stream().flatMap(root -> files(root, JAVA_SUFFIX).stream()).findAny().isPresent();
    }

    private static List<Path> files(Path root, String suffix) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(suffix))
                .sorted()
                .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not inspect files under " + root, exception);
        }
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }
}
