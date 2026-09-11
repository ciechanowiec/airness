package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShellSourceHintsTest {

    @Test
    void acceptsARealImportAndRejectsEmptyOrUnrelatedSubstitutes() {
        Path root = new GitFixture("shell-source-hints")
            .write("library.sh", "printf '%s\\n' ready\n")
            .write("other.sh", "exit 0\n")
            .write("good.sh", "# shellcheck source=library.sh\n. \"${root}/library.sh\"\n")
            .write("empty.sh", "# shellcheck source=/dev/null\n. library.sh\n")
            .write("unrelated.sh", "# shellcheck source=other.sh\n. library.sh\n")
            .write("missing.sh", "# shellcheck source=absent.sh\n. absent.sh\n")
            .write("borrowed.sh", "# shellcheck source=other.sh\n. library.sh\n. other.sh\n")
            .root();
        assertEquals(List.of(), ShellSourceHints.problems(root, root.resolve("good.sh")));
        assertEquals(1, ShellSourceHints.problems(root, root.resolve("empty.sh")).size());
        assertEquals(1, ShellSourceHints.problems(root, root.resolve("unrelated.sh")).size());
        assertEquals(1, ShellSourceHints.problems(root, root.resolve("missing.sh")).size());
        assertEquals(1, ShellSourceHints.problems(root, root.resolve("borrowed.sh")).size());
    }
}
