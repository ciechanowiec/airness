package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every committable text file indents as the {@code .editorconfig} it answers to declares, and only the
 * files a generator owns, those a file role exempts, and binaries stay unread.
 */
class IndentationCheckTest {

    private static final String EDITORCONFIG = ".editorconfig";
    private static final String CONFIGURATION = """
        root = true

        [*]
        indent_style = space
        indent_size = 4

        [*.{yml,yaml}]
        indent_size = 2

        [{Makefile,*.mk,.gitmodules,*.go}]
        indent_style = tab

        [*.txt]
        indent_style = unset
        indent_size = unset
        """;
    private static final String HEADLINE = "Indentation that departs";

    @Test
    void passesATreeThatIndentsAsItsEditorconfigDeclares() {
        Path root = new GitFixture("indentation-clean")
            .write(EDITORCONFIG, CONFIGURATION)
            .write("src/Main.java", "class Main {\n    void run() {\n    }\n}\n")
            .write("application.yml", "spring:\n  datasource:\n    url: x\n")
            .write("Makefile", "build:\n\tmvn verify\n")
            .write("tools/rules.mk", "lint:\n\tmvn validate\n")
            .write(".gitmodules", "[submodule \"x\"]\n\tpath = x\n")
            .write("cmd/main.go", "package main\n\nfunc main() {\n\tprintln()\n}\n")
            .write("notes.txt", "\tfree\n   form\n")
            .root();
        IndentationCheck check = new IndentationCheck(root, List.of(), _ -> false);
        assertAll(
            () -> assertTrue(Verdicts.clean(check.findings()), () -> check.findings().toString()),
            () -> assertEquals(8, check.scanned(), "every committable file is in scope")
        );
    }

    @Test
    void reportsAFileThatIndentsWithATabWhereItsStyleIsSpace() {
        Path root = new GitFixture("indentation-tab")
            .write(EDITORCONFIG, CONFIGURATION)
            .write("src/Main.java", "class Main {\n\tvoid run() {\n\t}\n}\n")
            .root();
        assertEquals(
            List.of("src/Main.java: indent_style = space, but line 2 indents with a tab"),
            offences(new IndentationCheck(root, List.of(), _ -> false))
        );
    }

    @Test
    void reportsAMakefileThatIndentsWithSpaces() {
        Path root = new GitFixture("indentation-makefile")
            .write(EDITORCONFIG, CONFIGURATION)
            .write("Makefile", "build:\n    mvn verify\n")
            .root();
        assertEquals(
            List.of("Makefile: indent_style = tab, but line 2 indents with a space"),
            offences(new IndentationCheck(root, List.of(), _ -> false))
        );
    }

    @Test
    void reportsAYamlFileThatNestsByAnotherWidthThanItsIndentSize() {
        Path root = new GitFixture("indentation-yaml")
            .write(EDITORCONFIG, CONFIGURATION)
            .write("src/main/resources/application.yml", "spring:\n    datasource:\n        url: x\n")
            .write("compose.yaml", "services:\n   database:\n     image: postgres:17.5\n")
            .root();
        assertEquals(
            List.of(
                "compose.yaml: indent_size = 2, but line 2 nests 3 columns deeper than line 1",
                "src/main/resources/application.yml: indent_size = 2, but line 2 nests 4 columns deeper than line 1"
            ),
            offences(new IndentationCheck(root, List.of(), _ -> false)).stream().sorted().toList()
        );
    }

    @Test
    void reportsTheEarlierOfTwoDeparturesInOneFile() {
        Path root = new GitFixture("indentation-earliest")
            .write(EDITORCONFIG, CONFIGURATION)
            .write("late-tab.yml", "a:\n    b: 1\nc:\n\td: 1\n")
            .write("early-tab.yml", "a:\n\tb: 1\nc:\n    d: 1\n")
            .root();
        assertEquals(
            List.of(
                "early-tab.yml: indent_style = space, but line 2 indents with a tab",
                "late-tab.yml: indent_size = 2, but line 2 nests 4 columns deeper than line 1"
            ),
            offences(new IndentationCheck(root, List.of(), _ -> false)).stream().sorted().toList()
        );
    }

    @Test
    void holdsAFileToTheEditorconfigNearestToIt() {
        Path root = new GitFixture("indentation-nearest")
            .write(EDITORCONFIG, CONFIGURATION)
            .write("deploy/.editorconfig", "[*.yml]\nindent_size = 4\n")
            .write("deploy/values.yml", "image:\n    tag: 1\n")
            .write("values.yml", "image:\n    tag: 1\n")
            .root();
        assertEquals(
            List.of("values.yml: indent_size = 2, but line 2 nests 4 columns deeper than line 1"),
            offences(new IndentationCheck(root, List.of(), _ -> false))
        );
    }

    @Test
    void verifiesNothingWhereNoEditorconfigDeclaresIndentation() {
        Path root = new GitFixture("indentation-undeclared")
            .write("Main.java", "class Main {\n\tvoid run() {}\n}\n")
            .write("application.yml", "spring:\n    datasource: x\n")
            .root();
        assertTrue(Verdicts.clean(new IndentationCheck(root, List.of(), _ -> false).findings()));
    }

    @Test
    void leavesGeneratedExemptAndBinaryFilesUnread() {
        Path root = new GitFixture("indentation-unread")
            .write(EDITORCONFIG, CONFIGURATION)
            .write("src/generated-types.ts", "export type A = {\n\ta: string\n}\n")
            .write("data/table.tsv", "\tsecond column\n")
            .write("image.bin", new byte[] {0, '\t', 'a', '\n', '\t', 'b'})
            .root();
        IndentationCheck check = new IndentationCheck(
            root, List.of("src/generated-types.ts"), path -> path.endsWith(".tsv")
        );
        assertAll(
            () -> assertTrue(Verdicts.clean(check.findings()), () -> check.findings().toString()),
            () -> assertEquals(1L, check.generated(), "the generated file is counted"),
            () -> assertEquals(2, check.scanned(), "the exempt file is not in scope, and the binary is read as none")
        );
    }

    private static List<String> offences(IndentationCheck check) {
        return Verdicts.offences(check.findings(), HEADLINE);
    }
}
