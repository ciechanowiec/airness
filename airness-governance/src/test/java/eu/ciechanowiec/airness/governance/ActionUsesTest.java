package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * A {@code uses} value is read as an action in another repository, as something the Node runtime rule passes over,
 * or as a value whose action cannot be read, and only a full commit is taken as a ref that never moves.
 */
class ActionUsesTest {

    private static final String COMMIT = "11bd71901bbe5b1630ceea73d27597364c9af683";

    private static ActionReference remote(String written) {
        return assertInstanceOf(ActionReference.class, ActionUses.read(written), written);
    }

    private static void unjudgeable(String written) {
        assertInstanceOf(ActionUse.Unjudgeable.class, ActionUses.read(written), written);
    }

    // The values that are not read as the kind, which a test expects to be none.
    private static List<String> notReadAs(Class<? extends ActionUse> kind, Collection<String> written) {
        return written.stream().filter(value -> !kind.isInstance(ActionUses.read(value))).toList();
    }

    @Test
    void readsAnActionAtTheRootOfItsRepository() {
        ActionReference action = remote("actions/checkout@" + COMMIT);
        assertEquals(new ActionReference("actions", "checkout", "", COMMIT), action);
        assertEquals("actions/checkout", action.slug());
        assertEquals("actions/checkout@" + COMMIT, action.toString());
        assertTrue(action.immutable());
    }

    @Test
    void readsAnActionInADirectoryOfItsRepository() {
        ActionReference action = remote("github/codeql-action/upload-sarif/nested@v3.1");
        assertEquals(new ActionReference("github", "codeql-action", "upload-sarif/nested", "v3.1"), action);
        assertEquals("github/codeql-action/upload-sarif/nested@v3.1", action.toString());
    }

    @Test
    void takesOnlyAFullLowercaseCommitAsARefThatNeverMoves() {
        assertFalse(remote("actions/checkout@v4").immutable(), "a tag");
        assertFalse(remote("actions/checkout@main").immutable(), "a branch");
        assertFalse(remote("actions/checkout@releases/v4").immutable(), "a branch with a slash");
        assertFalse(remote("actions/checkout@" + COMMIT.substring(1)).immutable(), "thirty-nine digits");
        assertFalse(remote("actions/checkout@%sa".formatted(COMMIT)).immutable(), "forty-one digits");
        assertFalse(remote("actions/checkout@11bd719").immutable(), "a short commit");
        assertFalse(remote("actions/checkout@" + COMMIT.toUpperCase(Locale.ROOT)).immutable(), "uppercase");
    }

    @Test
    void passesOverLocalAndDockerActionsAndReusableWorkflows() {
        List<String> passed = List.of(
            "./.github/actions/install-maven", "./", "docker://alpine:3.21",
            "octo/workflows/.github/workflows/build.yml@v1", "octo/workflows/.github/workflows/build.yaml@" + COMMIT
        );
        assertEquals(List.of(), notReadAs(ActionUse.Skipped.class, passed));
    }

    @Test
    void readsAnActionUnderAGithubDirectoryThatIsNotAWorkflow() {
        assertEquals(".github/actions/setup", remote("octo/tools/.github/actions/setup@v1").path());
        assertEquals(".github/workflows/nested/x.yml", remote("octo/tools/.github/workflows/nested/x.yml@v1").path());
    }

    @Test
    void cannotJudgeAnExpression() {
        ActionUse use = ActionUses.read("${{ matrix.action }}");
        assertEquals(
            new ActionUse.Unjudgeable(
                "an expression, which GitHub never substitutes in a uses key", "the action written literally"
            ),
            use
        );
        unjudgeable("actions/checkout@${{ inputs.ref }}");
    }

    @Test
    void cannotJudgeAValueOutsideTheFormGithubReads() {
        assertEquals(
            new ActionUse.Unjudgeable("not written in the form GitHub reads an action in", ActionUses.FORM),
            ActionUses.read("actions/checkout")
        );
        unjudgeable("checkout@v4");
        unjudgeable("/checkout@v4");
        unjudgeable("actions/@v4");
        unjudgeable("actions/checkout@");
        unjudgeable("actions/checkout@v4 extra");
        unjudgeable(">-");
    }

    @Test
    void cannotJudgeAPartThatClimbsOutOfItsDirectory() {
        List<String> climbing = List.of(
            "actions/../evil@v1", "actions/checkout/../../evil@v1", "actions/checkout/./sub@v1",
            "actions/checkout//sub@v1", "actions/checkout/sub/@v1", "actions/checkout@../../v1", "actions/..@v1",
            "actions/.@v1", "actions/checkout@v1:evil", "actions/checkout@v1%2f.."
        );
        assertEquals(List.of(), notReadAs(ActionUse.Unjudgeable.class, climbing));
    }
}
