package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An exclusion is read against the classes the coverage report measured, so one that names none of them
 * is reported rather than left to look as though it worked.
 */
class CoverageReportTest {

    private static final String REPORT = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
        <report name="example">
            <package name="com/example/cli">
                <class name="com/example/cli/StartCommand" sourcefilename="StartCommand.java"/>
                <class name="com/example/cli/Aroxy" sourcefilename="Aroxy.java"/>
            </package>
            <package name="com/example/daemon">
                <class name="com/example/daemon/Launcher" sourcefilename="Launcher.java"/>
                <class name="com/example/daemon/Launcher$Outcome" sourcefilename="Launcher.java"/>
            </package>
        </report>
        """;

    @TempDir
    private Path directory;

    @Test
    void readsEveryClassTheReportMeasured() {
        assertEquals(
            5, this.report().measured(),
            "three plain classes, and the nested one under both spellings of its nesting"
        );
    }

    @Test
    void acceptsARoleThatReachesTheClassesItNames() {
        assertEquals(
            List.of(), this.report().unreached(List.of("*Command", "com.example.daemon.Launcher*")),
            "both roles name classes this report measured"
        );
    }

    @Test
    void reportsAPatternThatReachesNothing() {
        assertEquals(
            List.of("com.example.cli.*Dto"),
            this.report().unreached(List.of("*Command", "com.example.cli.*Dto")),
            "a pattern naming no measured class excludes nothing, and nothing else would say so"
        );
    }

    @Test
    void reportsAPatternWrittenForAToolThatReadsPaths() {
        assertEquals(
            List.of("**/*Command"),
            this.report().unreached(List.of("**/*Command")),
            "a path belongs to a different setting, and here it is a pattern that matches nothing"
        );
    }

    @Test
    void reachesANestedClassThroughEitherSpellingOfItsNesting() {
        assertEquals(
            List.of(),
            this.report().unreached(
                List.of("com.example.daemon.Launcher.Outcome", "com.example.daemon.Launcher$Outcome")
            ),
            "the report writes a dollar sign and the coverage check writes a dot, so both reach it"
        );
    }

    @Test
    void refusesToGuessWhenTheReportCannotBeRead() {
        Path absent = this.directory.resolve("absent.xml");
        assertThrows(
            UncheckedIOException.class, () -> new CoverageReport(absent),
            "a report that cannot be read is not a report saying every exclusion is dead"
        );
    }

    @Test
    void readsAPatternAsAGlobRatherThanAsARegularExpression() {
        assertEquals(
            List.of("com.example.cli.Start.ommand"),
            this.report().unreached(List.of("com.example.cli.Start.ommand")),
            "a dot stands for itself here, so it does not stand in for the letter C"
        );
    }

    @SneakyThrows
    private CoverageReport report() {
        Path file = this.directory.resolve("jacoco.xml");
        Files.writeString(file, REPORT);
        return new CoverageReport(file);
    }
}
