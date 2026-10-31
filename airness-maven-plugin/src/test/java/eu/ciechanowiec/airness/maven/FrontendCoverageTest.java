package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.SneakyThrows;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every authored script and stylesheet of a module lies where a declared node project's gates select it.
 */
class FrontendCoverageTest {

    private static final String UNDECLARED = " lies in no declared node project; if it is a third-party distribution, "
        + "take it from a dependency (a WebJar, or an npm package the build copies) instead of keeping a copy";
    private static final String STATIC = "src/main/resources/static/";
    private static final String MANIFEST = "package.json";
    private static final String TOOLING = "frontend";
    private static final String EMPTY = "{}\n";

    @TempDir
    private Path module;

    @SneakyThrows
    private void write(String file, String content) {
        Path path = this.module.resolve(file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private void write(String file) {
        this.write(file, "x\n");
    }

    private FrontendCoverage.Walk walk() {
        return new FrontendCoverage.Walk(this.module, this.module, List.of(this.module.resolve("target")));
    }

    private FrontendCoverage coverage(@Nullable String declared, Supplier<List<String>> generated) {
        return new FrontendCoverage(
            this.walk(), NodeProjects.of(this.module, declared), generated, Map.of()
        );
    }

    private List<String> offences(@Nullable String declared) {
        return this.coverage(declared, List::of).findings().getFirst().offences();
    }

    private List<String> excusedOffences(Map<String, String> excused) {
        return new FrontendCoverage(
            this.walk(), NodeProjects.of(this.module, null), List::of, excused
        ).findings().getFirst().offences();
    }

    @Test
    void passesOverAFileTheHarnessExcusesInItsOwnModule() {
        this.write(STATIC + "css/report.css");
        this.write(STATIC + "css/theme.css");
        assertEquals(
            List.of(STATIC + "css/theme.css" + UNDECLARED),
            this.excusedOffences(Map.of(STATIC + "css/report.css", "no node project can check it")),
            "only the file the harness names is excused, never its neighbours"
        );
    }

    @Test
    void refusesAnExcuseWhoseFileIsGone() {
        assertEquals(
            List.of(
                STATIC + "css/report.css is excused by the harness, but no unchecked authored file is there any "
                    + "more; remove the entry"
            ),
            this.excusedOffences(Map.of(STATIC + "css/report.css", "no node project can check it")),
            "a stale exception is refused rather than kept"
        );
    }

    @Test
    void refusesAnExcuseForAFileANodeProjectNowChecks() {
        this.write(MANIFEST, EMPTY);
        this.write("src/report.css");
        List<String> offences = new FrontendCoverage(
            this.walk(), NodeProjects.of(this.module, "."), List::of, Map.of(
                "src/report.css", "no node project can check it"
            )
        ).findings().getFirst().offences();
        assertEquals(
            List.of(
                "src/report.css is excused by the harness, but no unchecked authored file is there any more; "
                    + "remove the entry"
            ),
            offences,
            "an exception outlives its reason once a gate checks the file"
        );
    }

    @Test
    void namesEveryAuthoredResourceOfAModuleWithoutAManifest() {
        this.write(STATIC + "js/browse.js");
        this.write(STATIC + "css/theme.css");
        this.write("src/main/java/App.java");
        assertEquals(
            List.of(STATIC + "css/theme.css" + UNDECLARED, STATIC + "js/browse.js" + UNDECLARED),
            this.offences(null),
            "a Java module serving a script and a stylesheet has a frontend whether or not it has a manifest"
        );
    }

    @Test
    void namesAResourceInsideADeclaredProjectButOutsideItsCheckedRoots() {
        this.write(TOOLING + '/' + MANIFEST, EMPTY);
        this.write(TOOLING + "/src/browse.ts");
        this.write(TOOLING + "/tests/browse.spec.ts");
        this.write(TOOLING + "/eslint.config.mjs");
        this.write(TOOLING + "/public/theme.css");
        List<String> offences = this.offences(TOOLING);
        assertEquals(1, offences.size(), "sources, tests and top-level tooling files are checked: " + offences);
        assertTrue(
            offences.getFirst().startsWith(
                TOOLING + "/public/theme.css lies in the node project frontend but outside its checked source roots "
                    + "[scripts, src, tests]"
            ),
            "a manifest beside a file does not make a gate read it: " + offences
        );
    }

    @Test
    void acceptsTheResourcesAModuleRootProjectAddsToItsSourceRoots() {
        this.write(MANIFEST, "{\"airness\": {\"sourceRoots\": [\"" + STATIC + "\"]}}\n");
        this.write(STATIC + "css/theme.css");
        this.write(STATIC + "css/forms.scss");
        assertEquals(List.of(), this.offences("."), "a stylesheet under a declared source root is checked where it is");
    }

    @Test
    void readsAVendorDirectoryAndAMinifiedNameAsAuthored() {
        this.write(STATIC + "vendor/htmx.min.js");
        assertEquals(
            List.of(STATIC + "vendor/htmx.min.js" + UNDECLARED), this.offences(null),
            "a directory name is no evidence of who wrote a file"
        );
    }

    @Test
    void passesOverTheFilesAGeneratorOwns() {
        this.write(STATIC + "generated/bundle.js");
        this.write(STATIC + "generated/nested/chunk.js");
        List<String> offences = this.coverage(null, () -> List.of(STATIC + "generated/**"))
            .findings().getFirst().offences();
        assertEquals(List.of(), offences, "declared generator output is not authored source");
    }

    @Test
    void refusesStylesheetDialectsNoGateReadsWhereverTheyLie() {
        this.write(MANIFEST, "{\"airness\": {\"sourceRoots\": [\".\"]}}\n");
        this.write("src/theme.less");
        this.write("src/theme.sass");
        assertEquals(
            List.of(
                "src/theme.less is written in less, which no Airness gate reads; write it as CSS or SCSS",
                "src/theme.sass is written in sass, which no Airness gate reads; write it as CSS or SCSS"
            ),
            this.offences(".")
        );
    }

    @Test
    void readsAFileOfAWorkspaceMemberAgainstThatMembersRoots() {
        this.write(TOOLING + '/' + MANIFEST, EMPTY);
        this.write(TOOLING + "/pnpm-workspace.yaml", "packages:\n  - packages/*\n");
        this.write(TOOLING + "/packages/theme/" + MANIFEST, "{\"airness\": {\"sourceRoots\": [\"styles\"]}}\n");
        this.write(TOOLING + "/packages/theme/styles/base.css");
        assertEquals(List.of(), this.offences(TOOLING), "a member's own source roots decide what its gates read");
    }

    @Test
    void readsNoGeneratedDeclarationInAModuleWithoutAFrontend() {
        this.write("src/main/java/App.java");
        FrontendCoverage coverage = this.coverage(
            null, () -> {
                throw new AssertionError("the repository's models are read only when there is a file to judge");
            }
        );
        assertEquals(0, coverage.inventoried(), "a backend module has no frontend file");
    }

    @Test
    void passesOverInstalledPackagesAndTheBuildDirectory() {
        this.write(TOOLING + '/' + MANIFEST, EMPTY);
        this.write(TOOLING + "/node_modules/htmx.org/dist/htmx.js");
        this.write("target/classes/static/app.js");
        FrontendCoverage coverage = this.coverage(TOOLING, List::of);
        assertEquals(0, coverage.inventoried(), "installed packages and build output are nobody's source");
        assertTrue(coverage.findings().getFirst().clean(), "nothing is left to report");
    }
}
