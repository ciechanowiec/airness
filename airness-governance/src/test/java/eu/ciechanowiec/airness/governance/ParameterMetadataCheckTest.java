package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.attribute.MethodParameterInfo;
import java.lang.classfile.attribute.MethodParametersAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParameterMetadataCheckTest {

    private static final String CLASS = "com/example/Sample.class";
    private static final String MAIN = "main";
    private static final String TEST = "test";

    @TempDir
    private Path directory;

    @Test
    void acceptsNamedProductionAndTestParameters() {
        Path productionSources = this.source(MAIN);
        Path testSources = this.source(TEST);
        Path production = this.compiled(MAIN, named(1));
        Path tests = this.compiled(TEST, named(2));
        assertTrue(
            this.check(productionSources, testSources, production, tests).findings().clean()
        );
    }

    @Test
    void rejectsAMethodWithoutTheAttribute() {
        Path sources = this.source(MAIN);
        Path classes = this.compiled(MAIN, method(1, Optional.empty(), ClassFile.ACC_PUBLIC));
        assertEquals(
            List.of(
                "production com/example/Sample.class: call(Ljava/lang/String;)V"
                    + " has no MethodParameters attribute"
            ),
            this.check(sources, this.directory.resolve("no-tests"), classes, this.directory.resolve(TEST))
                .findings()
                .offences()
        );
    }

    @Test
    void rejectsAnUnnamedFormalParameter() {
        MethodParametersAttribute metadata = MethodParametersAttribute.of(
            MethodParameterInfo.of(Optional.empty())
        );
        Path classes = this.compiled(
            MAIN, method(1, Optional.of(metadata), ClassFile.ACC_PUBLIC)
        );
        assertEquals(
            List.of(
                "production com/example/Sample.class: call(Ljava/lang/String;)V"
                    + " has unnamed parameter positions 1"
            ),
            this.check(
                this.source(MAIN), this.directory.resolve("no-tests"), classes,
                this.directory.resolve(TEST)
            ).findings().offences()
        );
    }

    @Test
    void rejectsAnAttributeWhoseCountDiffersFromTheDescriptor() {
        MethodParametersAttribute metadata = MethodParametersAttribute.of(
            MethodParameterInfo.of(Optional.of("first"))
        );
        Path classes = this.compiled(
            MAIN, method(2, Optional.of(metadata), ClassFile.ACC_PUBLIC)
        );
        assertEquals(
            List.of(
                "production com/example/Sample.class: call(Ljava/lang/String;Ljava/lang/String;)V"
                    + " records 1 of 2 parameters"
            ),
            this.check(
                this.source(MAIN), this.directory.resolve("no-tests"), classes,
                this.directory.resolve(TEST)
            ).findings().offences()
        );
    }

    @Test
    void ignoresParameterlessAndSyntheticMethods() {
        byte[] parameterless = method(0, Optional.empty(), ClassFile.ACC_PUBLIC);
        byte[] synthetic = method(
            1, Optional.empty(), ClassFile.ACC_PUBLIC | ClassFile.ACC_SYNTHETIC
        );
        Path production = this.compiled("parameterless", parameterless);
        Path tests = this.compiled("synthetic", synthetic);
        assertTrue(
            this.check(
                this.directory.resolve("no-main-sources"), this.directory.resolve("no-test-sources"),
                production, tests
            ).findings().clean()
        );
    }

    @Test
    void rejectsJavaSourcesThatProducedNoClassFiles() {
        assertEquals(
            List.of(
                "production Java sources produced no class files under "
                    + this.directory.resolve("missing-classes")
            ),
            this.check(
                this.source(MAIN), this.directory.resolve("no-tests"),
                this.directory.resolve("missing-classes"), this.directory.resolve(TEST)
            ).findings().offences()
        );
    }

    @Test
    void rejectsMalformedCompilerOutput() {
        Path sources = this.source(MAIN);
        Path classes = this.compiled(
            MAIN, "not a class file".getBytes(StandardCharsets.UTF_8)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> this.check(
                sources, this.directory.resolve("no-tests"), classes, this.directory.resolve(TEST)
            ).findings()
        );
    }

    private ParameterMetadataCheck check(
        Path productionSources, Path testSources, Path production, Path tests
    ) {
        return new ParameterMetadataCheck(
            List.of(productionSources), List.of(testSources), new ModuleOutput(production, tests)
        );
    }

    @SneakyThrows
    private Path source(String tree) {
        Path root = this.directory.resolve(tree + "-sources");
        Path source = root.resolve("com/example/Sample.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "class Sample {}\n");
        return root;
    }

    @SneakyThrows
    private Path compiled(String tree, byte[] bytes) {
        Path root = this.directory.resolve(tree + "-classes");
        Path file = root.resolve(CLASS);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
        return root;
    }

    private static byte[] named(int parameters) {
        List<MethodParameterInfo> names = IntStream.range(0, parameters)
            .mapToObj(index -> MethodParameterInfo.of(Optional.of("parameter%d".formatted(index))))
            .toList();
        return method(
            parameters, Optional.of(MethodParametersAttribute.of(names)), ClassFile.ACC_PUBLIC
        );
    }

    private static byte[] method(
        int parameters, Optional<MethodParametersAttribute> metadata, int flags
    ) {
        List<ClassDesc> types = IntStream.range(0, parameters)
            .mapToObj(_ -> ConstantDescs.CD_String)
            .toList();
        return ClassFile.of().build(
            ClassDesc.of("com.example.Sample"),
            type -> type.withMethod(
                "call", MethodTypeDesc.of(ConstantDescs.CD_void, types), flags | ClassFile.ACC_STATIC,
                declaration -> {
                    metadata.ifPresent(declaration::with);
                    declaration.withCode(CodeBuilder::return_);
                }
            )
        );
    }
}
