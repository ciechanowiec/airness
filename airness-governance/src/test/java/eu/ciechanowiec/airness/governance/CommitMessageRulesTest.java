package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The commit-message policy accepts a well-formed Conventional Commit and rejects each way a message
 * can break the rules: a bad header, a too-short or period-ending subject, a junk word, a missing body
 * on a non-trivial change. A revert header, and a commit that git recorded with two parents, are exempt
 * from the header shape alone, so the attribution ban that {@link CommitAttributionRulesTest} pins still
 * reaches both.
 */
class CommitMessageRulesTest {

    private static final DiffStat TRIVIAL = new DiffStat(1, 3);
    private static final DiffStat NON_TRIVIAL = new DiffStat(9, 200);

    @Test
    void acceptsAWellFormedTrivialCommit() {
        CommitMessage message = new CommitMessage("fix(proxy): guard against an empty upstream reply", "");
        assertTrue(CommitMessageRules.validate(message, TRIVIAL).isEmpty());
    }

    @Test
    void rejectsAnUnknownHeaderType() {
        CommitMessage message = new CommitMessage("improve: make the daemon faster overall", "");
        assertFalse(CommitMessageRules.validate(message, TRIVIAL).isEmpty());
    }

    @Test
    void rejectsATooShortSubject() {
        CommitMessage message = new CommitMessage("fix: too short", "");
        assertFalse(CommitMessageRules.validate(message, TRIVIAL).isEmpty());
    }

    @Test
    void rejectsATrailingPeriod() {
        CommitMessage message = new CommitMessage("docs: document the verification command.", "");
        assertFalse(CommitMessageRules.validate(message, TRIVIAL).isEmpty());
    }

    @Test
    void rejectsAJunkWord() {
        CommitMessage message = new CommitMessage("chore: wip on the reload watcher path", "");
        assertFalse(CommitMessageRules.validate(message, TRIVIAL).isEmpty());
    }

    @Test
    void requiresABodyForANonTrivialChange() {
        CommitMessage message = new CommitMessage("refactor(config): split the effective config merge", "");
        assertFalse(CommitMessageRules.validate(message, NON_TRIVIAL).isEmpty());
    }

    @Test
    void acceptsANonTrivialChangeThatCarriesABody() {
        CommitMessage message = new CommitMessage(
            "refactor(config): split the effective config merge",
            "The merge grew past the complexity cap, so it moves into smaller value objects."
        );
        assertTrue(CommitMessageRules.validate(message, NON_TRIVIAL).isEmpty());
    }

    @Test
    void refusesTheHeaderGitWritesForAMerge() {
        CommitMessage message = new CommitMessage("Merge branch 'release' into 'main'", "");
        assertFalse(
            CommitMessageRules.validate(message, NON_TRIVIAL).isEmpty(),
            "every tool that makes a merge offers to edit its message, so this form is a choice"
        );
    }

    @Test
    void rejectsAMergeLikeHeaderOnAnOrdinaryCommit() {
        CommitMessage message = new CommitMessage("Merge anything at all", "");
        assertFalse(CommitMessageRules.validate(message, TRIVIAL).isEmpty());
    }

    @Test
    void refusesTheHeaderGitWritesForARevert() {
        CommitMessage message = new CommitMessage(
            "Revert \"feat(cli): add the reload subcommand to the root\"",
            "This reverts commit 0123456789abcdef0123456789abcdef01234567."
        );
        assertFalse(
            CommitMessageRules.validate(message, NON_TRIVIAL).isEmpty(),
            "git revert --edit reaches a conforming message, so its default is a choice like any other"
        );
    }

    @Test
    void acceptsARevertWrittenTheWayEveryOtherCommitIs() {
        CommitMessage message = new CommitMessage(
            "fix(cli): undo the reload subcommand that broke startup",
            "The subcommand claimed a flag the root parser already owned, so every start failed."
        );
        assertEquals(
            List.of(), CommitMessageRules.validate(message, NON_TRIVIAL),
            "undoing a change is a change, and it says what it did like any other"
        );
    }

    @Test
    void acceptsOnlyTheNineTypesTheStandardNames() {
        assertEquals(
            List.of(),
            Stream.of("build", "chore", "ci", "docs", "feat", "fix", "perf", "refactor", "test")
                .flatMap(type -> header(type + ": mask bearer tokens in headers").stream())
                .toList(),
            "the nine types of the standard are the nine this harness takes"
        );
    }

    @Test
    void rejectsATypeTheStandardDoesNotName() {
        assertFalse(
            header("revert: mask bearer tokens in headers").isEmpty(),
            "git writes its own revert header, so a revert needs no type beside the nine"
        );
    }

    @Test
    void rejectsABreakingChangeMarkedInTheHeader() {
        assertFalse(
            header("feat!: mask bearer tokens in headers").isEmpty(),
            "what breaks belongs in the body, where it can be said rather than marked"
        );
    }

    private static List<String> header(String text) {
        return CommitMessageRules.validate(new CommitMessage(text, ""), TRIVIAL);
    }
}
