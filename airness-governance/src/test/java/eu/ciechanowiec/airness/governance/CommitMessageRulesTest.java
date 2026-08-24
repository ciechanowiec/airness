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
 * on a non-trivial change, and an AI-agent attribution marker. A revert header, and a commit that git
 * recorded with two parents, are exempt from the header shape alone, so the attribution ban still
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
    void rejectsAnAgentAttributionTrailer() {
        CommitMessage message = new CommitMessage(
            "feat(cli): add the reload subcommand to the root",
            "Co-authored-by: Claude <noreply@anthropic.com>"
        );
        assertFalse(CommitMessageRules.validate(message, TRIVIAL).isEmpty());
    }

    @Test
    void rejectsAnAttributionTrailerForEveryAgentNotJustClaude() {
        assertFalse(
            CommitMessageRules.validate(attributed("Co-authored-by: Codex <noreply@openai.com>"), TRIVIAL).isEmpty(),
            "the ban is on naming an agent, not on naming one particular vendor"
        );
        assertFalse(
            CommitMessageRules.validate(attributed("Generated with Codex"), TRIVIAL).isEmpty(),
            "the same trailer form from a different agent"
        );
        assertFalse(
            CommitMessageRules.validate(attributed("codex-session: 0199f0c1-dead-beef"), TRIVIAL).isEmpty(),
            "a session id identifies an agent run as surely as a trailer does"
        );
        assertFalse(
            CommitMessageRules.validate(attributed("https://chatgpt.com/codex"), TRIVIAL).isEmpty(),
            "a bare product page is an advertisement, whichever agent left it"
        );
    }

    /**
     * The ban is on advertising an agent, not on naming a host a project integrates with - and for a
     * project whose subject matter is one of those vendors, such a host is ordinary content. A rule
     * written as "no vendor URL" rather than "no product page" would reject that project's honest
     * history, so this pins the difference.
     */
    @Test
    void acceptsAnHonestMentionOfAnUpstreamTheProjectCalls() {
        CommitMessage message = new CommitMessage(
            "feat(client): forward Codex to its ChatGPT backend",
            "Codex signed in with ChatGPT posts to chatgpt.com/backend-api/codex, so the listener "
                + "forwards there rather than to api.anthropic.com."
        );
        assertEquals(
            List.of(), CommitMessageRules.validate(message, NON_TRIVIAL),
            "naming an upstream is not attributing the change to an agent"
        );
    }

    private static CommitMessage attributed(String trailer) {
        return new CommitMessage("feat(cli): add the reload subcommand to the root", trailer);
    }

    @Test
    void exemptsACommitWithTwoParentsWhateverItsHeaderSays() {
        CommitMessage gitForm = new CommitMessage("Merge branch 'release' into 'main'", "");
        CommitMessage arbitrary = new CommitMessage("anything at all", "");
        assertEquals(
            List.of(), CommitMessageRules.validate(gitForm, NON_TRIVIAL, true),
            "git's own merge header carries neither a type nor a scope nor a body"
        );
        assertEquals(
            List.of(), CommitMessageRules.validate(arbitrary, NON_TRIVIAL, true),
            "and topology alone decides it, since LinearHistoryCheck already bans the commit outright"
        );
    }

    @Test
    void rejectsAMergeLikeHeaderOnAnOrdinaryCommit() {
        CommitMessage message = new CommitMessage("Merge anything at all", "");
        assertFalse(CommitMessageRules.validate(message, TRIVIAL).isEmpty());
    }

    @Test
    void exemptsARevertHeaderFromTheHeaderShape() {
        CommitMessage message = new CommitMessage(
            "Revert \"feat(cli): add the reload subcommand to the root\"",
            "This reverts commit 0123456789abcdef0123456789abcdef01234567."
        );
        assertEquals(List.of(), CommitMessageRules.validate(message, NON_TRIVIAL));
    }

    @Test
    void rejectsAnIncompleteRevertHeader() {
        CommitMessage message = new CommitMessage("Revert \"unfinished", "");
        assertFalse(CommitMessageRules.validate(message, TRIVIAL).isEmpty());
    }

    @Test
    void stillRejectsAnAgentAttributionUnderAnExemptHeader() {
        CommitMessage message = new CommitMessage(
            "Revert \"feat(cli): add the reload subcommand to the root\"",
            "This reverts commit 0123456789abcdef0123456789abcdef01234567.\n\nGenerated with Claude Code"
        );
        assertFalse(
            CommitMessageRules.validate(message, TRIVIAL).isEmpty(),
            "a fixed header form is no licence to name an agent in the body"
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
