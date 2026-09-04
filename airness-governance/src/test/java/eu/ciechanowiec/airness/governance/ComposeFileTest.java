package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import eu.ciechanowiec.airness.governance.ComposeFile.PulledImage;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The compose reader finds the image of every pulled service, skips a built one, resolves a written
 * default, and reads nothing outside the services mapping.
 */
class ComposeFileTest {

    private static List<PulledImage> images(String yaml) {
        return ComposeFile.images(yaml).stream().map(Located::value).toList();
    }

    @Test
    void readsEveryServiceImageWithItsService() {
        String yaml = """
            services:
              db:
                image: "postgres:18"
                ports:
                  - "5432:5432"
              cache:
                image: valkey/valkey:8.1 # the open fork
            volumes:
              data:
                image: not-a-service
            """;
        List<PulledImage> read = images(yaml);
        assertEquals(
            List.of(new PulledImage("db", "postgres:18"), new PulledImage("cache", "valkey/valkey:8.1")), read
        );
        assertEquals(List.of(3, 7), ComposeFile.images(yaml).stream().map(Located::line).toList());
    }

    @Test
    void skipsABuiltService() {
        String yaml = """
            services:
              app:
                build: .
                image: example/app:1.0.0
              db:
                image: postgres:18
            """;
        assertEquals(List.of(new PulledImage("db", "postgres:18")), images(yaml), "the built image is only a name");
    }

    @Test
    void resolvesAWrittenDefaultAndLeavesABareVariable() {
        String yaml = """
            services:
              db:
                image: mongo:${MONGO_TAG:-7.0.14}
              cache:
                image: ${CACHE_IMAGE}
            """;
        assertEquals(
            List.of(new PulledImage("db", "mongo:7.0.14"), new PulledImage("cache", "${CACHE_IMAGE}")), images(yaml)
        );
    }

    @Test
    void readsAServicesMappingThatIsNotAtTheTopOfTheFile() {
        String yaml = """
            # compose
            name: example
            services:
              db:
                image: postgres:18
            """;
        assertEquals(List.of(new PulledImage("db", "postgres:18")), images(yaml));
    }

    @Test
    void readsNothingFromAFileWithoutServices() {
        assertEquals(
            List.of(), images("volumes:\n  data:\n    image: postgres:18\n"), "an image elsewhere is not pulled"
        );
    }
}
