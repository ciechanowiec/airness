package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The literal reader finds the image a Testcontainers call names, and nothing that merely looks like
 * one: not a comment, not a text block, not a constructor of the project's own, not a sentence.
 */
class JavaImageLiteralsTest {

    private static List<String> in(String source) {
        return JavaImageLiterals.in(source).stream().map(Located::value).toList();
    }

    @Test
    void readsParseAndConstructorLiteralsUnderTestcontainers() {
        String source = """
            import org.testcontainers.containers.GenericContainer;
            import org.testcontainers.utility.DockerImageName;

            class Images {
                static final GenericContainer<?> CACHE = new GenericContainer<>("redis:7.4.1");
                static final DockerImageName STORE = DockerImageName.parse("mongo:7");
                static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:18");
            }
            """;
        assertEquals(List.of("redis:7.4.1", "mongo:7", "postgres:18"), in(source), "in source order");
        assertEquals(List.of(5, 6, 7), JavaImageLiterals.in(source).stream().map(Located::line).toList());
    }

    @Test
    void ignoresAConstructorWithoutTheImport() {
        String source = "class Rooms { RoomContainer room = new RoomContainer(\"mongo:7\"); }\n";
        assertEquals(List.of(), in(source), "a project's own container is not an image");
    }

    @Test
    void ignoresACommentAndATextBlock() {
        String source = """
            import org.testcontainers.utility.DockerImageName;

            class Images {
                // DockerImageName.parse("mongo:7")
                /* new GenericContainer<>("redis:8") */
                String note = \"""
                    DockerImageName.parse("mongo:7")
                    \""";
            }
            """;
        assertEquals(List.of(), in(source), "neither a comment nor a document names an image");
    }

    @Test
    void ignoresALiteralThatIsNotAReference() {
        String source = """
            import org.testcontainers.containers.GenericContainer;

            class Images {
                Object one = new GenericContainer<>("some words here");
                Object two = DockerImageName.parse("Mongo:7");
            }
            """;
        assertEquals(List.of(), in(source), "a sentence and an upper-case name are not references");
    }
}
