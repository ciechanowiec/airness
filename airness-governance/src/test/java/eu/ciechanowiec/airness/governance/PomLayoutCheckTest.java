package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PomLayoutCheckTest {

    private static final String SEPARATION
        = "Set every element directly inside <project> that holds elements apart from its neighbours by exactly "
            + "one blank line, above the comments written directly over it";
    private static final String BLANK_RUNS = "Write no two consecutive blank lines in a pom";
    private static final String CROWDED
        = "Start every pom tag on its own line, apart from the closing tag of an element that holds only text";
    private static final String EDGES
        = "Write no blank line directly after an opening pom tag or directly before a closing one";

    private static final String CLEAN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
            <modelVersion>4.0.0</modelVersion>

            <!-- One comment line. -->
            <parent>
                <groupId>eu.ciechanowiec</groupId>
                <artifactId>airness-parent-java</artifactId>
                <version>1</version>
                <relativePath/>
            </parent>

            <groupId>com.example</groupId>
            <artifactId>consumer</artifactId>
            <version>1.0.0</version>
            <properties/>
            <packaging>jar</packaging>
            <description>
                Text that runs

                over paragraphs.
            </description>

            <!--
                A comment over several lines.
            -->
            <!-- And a second one. -->
            <licenses>
                <license>
                    <name>MIT</name>
                    <url><![CDATA[https://opensource.org/license/mit]]></url>
                </license>
            </licenses>

            <build>
                <plugins>
                    <plugin>
                        <artifactId>maven-jar-plugin</artifactId> <!-- trailing note -->
                        <configuration></configuration>
                    </plugin>
                </plugins>
            </build>

            <profiles>
                <profile>
                    <id>x</id>
                    <activation>
                        <activeByDefault>false</activeByDefault>
                    </activation>
                </profile>
            </profiles>
        </project>
        """;

    @Test
    void acceptsAPomWrittenToTheLayout(@TempDir Path directory) {
        assertEquals(List.of(), offences(directory, CLEAN));
    }

    @Test
    void acceptsABlockThatOpensOrClosesTheProject(@TempDir Path directory) {
        String pom = """
            <project>
                <parent>
                    <artifactId>a</artifactId>
                </parent>

                <artifactId>b</artifactId>

                <properties>
                    <a>b</a>
                </properties>
            </project>
            """;
        assertEquals(List.of(), offences(directory, pom));
    }

    @Test
    void refusesABlockWithoutABlankLineAboveIt(@TempDir Path directory) {
        String pom = CLEAN.replace(
            "    <modelVersion>4.0.0</modelVersion>\n\n", "    <modelVersion>4.0.0</modelVersion>\n"
        );
        assertEquals(
            List.of(SEPARATION + ": pom.xml:5: no blank line between <modelVersion> and a comment"),
            offences(directory, pom)
        );
    }

    @Test
    void refusesABlockWithoutABlankLineBelowIt(@TempDir Path directory) {
        String pom = CLEAN.replace("    </parent>\n\n", "    </parent>\n");
        assertEquals(
            List.of(SEPARATION + ": pom.xml:13: no blank line between <parent> and <groupId>"),
            offences(directory, pom)
        );
    }

    @Test
    void refusesTwoBlankLinesAroundABlock(@TempDir Path directory) {
        String pom = CLEAN.replace("    </licenses>\n\n", "    </licenses>\n\n\n");
        assertEquals(
            List.of(
                SEPARATION + ": pom.xml:37: 2 blank lines between <licenses> and <build>",
                BLANK_RUNS + ": pom.xml:36: a second consecutive blank line"
            ),
            offences(directory, pom)
        );
    }

    @Test
    void refusesABlankLineBetweenTheCommentsOfABlockAndWhatPrecedesThem(@TempDir Path directory) {
        String pom = CLEAN.replace("    </description>\n\n", "    </description>\n");
        assertEquals(
            List.of(SEPARATION + ": pom.xml:24: no blank line between <description> and a comment"),
            offences(directory, pom)
        );
    }

    @Test
    void acceptsACommentSetApartFromTheBlockBelowIt(@TempDir Path directory) {
        String pom = CLEAN.replace("    <!-- And a second one. -->\n", "    <!-- And a second one. -->\n\n");
        assertEquals(List.of(), offences(directory, pom));
    }

    @Test
    void refusesTwoBlocksOnOneLine(@TempDir Path directory) {
        String pom = CLEAN.replace("    </build>\n\n    <profiles>", "    </build><profiles>");
        assertEquals(
            List.of(
                SEPARATION + ": pom.xml:43: no blank line between <build> and <profiles>",
                CROWDED + ": pom.xml:43: <profiles> follows other markup on its line"
            ),
            offences(directory, pom)
        );
    }

    @Test
    void leavesTheElementsInsideABlockTogether(@TempDir Path directory) {
        String pom = CLEAN.replace("<id>x</id>", "<id>x</id>\n            <build>\n            </build>");
        assertEquals(List.of(), offences(directory, pom));
    }

    @Test
    void refusesSecondBlankLinesOncePerRunWhereverTheyStand(@TempDir Path directory) {
        String pom = CLEAN.replace("A comment over several lines.", "A comment\n\n\n\nover several lines.")
            .replace("over paragraphs.", "\nover paragraphs.");
        assertEquals(
            List.of(
                BLANK_RUNS + ": pom.xml:22: a second consecutive blank line",
                BLANK_RUNS + ": pom.xml:29: a second consecutive blank line"
            ),
            offences(directory, pom)
        );
    }

    @Test
    void refusesElementsWrittenOnTheLineOfTheirParent(@TempDir Path directory) {
        String pom = CLEAN.replace(
            """
                        <license>
                            <name>MIT</name>
                            <url><![CDATA[https://opensource.org/license/mit]]></url>
                        </license>
                """,
            "        <license><name>MIT</name><url>https://opensource.org/license/mit</url></license>\n"
        );
        assertEquals(
            List.of(
                CROWDED + ": pom.xml:30: <name> follows other markup on its line",
                CROWDED + ": pom.xml:30: <url> follows other markup on its line",
                CROWDED + ": pom.xml:30: </license> follows other markup on its line"
            ),
            offences(directory, pom)
        );
    }

    @Test
    void refusesAClosingTagAfterAnEmptyChildOnItsLine(@TempDir Path directory) {
        String pom = CLEAN.replace("<relativePath/>\n    </parent>", "<relativePath/></parent>");
        assertEquals(
            List.of(CROWDED + ": pom.xml:11: </parent> follows other markup on its line"),
            offences(directory, pom)
        );
    }

    @Test
    void refusesATagWrittenAfterACommentOnItsLine(@TempDir Path directory) {
        String pom = CLEAN.replace("<!-- One comment line. -->\n    <parent>", "<!-- One comment line. --><parent>");
        assertEquals(
            List.of(
                SEPARATION + ": pom.xml:6: no blank line between a comment and <parent>",
                CROWDED + ": pom.xml:6: <parent> follows other markup on its line"
            ),
            offences(directory, pom)
        );
    }

    @Test
    void refusesABlankLineDirectlyAfterAnOpeningTag(@TempDir Path directory) {
        String pom = CLEAN.replace("<plugins>\n", "<plugins>\n\n").replace("<profiles>\n", "<profiles>\n\n");
        assertEquals(
            List.of(
                EDGES + ": pom.xml:38: blank line directly after <plugins>",
                EDGES + ": pom.xml:47: blank line directly after <profiles>"
            ),
            offences(directory, pom)
        );
    }

    @Test
    void refusesABlankLineDirectlyAfterAnOpeningTagAboveAComment(@TempDir Path directory) {
        String pom = CLEAN.replace("<plugin>\n", "<plugin>\n\n            <!-- note -->\n");
        assertEquals(List.of(EDGES + ": pom.xml:39: blank line directly after <plugin>"), offences(directory, pom));
    }

    @Test
    void refusesABlankLineDirectlyBeforeAClosingTag(@TempDir Path directory) {
        String pom = CLEAN.replace("    </plugins>", "\n        </plugins>")
            .replace("    </profiles>\n", "\n    </profiles>\n");
        assertEquals(
            List.of(
                EDGES + ": pom.xml:42: blank line directly before </plugins>",
                EDGES + ": pom.xml:53: blank line directly before </profiles>"
            ),
            offences(directory, pom)
        );
    }

    @Test
    void refusesABlankLineInsideAnElementThatHoldsNothingElse(@TempDir Path directory) {
        String pom = CLEAN.replace("<configuration></configuration>", "<configuration>\n\n</configuration>");
        assertEquals(
            List.of(EDGES + ": pom.xml:41: blank line inside <configuration>, which holds nothing else"),
            offences(directory, pom)
        );
    }

    @SneakyThrows
    private static List<String> offences(Path directory, String pom) {
        Path file = directory.resolve("pom.xml");
        Files.writeString(file, pom);
        return new PomLayoutCheck(file).findings().stream()
            .flatMap(findings -> findings.offences().stream().map(offence -> findings.headline() + ": " + offence))
            .toList();
    }
}
