package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * A real module with a shared template and the controller declarations a regression supplies.
 */
@UtilityClass
final class SpringViewFixture {

    private static final String UNRESOLVED = "View names that reach no template the module ships";

    private static final String TEMPLATE = """
        <!DOCTYPE html>
        <html lang="en" xmlns:th="http://www.thymeleaf.org">
        <body><div th:fragment="rows(value)">Rows</div></body>
        </html>
        """;

    static List<String> missing(String annotation, String members) {
        return Verdicts.offences(findings(annotation, members), UNRESOLVED);
    }

    static List<Findings> findings(String annotation, String members) {
        String source = """
            package sample;
            %s
            class Rooms {
                private static final String GONE = "room/missing";
                %s
            }
            """.formatted(annotation, members);
        Path root = new GitFixture("view-handlers")
            .write("src/main/java/sample/Rooms.java", source)
            .write("src/main/resources/templates/room/list.html", TEMPLATE)
            .root();
        return new SpringModuleCheck(
            root, List.of(Path.of("src/main/java")), List.of(Path.of("src/main/resources"))
        ).findings();
    }
}
