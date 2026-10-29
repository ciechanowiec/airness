package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies which backticked tokens of an instruction file are read as repository paths.
 */
class DocumentReferencesTest {

    @Test
    void readsOnlyFullPathsToConcreteFiles() {
        String markdown = """
            Read `docs/guide.md` and `/scripts/build.sh`, then `src/access/**` and `src/rules/*`.
            Keep `README.md` beside `src/main/java` and run `sh scripts/verify.sh`.
            The schema lives at `https://example.com/schema.json`, and `config/*.yaml` is a glob.
            Generated output goes to `target/site/index.adoc`, and `docs/guide.md` is named twice.
            """;
        assertEquals(
            List.of("docs/guide.md", "scripts/build.sh", "target/site/index.adoc"),
            DocumentReferences.paths(markdown),
            "a bare name, a directory, a glob, a command line and a URL are not paths this reads"
        );
    }

    @Test
    void readsAPathGivenWithATrailingGlobAsTheFileItNames() {
        assertEquals(
            List.of("schema/order.json"), DocumentReferences.paths("See `schema/order.json/**`."),
            "a trailing glob is stripped before the name is judged"
        );
    }

    @Test
    void readsScriptFamiliesAndPnpmRunInvocationsAsNpmScripts() {
        assertEquals(
            List.of("lint:arch", "verify:full", "knip", "build"),
            DocumentReferences.scripts("Run `lint:arch`, `verify:full`, `knip`, `build`, `pnpm run build`.\n"),
            "a backticked token of a script family and a pnpm run name are scripts, a bare word is not"
        );
    }

    @Test
    void readsThePackageAModuleSpecifierStartsWith() {
        assertEquals(
            List.of("@airness/node", "library"),
            List.of(
                DocumentReferences.specifierPackage("@airness/node/tsconfig.json"),
                DocumentReferences.specifierPackage("library/tsconfig.json")
            ),
            "a scoped specifier names its scope and package, an unscoped one its first segment"
        );
    }
}
