package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/*
 * The two rules that read the manifest of the finished archive rather than its entries. Each fixture
 * writes its own manifest, because an archive stream writes none on its own, and every archive here is
 * a real one the check opens as it opens a shipped artifact.
 */
class ArtifactContentManifestTest {

    private static final String VERSIONS = "Versioned classes the manifest does not declare";
    private static final String NATIVE_ACCESS = "Restricted native access the manifest does not declare";
    private static final String MAIN = "main";
    private static final String TEST = "test";
    private static final String MANIFEST = "META-INF/MANIFEST.MF";
    private static final String PLAIN = "Manifest-Version: 1.0\n";
    private static final String RUNNABLE = "Manifest-Version: 1.0\nMain-Class: com.example.Example\n";
    private static final String VERSIONED = "META-INF/versions/17/com/example/Example.class";
    private static final String CLASS = "com/example/Example.class";
    private static final String BYTECODE = "bytecode";
    private static final String REACHING = "bytecode java/lang/foreign/Linker bytecode";

    @TempDir
    private Path directory;

    @Test
    void namesAVersionedClassTheManifestLeavesUndeclared() {
        Path jar = this.jar(Map.of(MANIFEST, PLAIN, VERSIONED, BYTECODE));
        assertEquals(
            List.of(VERSIONED),
            offences(this.check(jar), VERSIONS),
            "a runtime reads a versioned class only where the manifest declares the archive"
        );
    }

    @Test
    void acceptsAVersionedClassTheManifestDeclares() {
        Path jar = this.jar(
            Map.of(MANIFEST, PLAIN + "Multi-Release: true\n", VERSIONED, BYTECODE)
        );
        assertEquals(List.of(), offences(this.check(jar), VERSIONS), "a declared archive answers");
    }

    /*
     * The format reads the declared value without regard to case, so a manifest written the other way
     * answers as well as the ordinary one.
     */
    @Test
    void readsTheDeclarationWhateverCaseItIsWrittenIn() {
        Path jar = this.jar(
            Map.of(MANIFEST, PLAIN + "Multi-Release: TRUE\n", VERSIONED, BYTECODE)
        );
        assertEquals(List.of(), offences(this.check(jar), VERSIONS), "the value is read case blind");
    }

    /*
     * An archive carrying no manifest at all declares nothing, which is the answer an archive whose
     * manifest omits the line gives as well.
     */
    @Test
    void readsAnArchiveCarryingNoManifestAsDeclaringNothing() {
        Path jar = this.jar(Map.of(VERSIONED, BYTECODE));
        assertEquals(
            List.of(VERSIONED),
            offences(this.check(jar), VERSIONS),
            "an archive with no manifest declares no versioned class either"
        );
    }

    @Test
    void acceptsAnArchiveThatShipsNoVersionedClass() {
        Path jar = this.jar(Map.of(MANIFEST, PLAIN, CLASS, BYTECODE));
        assertEquals(
            List.of(),
            offences(this.check(jar), VERSIONS),
            "an archive declaring nothing owes nothing where it ships no versioned class"
        );
    }

    @Test
    void namesAClassOfThisModuleThatReachesTheOperatingSystemUndeclared() {
        this.compiled(CLASS, REACHING);
        Path jar = this.jar(Map.of(MANIFEST, RUNNABLE, CLASS, REACHING));
        assertEquals(
            List.of(CLASS),
            offences(this.check(jar), NATIVE_ACCESS),
            "an undeclared restricted call writes warnings before the application says anything"
        );
    }

    @Test
    void acceptsTheSameArchiveWhereTheManifestDeclaresNativeAccess() {
        this.compiled(CLASS, REACHING);
        Path jar = this.jar(
            Map.of(MANIFEST, RUNNABLE + "Enable-Native-Access: ALL-UNNAMED\n", CLASS, REACHING)
        );
        assertEquals(
            List.of(), offences(this.check(jar), NATIVE_ACCESS), "a declared archive answers"
        );
    }

    /*
     * Only a runnable archive can carry the declaration, so a library says nothing about it and
     * whoever launches the library answers instead.
     */
    @Test
    void leavesALibraryAloneBecauseItCannotCarryTheDeclaration() {
        this.compiled(CLASS, REACHING);
        Path jar = this.jar(Map.of(MANIFEST, PLAIN, CLASS, REACHING));
        assertEquals(
            List.of(),
            offences(this.check(jar), NATIVE_ACCESS),
            "an archive naming no main class is answered for by whoever launches it"
        );
    }

    @Test
    void readsTheBytesThisModuleCompiledAndNotOnesADependencyPublished() {
        this.compiled(CLASS, BYTECODE);
        Path jar = this.jar(
            Map.of(MANIFEST, RUNNABLE, CLASS, BYTECODE, "com/vendor/Reaching.class", REACHING)
        );
        assertEquals(
            List.of(),
            offences(this.check(jar), NATIVE_ACCESS),
            "a restricted call inside a vendored library is that library's own question"
        );
    }

    private List<Findings> check(Path jar) {
        return new ArtifactContentCheck(
            jar,
            new ModuleOutput(this.directory.resolve(MAIN), this.directory.resolve(TEST)),
            this.directory,
            List.of()
        ).findings();
    }

    private void compiled(String entry, String content) {
        new ArchiveFixture(this.directory).output(MAIN, entry, content);
    }

    private Path jar(Map<String, String> entries) {
        return new ArchiveFixture(this.directory).jar(entries);
    }

    private static List<String> offences(Collection<Findings> findings, String headline) {
        return ArchiveFixture.offences(findings, headline);
    }
}
