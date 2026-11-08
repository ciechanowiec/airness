package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalRepositoryTest {

    private static final Path INSTALLED = Path.of("sample", "library", "1.0.0", "library-1.0.0.pom");

    @TempDir
    private Path directory;

    @Test
    void chainsNoTailUnlessAPropertyNamesOne() {
        LocalRepository repository = LocalRepository.of(this.head(), new Properties(), new Properties());

        assertEquals(List.of(this.head()), repository.layers());
    }

    @Test
    void readsTheTailsInTheOrderTheyAreNamedAndSkipsBlankEntries() {
        Properties user = properties(" " + this.tail("first") + " ,, " + this.tail("second") + ",");

        LocalRepository repository = LocalRepository.of(this.head(), new Properties(), user);

        assertEquals(List.of(this.head(), this.tail("first"), this.tail("second")), repository.layers());
    }

    @Test
    void takesTheTailsFromTheSystemWhenTheCommandLineNamesNone() {
        LocalRepository repository = LocalRepository.of(
            this.head(), properties(this.tail("system").toString()), new Properties()
        );

        assertEquals(List.of(this.tail("system")), repository.tails());
    }

    @Test
    void letsTheCommandLineWinOverTheSystem() {
        LocalRepository repository = LocalRepository.of(
            this.head(), properties(this.tail("system").toString()), properties(this.tail("user").toString())
        );

        assertEquals(List.of(this.tail("user")), repository.tails());
    }

    @Test
    @SneakyThrows
    void findsAFileInTheFirstRepositoryOfTheChainThatHoldsIt() {
        Path inSecond = Files.createDirectories(this.tail("second").resolve(INSTALLED).getParent());
        Files.writeString(inSecond.resolve(INSTALLED.getFileName()), "<project/>");
        Path inFirst = Files.createDirectories(this.tail("first").resolve(INSTALLED).getParent());
        Files.writeString(inFirst.resolve(INSTALLED.getFileName()), "<project/>");
        LocalRepository repository = new LocalRepository(
            this.head(), List.of(this.tail("first"), this.tail("second"))
        );

        assertEquals(this.tail("first").resolve(INSTALLED), repository.file(INSTALLED));
    }

    @Test
    void answersTheHeadForAFileNoRepositoryHolds() {
        LocalRepository repository = new LocalRepository(this.head(), List.of(this.tail("first")));

        assertEquals(this.head().resolve(INSTALLED), repository.file(INSTALLED));
    }

    private Path head() {
        return this.directory.resolve("head");
    }

    private Path tail(String name) {
        return this.directory.resolve(name);
    }

    private static Properties properties(String tails) {
        Properties properties = new Properties();
        properties.setProperty(LocalRepository.TAIL, tails);
        return properties;
    }
}
