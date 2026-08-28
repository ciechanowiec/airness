package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

class LicenseConfigurationPolicyTest {

    private static final String PLUGIN = "license-maven-plugin";
    private static final String SEPARATOR = "\\|";
    /*
     * One spelling per popular package that declares a license by a name other than its identifier. Each
     * pair was read from the project file of a package a consumer is likely to resolve, so a pair that
     * stops holding says the allowlist stopped answering for that package.
     */
    private static final Map<String, String> AS_DECLARED = Map.ofEntries(
        Map.entry("Eclipse Public License 1.0", "EPL-1.0"),
        Map.entry("Eclipse Public License v. 2.0", "EPL-2.0"),
        Map.entry("EPL 2.0", "EPL-2.0"),
        Map.entry("Apache License, 2.0", "Apache-2.0"),
        Map.entry("Apache Software Licenses", "Apache-2.0"),
        Map.entry(
            "GNU General Public License, version 2 with the GNU Classpath Exception",
            "GPL-2.0-with-classpath-exception"
        ),
        Map.entry("GPLv2+CE", "GPL-2.0-with-classpath-exception"),
        Map.entry("BSD 3-clause New License", "BSD-3-Clause"),
        Map.entry("Modified BSD", "BSD-3-Clause"),
        Map.entry("LGPL-2.1-only", "LGPL-2.1"),
        Map.entry("LGPL-2.1-or-later", "LGPL-2.1"),
        Map.entry("MPL 2.0", "MPL-2.0"),
        Map.entry("MIT No Attribution", "MIT-0"),
        Map.entry("Plexus", "BSD-3-Clause"),
        Map.entry("(Apache-2.0 OR EPL-2.0)", "Apache-2.0"),
        Map.entry("Apache License", "Apache-2.0"),
        Map.entry("Common Public License Version 1.0", "CPL-1.0"),
        Map.entry(
            "Similar to Apache License but with the acknowledgment clause removed",
            "JDOM License"
        )
    );

    @Test
    void rootNormalizesNetworkntsApacheLicenseName() {
        assertEquals("Apache-2.0", identifierOf("Apache License Version 2.0").orElseThrow());
    }

    @Test
    void carriesTheSpellingsThatPopularPackagesDeclare() {
        Map<String, String> unreadable = AS_DECLARED.entrySet().stream()
            .filter(pair -> !identifierOf(pair.getKey()).equals(Optional.of(pair.getValue())))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertEquals(Map.of(), unreadable, "a package declaring one of these fails as unreadable");
    }

    @Test
    void mergesOnlyIntoAnAllowedIdentifier() {
        Set<String> allowed = Set.copyOf(allowlist());
        List<String> unallowed = merges().stream()
            .map(merge -> merge.split(SEPARATOR, -1)[0].strip())
            .filter(target -> !allowed.contains(target))
            .toList();
        assertEquals(List.of(), unallowed, "a merge naming an omitted identifier normalizes to nothing");
    }

    @Test
    void namesEverySpellingOnce() {
        List<String> repeated = merges().stream()
            .flatMap(merge -> spellingsOf(merge).stream())
            .collect(Collectors.groupingBy(spelling -> spelling, Collectors.counting()))
            .entrySet()
            .stream()
            .filter(count -> count.getValue() > 1)
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
        assertTrue(repeated.isEmpty(), "a spelling written twice normalizes by the merge read first: " + repeated);
    }

    /*
     * The identifier a spelling normalizes to, which is the head of the merge that carries it. A spelling
     * no merge carries has no identifier, and the package declaring it fails as unreadable rather than as
     * disallowed.
     */
    private static Optional<String> identifierOf(String spelling) {
        return merges().stream()
            .filter(merge -> spellingsOf(merge).contains(spelling))
            .map(merge -> merge.split(SEPARATOR, -1)[0].strip())
            .findFirst();
    }

    private static List<String> spellingsOf(String merge) {
        return Arrays.stream(merge.split(SEPARATOR, -1)).skip(1).map(String::strip).toList();
    }

    private static List<String> merges() {
        return textsUnder("licenseMerges", "licenseMerge");
    }

    private static List<String> allowlist() {
        return textsUnder("includedLicenses", "includedLicense");
    }

    private static List<String> textsUnder(String container, String entry) {
        Element configuration = ProjectFiles.child(
            ProjectFiles.managed(ProjectFiles.rootPom(), PLUGIN), "configuration", container
        );
        return Xml.children(configuration, entry).stream()
            .map(Element::getTextContent)
            .map(String::strip)
            .toList();
    }
}
