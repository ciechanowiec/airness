package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The result file is the whole contract between Maven and a gate, and no reading of it passes silently.
 */
class NodeGateResultTest {

    private static final String GATE = "knip";
    private static final String PASSED = "passed";
    private static final String NONE = "[]";
    private static final String PROJECT = "src/main/frontend";
    private static final String TEMPLATE = """
        {"contract": %s, "gate": "%s", "title": "dead code", "status": "%s", "summary": "%s",
         "findings": %s, "evidence": %s}
        """;

    @TempDir
    private Path results;

    @SneakyThrows
    private NodeGateResult read(String content, int exit) {
        Path result = Files.writeString(this.results.resolve(GATE + ".json"), content);
        return NodeGateResult.read(GATE, PROJECT, result, exit);
    }

    @SneakyThrows
    private NodeGateResult read(String status, String findings, String evidence, int exit) {
        return this.read(
            TEMPLATE.formatted(NodeGateResult.CONTRACT, GATE, status, "2 unused", findings, evidence), exit
        );
    }

    private static String only(NodeGateResult result) {
        List<String> offences = result.findings().offences();
        assertEquals(1, offences.size(), String.valueOf(offences));
        return offences.getFirst();
    }

    @Test
    @SneakyThrows
    void passesAPassingGateAndStatesItsSummary() {
        NodeGateResult result = this.read(PASSED, NONE, NONE, 0);
        assertTrue(result.findings().clean());
        assertEquals("Node gate knip in src/main/frontend passed: 2 unused", result.line());
        assertEquals("Node gate knip in src/main/frontend: dead code", result.findings().headline());
    }

    @Test
    @SneakyThrows
    void namesAGateThatDidNotApplyAsNotApplicableRatherThanAsAPass() {
        NodeGateResult result = this.read(
            TEMPLATE.formatted(NodeGateResult.CONTRACT, GATE, "not-applicable", "", NONE, NONE), 0
        );
        assertTrue(result.findings().clean());
        assertEquals("Node gate knip in src/main/frontend not applicable", result.line());
    }

    @Test
    @SneakyThrows
    void reportsEveryFindingOfAFailedGate() {
        NodeGateResult result = this.read("failed", "[\"src/a.ts:1 unused\", \"src/b.ts:2 unused\"]", NONE, 1);
        assertEquals(List.of("src/a.ts:1 unused", "src/b.ts:2 unused"), result.findings().offences());
    }

    @Test
    @SneakyThrows
    void failsAGateThatWroteNoResultOrOneThatCannotBeRead() {
        NodeGateResult missing = NodeGateResult.read(GATE, PROJECT, this.results.resolve("absent.json"), 0);
        assertTrue(only(missing).startsWith("wrote no result file"), only(missing));
        assertTrue(only(this.read("{not json", 0)).contains("cannot be read"));
        assertTrue(only(this.read(NONE, 0)).contains("not a JSON object"));
        assertTrue(only(this.read("{\"contract\": 1, \"gate\": \"knip\", \"title\": 7}", 0)).contains("status"));
        assertTrue(only(this.read(PASSED, "[1]", NONE, 0)).contains("findings is not a list of text"));
        assertTrue(only(this.read("passing", NONE, NONE, 0)).contains("status passing"));
    }

    @Test
    @SneakyThrows
    void refusesAContractVersionOrGateItDidNotAskFor() {
        assertTrue(
            only(this.read(TEMPLATE.formatted(2, GATE, PASSED, "", NONE, NONE), 0)).contains("reads contract 1")
        );
        assertTrue(
            only(this.read(TEMPLATE.formatted(NodeGateResult.CONTRACT, "eslint", PASSED, "", NONE, NONE), 0))
                .contains("reported the gate eslint rather than knip")
        );
    }

    @Test
    @SneakyThrows
    void failsAResultThatContradictsItself() {
        assertTrue(only(this.read(PASSED, NONE, NONE, 2)).contains("exited 2 while reporting passed"));
        assertTrue(only(this.read("failed", NONE, NONE, 1)).contains("without naming a finding"));
        assertTrue(only(this.read(PASSED, "[\"src/a.ts:1\"]", NONE, 0)).contains("while listing 1 finding(s)"));
    }

    @Test
    @SneakyThrows
    void acceptsEvidenceWrittenBesideTheResultAndNothingElse() {
        Files.writeString(this.results.resolve("dependencies.json"), "[]\n");
        assertTrue(this.read(PASSED, NONE, "[\"dependencies.json\"]", 0).findings().clean());
        assertTrue(only(this.read(PASSED, NONE, "[\"bom.cdx.json\"]", 0)).contains("evidence bom.cdx.json"));
        assertTrue(only(this.read(PASSED, NONE, "[\"../../pom.xml\"]", 0)).contains("evidence ../../pom.xml"));
        Files.createDirectories(this.results.resolve("ui"));
        Files.writeString(this.results.resolve("ui/dependencies.json"), "[]\n");
        assertTrue(
            only(this.read(PASSED, NONE, "[\"ui/dependencies.json\"]", 0)).contains("evidence ui/dependencies.json"),
            "a nested project's evidence is not this project's"
        );
    }

    @Test
    @SneakyThrows
    void namesThePreviousEvidenceSoANewRunCannotPassOnIt() {
        Path result = this.results.resolve(GATE + ".json");
        assertEquals(List.of(), NodeGateResult.previousEvidence(result), "no previous result lists nothing");
        Files.writeString(
            result, TEMPLATE.formatted(NodeGateResult.CONTRACT, GATE, PASSED, "", NONE, "[\"bom.cdx.json\", \"../x\"]")
        );
        assertEquals(List.of(this.results.resolve("bom.cdx.json")), NodeGateResult.previousEvidence(result));
        Files.writeString(result, "{broken");
        assertEquals(List.of(), NodeGateResult.previousEvidence(result));
    }
}
