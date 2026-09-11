package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class InfrastructureYamlTest {

    @Test
    @SneakyThrows
    void classifiesDocumentsByTheirStructureAndRejectsObjectConstruction() {
        assertEquals("cloudformation", InfrastructureYaml.framework("Resources: {}\n"));
        assertEquals(
            "arm", InfrastructureYaml.framework("$schema: https://schema.management.azure.com/template.json\n")
        );
        assertEquals("terraform_plan", InfrastructureYaml.framework("resource_changes: []\nterraform_version: 1\n"));
        assertEquals("ansible", InfrastructureYaml.framework("- hosts: all\n"));
        assertEquals("argo_workflows", InfrastructureYaml.framework("apiVersion: argoproj.io/v1\nkind: Workflow\n"));
        assertEquals("", InfrastructureYaml.framework("type: recipe\n---\ntype: recipe\n"));
        assertEquals(List.of(), InfrastructureYaml.documents("---\n"));
        assertThrows(IOException.class, () -> InfrastructureYaml.documents("!!java.lang.Object {}"));
        assertThrows(IOException.class, () -> InfrastructureYaml.documents("spec: {}\nspec: {}\n"));
    }

    @Test
    @SneakyThrows
    void ignoresUnrelatedSequencesAndScalarDocuments() {
        assertEquals("", InfrastructureYaml.framework("- unrelated\n- another\n"));
        assertEquals("", InfrastructureYaml.framework("plain data\n"));
    }
}
