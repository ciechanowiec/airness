package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The reader finds the image a Testcontainers call names, whether the call writes it out or holds it
 * in a constant of the same source, and nothing that merely looks like one: not a comment, not a text
 * block, not a constructor of the project's own, not a sentence, not a name nothing declares.
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

    @Test
    void readsAnImageAConstantOfTheSameFileNames() {
        String source = """
            import org.testcontainers.containers.GenericContainer;
            import org.testcontainers.utility.DockerImageName;

            class Images {
                private static final String CACHE_IMAGE = "redis:7.4.1";
                private static final String STORE_IMAGE = "mongo:7";

                static final GenericContainer<?> CACHE = new GenericContainer<>(CACHE_IMAGE);
                static final DockerImageName STORE = DockerImageName.parse(STORE_IMAGE);
            }
            """;
        assertEquals(List.of("redis:7.4.1", "mongo:7"), in(source), "a name lifted out of the call is followed");
        assertEquals(
            List.of(8, 9),
            JavaImageLiterals.in(source).stream().map(Located::line).toList(),
            "the line reported is the call that pulls the image rather than the declaration"
        );
    }

    @Test
    void ignoresAnIdentifierNoConstantOfTheFileDeclares() {
        String source = """
            import org.testcontainers.containers.GenericContainer;
            import org.testcontainers.utility.DockerImageName;

            class Images {
                GenericContainer<?> of(String chosen) {
                    DockerImageName.parse(Elsewhere.STORE_IMAGE);
                    return new GenericContainer<>(chosen);
                }
            }
            """;
        assertEquals(List.of(), in(source), "a reader with no compiler follows no name this source declares");
    }

    @Test
    void ignoresAConstantThatHoldsNoReference() {
        String source = """
            import org.testcontainers.containers.GenericContainer;

            class Images {
                private static final String NOTE = "some words here";

                static final GenericContainer<?> CACHE = new GenericContainer<>(NOTE);
            }
            """;
        assertEquals(List.of(), in(source), "a constant holding a sentence names no image");
    }
}
