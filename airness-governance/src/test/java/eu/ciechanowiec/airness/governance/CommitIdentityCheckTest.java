package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The commit-identity check holds every email the history records, as an author or as a committer, to one
 * name, compared exactly as recorded and whatever a mailmap says. One email under two names is reported in
 * each way a history can record it: twice as an author, twice as a committer, and once in each role.
 */
class CommitIdentityCheckTest {

    private static final Identity DOE = new Identity("Doe", "doe@example.invalid");
    private static final Identity JANE_DOE = new Identity("Doe, Jane", DOE.email());
    private static final Identity FIXTURE = new Identity("Fixture", "fixture@example.invalid");

    @Test
    void passesOverAHistoryWithOneNamePerEmail() {
        GitFixture fixture = new GitFixture("identity-clean");
        commit(fixture, DOE, DOE);
        commit(fixture, FIXTURE, DOE);
        CommitIdentityCheck check = new CommitIdentityCheck(fixture.root());
        assertEquals(2, check.scanned(), "both commits were read");
        assertTrue(Verdicts.clean(check.findings()), "and each email stands for one name in both roles");
    }

    @Test
    void passesOverOneNameUnderTwoEmails() {
        GitFixture fixture = new GitFixture("identity-two-emails");
        commit(fixture, DOE, FIXTURE);
        commit(fixture, new Identity(DOE.name(), "jane@example.invalid"), FIXTURE);
        assertTrue(
            Verdicts.clean(new CommitIdentityCheck(fixture.root()).findings()),
            "the rule maps each email to one name, and a name may own several emails"
        );
    }

    @Test
    void rejectsOneEmailUnderTwoAuthorNames() {
        GitFixture fixture = new GitFixture("identity-authors");
        String first = commit(fixture, FIXTURE, DOE);
        String second = commit(fixture, FIXTURE, JANE_DOE);
        assertEquals(
            List.of(offence("'Doe' (author of " + first + "), 'Doe, Jane' (author of " + second + ")")),
            offences(fixture),
            "two author names under one email are two people in the history"
        );
    }

    @Test
    void rejectsOneEmailUnderTwoCommitterNames() {
        GitFixture fixture = new GitFixture("identity-committers");
        String first = commit(fixture, DOE, FIXTURE);
        String second = commit(fixture, JANE_DOE, FIXTURE);
        assertEquals(
            List.of(offence("'Doe' (committer of " + first + "), 'Doe, Jane' (committer of " + second + ")")),
            offences(fixture),
            "a committer identity is held to the rule as an author identity is"
        );
    }

    @Test
    void rejectsOneEmailUnderAnAuthorNameAndADifferentCommitterName() {
        GitFixture fixture = new GitFixture("identity-roles");
        String first = commit(fixture, FIXTURE, DOE);
        String second = commit(fixture, JANE_DOE, FIXTURE);
        assertEquals(
            List.of(offence("'Doe' (author of " + first + "), 'Doe, Jane' (committer of " + second + ")")),
            offences(fixture),
            "the author and committer roles share one map from email to name"
        );
    }

    @Test
    void readsTheNamesAsRecordedWhateverTheMailmapSays() {
        GitFixture fixture = new GitFixture("identity-mailmap");
        String first = commit(fixture, FIXTURE, DOE);
        fixture.write(".mailmap", DOE.signature() + " " + JANE_DOE.signature() + "\n");
        String second = commit(fixture, FIXTURE, JANE_DOE);
        fixture.git("config", "log.mailmap", "true");
        assertEquals(
            List.of(offence("'Doe' (author of " + first + "), 'Doe, Jane' (author of " + second + ")")),
            offences(fixture),
            "a mailmap changes how some tools display a commit, never what the commit records"
        );
    }

    /**
     * Records an empty commit, staging whatever the fixture holds, under the given committer and author.
     *
     * @return the hash of the new commit
     */
    private static String commit(GitFixture fixture, Identity committer, Identity author) {
        fixture.stage();
        fixture.git(
            "-c", "user.name=" + committer.name(), "-c", "user.email=" + committer.email(), "commit", "--quiet",
            "--no-verify", "--allow-empty", "--author=" + author.signature(), "--message",
            "feat(core): record a fixture commit"
        );
        return GitPlumbing.run(fixture.root(), List.of("rev-parse", "HEAD")).strip();
    }

    private static List<String> offences(GitFixture fixture) {
        return Verdicts.offences(new CommitIdentityCheck(fixture.root()).findings(), "more than one name");
    }

    private static String offence(String names) {
        return "<" + DOE.email() + "> is recorded under 2 names, where one person keeps one: " + names;
    }

    /**
     * A name and an email, as git records them together.
     */
    private record Identity(String name, String email) {

        String signature() {
            return this.name + " <" + this.email + ">";
        }
    }
}
