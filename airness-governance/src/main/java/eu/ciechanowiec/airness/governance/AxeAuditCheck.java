package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * No test of the project asks the accessibility rules a question of its own.
 *
 * <p>The rules answer in three ways: the page broke this rule, the page kept it, and this rule could
 * not tell. The third is where the interesting failures live, because a rule that can decide usually
 * decides correctly and a rule that cannot is a case nobody has looked at. A project that writes the
 * question itself asks for the failures, asserts that there are none, and throws the rest away.
 *
 * <p>Three projects of this fleet did exactly that, independently, with the same wording, and the
 * omission was invisible to every other check: a suite can be green, its coverage floors met and its
 * accessibility assertions passing, while a button draws its label in its own background colour. That
 * is the shape of defect a harness exists to make impossible rather than to warn about, which is why
 * the question moved into one artifact and this check keeps it there.
 *
 * <p>Only test sources are read. A page is audited from a test, and a production class that mentions
 * the rules is doing something else entirely.
 */
public final class AxeAuditCheck {

    private static final String ASKED
        = "A test asks the accessibility rules its own question, so every verdict it did not ask for "
            + "goes unread. Run AxeAudit.SCRIPT from airness-web-evidence and assert AxeVerdict.problems()";

    private static final String LOCATED
        = "A test locates the accessibility rules by a version it names itself, which is a second place "
            + "for that version to be pinned. Read them through AxeLibrary.rules() instead";

    private final ScannedSources sources;

    /**
     * Reads the sources once, so both rules are answered from one pass over the tree.
     *
     * @param root      the working tree root
     * @param testRoots the test source directories whose Java sources are read
     */
    public AxeAuditCheck(Path root, Collection<Path> testRoots) {
        this.sources = new ScannedSources(root, testRoots);
    }

    /**
     * How many sources the check read, which a caller refuses when it is zero.
     *
     * @return the number of Java test sources in scope
     */
    public int scanned() {
        return this.sources.scanned();
    }

    /**
     * Both rules, each reported separately so a failure names which one was broken.
     *
     * @return one verdict per rule
     */
    public List<Findings> findings() {
        return List.of(
            new Findings(ASKED, this.sources.offences(AxeAuditRules::asked)),
            new Findings(LOCATED, this.sources.offences(AxeAuditRules::located))
        );
    }
}
