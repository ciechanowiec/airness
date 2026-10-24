package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies that every instruction file's path references resolve, beside it or at the root.
 */
class DocumentReferenceCheckTest {

    @Test
    void namesAReferenceThatResolvesNowhere() {
        Path root = new GitFixture("docs-stale")
            .write("AGENTS.md", "Read `docs/guide.md` and `docs/removed.md`.\n")
            .write("docs/guide.md", "Guide.\n")
            .root();
        DocumentReferenceCheck check = new DocumentReferenceCheck(root);
        assertEquals(
            List.of("AGENTS.md: docs/removed.md"),
            Verdicts.offences(check.findings(), "name a file which does not exist"),
            "the removed file is named and the present one is not"
        );
        assertEquals(1, check.scanned(), "the one instruction file is read");
    }

    @Test
    void resolvesANestedDocumentBesideItselfAndAtTheRoot() {
        Path root = new GitFixture("docs-nested")
            .write("AGENTS.md", "Root rules.\n")
            .write("web/AGENTS.md", "Edit `src/app.ts`, see `docs/guide.md`, never `src/gone.ts`.\n")
            .write("web/src/app.ts", "export {}\n")
            .write("docs/guide.md", "Guide.\n")
            .root();
        assertEquals(
            List.of(Path.of("web", "AGENTS.md") + ": src/gone.ts"),
            Verdicts.offences(new DocumentReferenceCheck(root).findings(), "name a file which does not exist"),
            "a nested document names its own files and the root's, and only a name found nowhere is stale"
        );
    }

    @Test
    void passesWhenEveryReferenceResolves() {
        Path root = new GitFixture("docs-clean")
            .write("AGENTS.md", "Follow `.airness/agent-guide.md`.\n")
            .write(".airness/agent-guide.md", "Guide.\n")
            .root();
        assertTrue(Verdicts.clean(new DocumentReferenceCheck(root).findings()), "the managed guide is present");
    }
}
