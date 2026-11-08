package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class StarterRequestTest {

    private static final String GROUP = "com.example";

    @Test
    void movesTheCodeUnderTheGroupAndTheArtifactUnlessAPackageIsGiven() {
        StarterRequest request = StarterRequest.of(GROUP, "task-tracker", Optional.empty(), Optional.empty());

        assertEquals("com.example.tasktracker", request.javaPackage());
        assertEquals("com/example/tasktracker", request.packagePath());
    }

    @Test
    void callsTheApplicationByTheCapitalisedWordsOfItsArtifactUnlessANameIsGiven() {
        assertEquals(
            "Task Tracker", StarterRequest.of(GROUP, "task-tracker", Optional.empty(), Optional.empty()).name()
        );
        assertEquals(
            "Ledger", StarterRequest.of(GROUP, "books", Optional.of("com.example.books"), Optional.of("Ledger")).name()
        );
    }

    @Test
    void dropsTheHyphensOfAGroupFromTheDerivedPackage() {
        assertEquals(
            "eu.myorg.tracker", StarterRequest.of("eu.my-org", "tracker", Optional.empty(), Optional.empty())
                .javaPackage()
        );
    }

    @Test
    void admitsWellFormedNames() {
        assertTrue(
            StarterRequest.of(GROUP, "tracker", Optional.empty(), Optional.of("Zadania 2")).problems().isEmpty()
        );
    }

    @Test
    void refusesEachMalformedValueByItsProperty() {
        List<String> problems = new StarterRequest("Com.Example", "Task_Tracker", "com.Example", "Task  Tracker")
            .problems();

        assertEquals(4, problems.size(), problems.toString());
        assertTrue(problems.getFirst().startsWith("airness.groupId is 'Com.Example'"), problems.getFirst());
        assertTrue(problems.get(1).startsWith("airness.artifactId is 'Task_Tracker'"), problems.get(1));
        assertTrue(problems.get(2).startsWith("airness.package is 'com.Example'"), problems.get(2));
        assertTrue(problems.getLast().startsWith("airness.name is 'Task  Tracker'"), problems.getLast());
    }

    @Test
    void refusesAPackageWithAJavaKeywordInIt() {
        List<String> problems = StarterRequest.of(GROUP, "new", Optional.empty(), Optional.empty()).problems();

        assertEquals(List.of("airness.package is 'com.example.new', but a part of it is a Java keyword"), problems);
    }

    @Test
    void refusesAMalformedPackageOnceRatherThanAlsoAsAKeyword() {
        List<String> problems = StarterRequest.of(
            GROUP, "tracker", Optional.of("org.sample.task-tracker"), Optional.empty()
        ).problems();

        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.getFirst().startsWith("airness.package is 'org.sample.task-tracker'"), problems.getFirst());
    }

    @Test
    void namesAnArtifactWithADoubledHyphenBeforeRefusingIt() {
        StarterRequest request = StarterRequest.of(GROUP, "task--tracker", Optional.empty(), Optional.empty());

        assertEquals("Task Tracker", request.name());
        assertTrue(request.problems().getFirst().startsWith("airness.artifactId is 'task--tracker'"));
    }

    @Test
    void readsTheRequestFromTheCommandLineProperties() {
        Properties properties = new Properties();
        properties.setProperty("airness.groupId", " org.sample ");
        properties.setProperty("airness.artifactId", "books");
        properties.setProperty("airness.package", "org.sample.library");
        properties.setProperty("airness.name", "  ");

        assertEquals(
            new StarterRequest("org.sample", "books", "org.sample.library", "Books"), StarterRequest.of(properties)
        );
    }

    @Test
    void refusesARequestWhoseCoordinatesAreNotSet() {
        List<String> problems = StarterRequest.of(new Properties()).problems();

        assertTrue(problems.getFirst().startsWith("airness.groupId is ''"), problems.toString());
        assertTrue(problems.get(1).startsWith("airness.artifactId is ''"), problems.toString());
    }
}
