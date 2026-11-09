package eu.ciechanowiec.airness.governance;

import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * What the tests of the ignore-pattern check share: how they ask the check about a working tree, the endings its
 * offences carry, and a managed block to repeat.
 */
@UtilityClass
final class IgnoreFixtures {

    /**
     * The root {@code .gitignore}.
     */
    static final String GIT_IGNORE = ".gitignore";

    /**
     * The ending of an offence whose declaration stands in the same file.
     */
    static final String SAME_FILE = " and no line between them reverses it; remove this line";

    /**
     * The ending of an offence whose declaration stands in a {@code .gitignore} of a directory above.
     */
    static final String INHERITED
        = ", an un-anchored pattern that already applies in every directory beneath it," + SAME_FILE;

    /**
     * A generic managed block, whose {@code target/} stands on line 2 and whose {@code *.log} on line 3.
     */
    static final String GENERIC_BLOCK = """
        # BEGIN AIRNESS MANAGED: generic
        target/
        *.log
        # END AIRNESS MANAGED: generic
        """;

    private static final String HEADLINE = "Ignore patterns that repeat";

    /**
     * The offences the check names in a working tree.
     *
     * @param fixture the working tree
     * @return the offences, in the order of the files and their lines
     */
    static List<String> offences(GitFixture fixture) {
        return Verdicts.offences(new IgnorePatternCheck(fixture.root()).findings(), HEADLINE);
    }
}
