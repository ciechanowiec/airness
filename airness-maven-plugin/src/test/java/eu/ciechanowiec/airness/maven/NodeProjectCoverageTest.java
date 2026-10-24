package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every package.json in a module is declared or a member of a declared workspace, and every declaration
 * names a node project inside the module.
 */
class NodeProjectCoverageTest {

    private static final String MANIFEST = "package.json";
    private static final String UNCHECKED = " is a node project that no gate checks; add it to airness.node.projects";

    @TempDir
    private Path module;

    @SneakyThrows
    private void manifest(String directory) {
        Path project = Files.createDirectories(this.module.resolve(directory));
        Files.writeString(project.resolve(MANIFEST), "{}\n");
    }

    private List<String> offences(@Nullable String declared, List<Path> skipped) {
        return new NodeProjectCoverage(this.module, NodeProjects.of(this.module, declared), skipped)
            .findings().getFirst().offences();
    }

    @Test
    @SneakyThrows
    void reportsAPackageJsonInAModuleThatDeclaresNothing() {
        this.manifest("ui");
        this.manifest(".");
        assertEquals(List.of(MANIFEST + UNCHECKED, "ui/package.json" + UNCHECKED), this.offences(null, List.of()));
    }

    @Test
    @SneakyThrows
    void acceptsDeclaredProjectsAndTheMembersTheirWorkspacesList() {
        this.manifest("src/main/webapp/admin");
        this.manifest("ui/storefront");
        this.manifest("ui/storefront/packages/theme");
        Files.writeString(this.module.resolve("ui/storefront/pnpm-workspace.yaml"), "packages:\n  - packages/*\n");
        this.manifest("ui/storefront/fixtures/sample");
        assertEquals(
            List.of("ui/storefront/fixtures/sample/package.json" + UNCHECKED),
            this.offences("src/main/webapp/admin,ui/storefront", List.of()),
            "a directory inside a declared root that its workspace does not list is still unchecked"
        );
    }

    @Test
    @SneakyThrows
    void passesOverInstalledPackagesBuildOutputGitAndChildModules() {
        this.manifest(".");
        this.manifest("node_modules/left-pad");
        this.manifest("target/classes/static");
        this.manifest(".git/hooks");
        this.manifest("backend/ui");
        this.manifest("output/static");
        this.manifest("dist/cjs");
        this.manifest(".next");
        this.manifest("build/esm");
        NodeProjectCoverage coverage = new NodeProjectCoverage(
            this.module, NodeProjects.of(this.module, "."), List.of(
                this.module.resolve("backend"), this.module.resolve("output")
            )
        );
        assertEquals(List.of(), coverage.findings().getFirst().offences());
        assertEquals(1, coverage.found(), "only the module's own manifest was walked");
    }

    @Test
    void reportsADeclarationWithoutAManifestAndOneOutsideTheModule() {
        assertEquals(
            List.of(
                "src/main/frontend has no package.json; fix airness.node.projects",
                "../web lies outside the module; list only directories inside it in airness.node.projects"
            ),
            this.offences("src/main/frontend,../web", List.of())
        );
    }

    @Test
    @SneakyThrows
    void readsOnlyWhatACommitWouldCarryInsideAWorkingTree() {
        Process init = new ProcessBuilder("git", "init", "--quiet").directory(this.module.toFile()).start();
        assertEquals(0, init.waitFor(), "git init");
        Files.writeString(this.module.resolve(".gitignore"), "out/\n");
        this.manifest(".");
        this.manifest("out/cjs");
        this.manifest("ui");
        this.manifest("backend/web");
        assertEquals(
            List.of("ui/package.json" + UNCHECKED),
            this.offences(".", List.of(this.module.resolve("backend"))),
            "ignored output and a child module's directory are not this module's node projects"
        );
    }
}
