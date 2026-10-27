package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Verifies that every instruction file's path references resolve, beside it or at the root.
 */
class DocumentReferenceCheckTest {

    private static final String UNDECLARED = "name an npm script";
    private static final String AGENTS = "AGENTS.md";

    @Test
    void namesAReferenceThatResolvesNowhere() {
        Path root = new GitFixture("docs-stale")
            .write(AGENTS, "Read `docs/guide.md` and `docs/removed.md`.\n")
            .write("docs/guide.md", "Guide.\n")
            .root();
        DocumentReferenceCheck check = check(root, Map.of());
        assertEquals(
            List.of(AGENTS + ": docs/removed.md"),
            Verdicts.offences(check.findings(), "name a file which does not exist"),
            "the removed file is named and the present one is not"
        );
        assertEquals(1, check.scanned(), "the one instruction file is read");
    }

    @Test
    void resolvesANestedDocumentBesideItselfAndAtTheRoot() {
        Path root = new GitFixture("docs-nested")
            .write(AGENTS, "Root rules.\n")
            .write("web/AGENTS.md", "Edit `src/app.ts`, see `docs/guide.md`, never `src/gone.ts`.\n")
            .write("web/src/app.ts", "export {}\n")
            .write("docs/guide.md", "Guide.\n")
            .root();
        assertEquals(
            List.of(Path.of("web", AGENTS) + ": src/gone.ts"),
            Verdicts.offences(check(root, Map.of()).findings(), "name a file which does not exist"),
            "a nested document names its own files and the root's, and only a name found nowhere is stale"
        );
    }

    @Test
    void passesWhenEveryReferenceResolves() {
        Path root = new GitFixture("docs-clean")
            .write(AGENTS, "Follow `.airness/agent-guide.md`.\n")
            .write(".airness/agent-guide.md", "Guide.\n")
            .root();
        assertTrue(Verdicts.clean(check(root, Map.of()).findings()), "the managed guide is present");
    }

    @Test
    void exemptsAModuleSpecifierOfADeclaredDependency() {
        Path root = new GitFixture("docs-specifier")
            .write("web/AGENTS.md", "Extend `@airness/node/tsconfig.json`, never `@gone/pkg/tsconfig.json`.\n")
            .root();
        Map<Path, PackageManifest> manifests = Map.of(
            root.resolve("web"), new PackageManifest(Set.of(), Set.of("@airness/node"), Set.of("@airness/node"))
        );
        assertEquals(
            List.of(Path.of("web", AGENTS) + ": @gone/pkg/tsconfig.json"),
            Verdicts.offences(
                check(root, manifests).findings(),
                "name a file which does not exist"
            ),
            "a subpath of a declared dependency is a specifier, and one of an undeclared package is still a path"
        );
    }

    @Test
    void namesAnNpmScriptNoManifestBesideTheDocumentOrAtTheRootDeclares() {
        Path root = new GitFixture("docs-scripts")
            .write(AGENTS, "Run `lint:arch`.\n")
            .write("web/AGENTS.md", "Run `pnpm run build`, `test:unit`, `lint:gone` and `pnpm run lint:arch`.\n")
            .root();
        Map<Path, PackageManifest> manifests = Map.of(
            root, new PackageManifest(Set.of("lint:arch"), Set.of(), Set.of()),
            root.resolve("web"), new PackageManifest(Set.of("build", "test:unit"), Set.of(), Set.of())
        );
        assertEquals(
            List.of(Path.of("web", AGENTS) + ": lint:gone"),
            Verdicts.offences(
                check(root, manifests).findings(),
                UNDECLARED
            ),
            "a script declared beside the document or at the root resolves, and one declared nowhere does not"
        );
    }

    @Test
    void judgesNoScriptWhereNoManifestIsNear() {
        Path root = new GitFixture("docs-no-node")
            .write(AGENTS, "Run `lint:arch` and `pnpm run build`.\n")
            .root();
        assertTrue(
            Verdicts.clean(check(root, Map.of()).findings()),
            "a repository without a package.json near the document names no npm script"
        );
    }

    private static DocumentReferenceCheck check(Path root, Map<Path, PackageManifest> manifests) {
        return new DocumentReferenceCheck(root, directory -> Optional.ofNullable(manifests.get(directory)));
    }
}
