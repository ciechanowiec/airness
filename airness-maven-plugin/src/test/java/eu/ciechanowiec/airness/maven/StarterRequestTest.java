package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.LicenseCatalogue;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class StarterRequestTest {

    private static final String GROUP = "org.sample";
    private static final LicenseCatalogue LICENSES = SpdxLicenses.current();
    private static final Year YEAR = Year.of(2026);
    private static final StarterOwner OWNER = new StarterOwner(
        "Sample Organization", "https://git.sample.org/tracker", "LicenseRef-Proprietary", YEAR
    );

    @Test
    void movesTheCodeUnderTheGroupAndTheArtifactUnlessAPackageIsGiven() {
        StarterRequest request = StarterRequest.of(GROUP, "task-tracker", Optional.empty(), Optional.empty());

        assertEquals("org.sample.tasktracker", request.javaPackage());
        assertEquals("org/sample/tasktracker", request.packagePath());
    }

    @Test
    void callsTheApplicationByTheCapitalisedWordsOfItsArtifactUnlessANameIsGiven() {
        assertEquals(
            "Task Tracker", StarterRequest.of(GROUP, "task-tracker", Optional.empty(), Optional.empty()).name()
        );
        assertEquals(
            "Ledger", StarterRequest.of(GROUP, "books", Optional.of("org.sample.books"), Optional.of("Ledger")).name()
        );
    }

    @Test
    void admitsWellFormedNamesUnderAnOwner() {
        assertTrue(
            StarterRequest.of(GROUP, "tracker", Optional.empty(), Optional.of("Zadania 2")).owned(OWNER)
                .problems(LICENSES).isEmpty()
        );
    }

    @Test
    void refusesEachMalformedValueByItsProperty() {
        List<String> problems = new StarterRequest("Com.Example", "Task_Tracker", "com.Example", "Task  Tracker", OWNER)
            .problems(LICENSES);

        assertEquals(4, problems.size(), problems.toString());
        assertTrue(problems.getFirst().startsWith("airness.groupId is 'Com.Example'"), problems.getFirst());
        assertTrue(problems.get(1).startsWith("airness.artifactId is 'Task_Tracker'"), problems.get(1));
        assertTrue(problems.get(2).startsWith("airness.package is 'com.Example'"), problems.get(2));
        assertTrue(problems.getLast().startsWith("airness.name is 'Task  Tracker'"), problems.getLast());
    }

    @Test
    void refusesAGroupUnderAnExampleDomainOrOfOneSegment() {
        assertEquals(
            List.of(
                "airness.groupId is 'com.example', but it must be a reverse domain the owner holds, of lower-case "
                    + "words joined by dots, such as org.sample"
            ),
            StarterRequest.of("com.example", "tracker", Optional.empty(), Optional.empty()).owned(OWNER)
                .problems(LICENSES)
        );
        assertTrue(
            StarterRequest.of("sample", "tracker", Optional.of("sample.tracker"), Optional.empty()).owned(OWNER)
                .problems(LICENSES).getFirst().startsWith("airness.groupId is 'sample'")
        );
    }

    @Test
    void refusesAPackageWithAJavaKeywordInIt() {
        List<String> problems = StarterRequest.of(GROUP, "new", Optional.empty(), Optional.empty()).owned(OWNER)
            .problems(LICENSES);

        assertEquals(List.of("airness.package is 'org.sample.new', but a part of it is a Java keyword"), problems);
    }

    @Test
    void refusesAMalformedPackageOnceRatherThanAlsoAsAKeyword() {
        List<String> problems = StarterRequest.of(
            GROUP, "tracker", Optional.of("org.sample.task-tracker"), Optional.empty()
        ).owned(OWNER).problems(LICENSES);

        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.getFirst().startsWith("airness.package is 'org.sample.task-tracker'"), problems.getFirst());
    }

    @Test
    void namesAnArtifactWithADoubledHyphenBeforeRefusingIt() {
        StarterRequest request = StarterRequest.of(GROUP, "task--tracker", Optional.empty(), Optional.empty());

        assertEquals("Task Tracker", request.name());
        assertTrue(
            request.owned(OWNER).problems(LICENSES).getFirst().startsWith("airness.artifactId is 'task--tracker'")
        );
    }

    @Test
    void readsTheRequestAndItsOwnerFromTheCommandLineProperties() {
        Properties properties = new Properties();
        properties.setProperty("airness.groupId", " org.sample ");
        properties.setProperty("airness.artifactId", "books");
        properties.setProperty("airness.package", "org.sample.library");
        properties.setProperty("airness.name", "  ");
        properties.setProperty("airness.organization", "Sample Organization");
        properties.setProperty("airness.url", "https://git.sample.org/tracker");

        assertEquals(
            new StarterRequest("org.sample", "books", "org.sample.library", "Books", OWNER),
            StarterRequest.of(properties, YEAR)
        );
    }

    @Test
    void refusesARequestWhoseCoordinatesAndOwnerAreNotSet() {
        List<String> problems = StarterRequest.of(new Properties(), YEAR).problems(LICENSES);

        assertTrue(problems.getFirst().startsWith("airness.groupId is ''"), problems.toString());
        assertTrue(problems.get(1).startsWith("airness.artifactId is ''"), problems.toString());
        assertTrue(problems.contains("airness.organization is not set, but it must name who owns the application"));
    }
}
