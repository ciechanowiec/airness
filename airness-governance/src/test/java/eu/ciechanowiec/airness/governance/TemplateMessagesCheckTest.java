package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplateMessagesCheckTest {

    private static final String APPLICATION = "example.Application";
    private static final TemplateMessageReference REFERENCE = new TemplateMessageReference(
        "templates/page.html",
        "src/main/resources/templates/page.html:3:1", "browse.more"
    );
    @TempDir
    private Path root;

    @Test
    @SneakyThrows
    void rejectsMissingKeysWithTheSourceLocationEvenBesideASuccessfulContext() {
        TemplateMessageInputs inputs = inputs(1);
        Path evidence = this.root.resolve("context.evidence");
        inputs.write(TemplateMessageInputs.beside(evidence));
        Files.write(
            evidence, List.of(
                line(inputs, "assessed", "1"), line(inputs, "missing", REFERENCE.encoded()),
                line(inputs, "assessed", "1")
            )
        );
        TemplateMessagesCheck check = new TemplateMessagesCheck(
            inputs, TemplateMessageInputs.beside(evidence), evidence
        );
        assertFalse(Verdicts.clean(check.findings()));
        assertTrue(Verdicts.offences(check.findings(), "missing from base").getFirst().contains(REFERENCE.location()));
        assertEquals(2, check.checked());
    }

    @Test
    @SneakyThrows
    void refusesMissingStaleAndDifferentManifestEvidence() {
        TemplateMessageInputs inputs = inputs(2);
        Path evidence = this.root.resolve("context.evidence");
        Path manifest = TemplateMessageInputs.beside(evidence);
        assertFalse(Verdicts.clean(new TemplateMessagesCheck(inputs, manifest, evidence).findings()));
        inputs.write(manifest);
        assertFalse(Verdicts.clean(new TemplateMessagesCheck(inputs, manifest, evidence).findings()));
        Files.write(evidence, List.of(line(inputs(1), "assessed", "1")));
        assertFalse(Verdicts.clean(new TemplateMessagesCheck(inputs, manifest, evidence).findings()));
        TemplateMessageInputs changed = new TemplateMessageInputs(
            2, inputs.applications(),
            List.of(new TemplateMessageReference("templates/page.html", REFERENCE.location(), "changed"))
        );
        changed.write(manifest);
        assertFalse(Verdicts.clean(new TemplateMessagesCheck(inputs, manifest, evidence).findings()));
    }

    @Test
    @SneakyThrows
    void reportsCustomConfigurationsAsUnassessedAndLookupErrorsAsFailures() {
        TemplateMessageInputs inputs = inputs(1);
        Path evidence = this.root.resolve("context.evidence");
        Path manifest = TemplateMessageInputs.beside(evidence);
        inputs.write(manifest);
        Files.write(evidence, List.of(line(inputs, "unassessed", "custom source")));
        TemplateMessagesCheck unassessed = new TemplateMessagesCheck(inputs, manifest, evidence);
        assertTrue(Verdicts.clean(unassessed.findings()));
        assertEquals(0, unassessed.checked());
        assertTrue(unassessed.unassessed().getFirst().contains("custom source"));
        Files.write(evidence, List.of(line(inputs, "error", "lookup failed")));
        assertFalse(Verdicts.clean(new TemplateMessagesCheck(inputs, manifest, evidence).findings()));
    }

    @Test
    void requiresNoRuntimeAssessmentForAnEmptyInventory() {
        TemplateMessageInputs inputs = new TemplateMessageInputs(1, List.of(APPLICATION), List.of());
        assertTrue(
            Verdicts.clean(
                new TemplateMessagesCheck(
                    inputs, this.root.resolve("inputs"),
                    this.root.resolve("evidence")
                ).findings()
            )
        );
    }

    private static TemplateMessageInputs inputs(long started) {
        return new TemplateMessageInputs(started, List.of(APPLICATION), List.of(REFERENCE));
    }

    private static String line(TemplateMessageInputs inputs, String status, String detail) {
        return "messages %d %s %s %s %s %s".formatted(
            inputs.started(), inputs.digest(),
            TemplateMessageReference.encode(APPLICATION), TemplateMessageReference.encode("context"), status,
            TemplateMessageReference.encode(detail)
        );
    }
}
