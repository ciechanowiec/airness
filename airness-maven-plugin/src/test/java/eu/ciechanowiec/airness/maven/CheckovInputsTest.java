package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckovInputsTest {

    @Test
    @SneakyThrows
    void refusesASelectedFrameworkThatTheScannerDidNotAssess(@TempDir Path directory) {
        Path docker = Files.writeString(directory.resolve("Dockerfile"), "FROM scratch\n");
        assertThrows(IOException.class, () -> CheckovInputs.verify(directory, List.of(docker), Set.of()));
        CheckovInputs.verify(directory, List.of(docker), Set.of("dockerfile"));
        assertEquals("dockerfile", CheckovInputs.required(Path.of("service.dockerfile"), "FROM scratch"));
    }

    @Test
    void recognizesRequiredRenderersAndSecurityBearingInputKinds() {
        assertEquals("helm", CheckovInputs.required(Path.of("chart/Chart.yaml"), ""));
        assertEquals("kustomize", CheckovInputs.required(Path.of("kustomization.yaml"), ""));
        assertEquals(
            "terraform", CheckovInputs.required(Path.of("main.tf"), "resource \"aws_s3_bucket\" \"bucket\" {}")
        );
        assertEquals("github_actions", CheckovInputs.required(Path.of(".github/workflows/build.yml"), ""));
        assertEquals("kubernetes", CheckovInputs.required(Path.of("pod.yaml"), "apiVersion: v1\nkind: Pod\n"));
        assertEquals("", CheckovInputs.required(Path.of("application.yaml"), "spring:\n"));
    }

    @Test
    @SneakyThrows
    void routesEachFrameworkWithoutFeedingApplicationOrAnalyzerYamlToCloudFormation(@TempDir Path directory) {
        Path recipe = Files.writeString(directory.resolve("recipe.yaml"), "type: recipe\n---\ntype: recipe\n");
        Path cloud = Files.writeString(directory.resolve("stack.yaml"), "AWSTemplateFormatVersion: 2010-09-09\n");
        Path script = Files.writeString(directory.resolve("script.sh"), "exit 0\n");
        Path variables = Files.writeString(directory.resolve("values.tfvars"), "name = \"example\"\n");
        List<Path> files = List.of(recipe, cloud, script, variables);
        assertEquals(List.of(cloud), CheckovInputs.select("cloudformation", directory, files));
        assertEquals(List.of(variables), CheckovInputs.select("terraform", directory, files));
        assertEquals(List.of(), CheckovInputs.select("helm", directory, files));
    }

    @Test
    @SneakyThrows
    void keepsRendererInputsTogetherAndDoesNotScanRawHelmTemplatesAsKubernetes(@TempDir Path directory) {
        Path chart = Files.writeString(directory.resolve("Chart.yaml"), "apiVersion: v2\n");
        Path template = Files.writeString(
            directory.resolve("pod.yaml"), "kind: Pod\nmetadata: {{ .Values.metadata }}\n"
        );
        List<Path> files = List.of(chart, template);
        assertEquals(files, CheckovInputs.select("helm", directory, files));
        assertEquals(List.of(), CheckovInputs.select("kubernetes", directory, files));
    }

    @Test
    void recognizesCloudTemplatesPlansAndPlaybooks() {
        assertEquals(
            "arm", CheckovInputs.required(
                Path.of("main.json"), "$schema: https://schema.management.azure.com/template.json\n"
            )
        );
        assertEquals(
            "terraform_plan", CheckovInputs.required(
                Path.of("plan.json"), "resource_changes: []\nterraform_version: 1\n"
            )
        );
        assertEquals(
            "argo_workflows", CheckovInputs.required(
                Path.of("workflow.yaml"), "apiVersion: argoproj.io/v1\nkind: Workflow\n"
            )
        );
        assertEquals("ansible", CheckovInputs.required(Path.of("playbook.yml"), "- hosts: all\n"));
        assertEquals("bicep", CheckovInputs.required(Path.of("main.bicep"), ""));
    }

    @Test
    void keepsNativeTaggedOrMalformedInfrastructureInItsOwningScanner() {
        assertEquals(
            "cloudformation", CheckovInputs.required(
                Path.of("stack.yaml"),
                "AWSTemplateFormatVersion: !Ref Version\n"
            )
        );
        assertEquals(
            "arm", CheckovInputs.required(
                Path.of("arm.yaml"),
                "$schema: https://schema.management.azure.com\ninvalid: [\n"
            )
        );
        assertEquals(
            "terraform_plan", CheckovInputs.required(
                Path.of("plan.json"),
                "{\"terraform_version\":1,\"resource_changes\":["
            )
        );
        assertEquals(
            "argo_workflows", CheckovInputs.required(
                Path.of("workflow.yaml"),
                "apiVersion: argoproj.io/v1\nkind: Workflow\ninvalid: [\n"
            )
        );
        assertEquals(
            "kubernetes", CheckovInputs.required(
                Path.of("pod.yaml"),
                "kind: Pod\ninvalid: [\n"
            )
        );
        assertEquals(
            "kubernetes", CheckovInputs.required(
                Path.of("pod.json"),
                "{\"apiVersion\":\"v1\",\"kind\":\"Pod\","
            )
        );
        assertEquals("ansible", CheckovInputs.required(Path.of("playbook.yaml"), "- hosts: [\n"));
        assertEquals("", CheckovInputs.required(Path.of("settings.yaml"), "invalid: [\n"));
    }

    @Test
    @SneakyThrows
    void retainsTerraformJsonAndKustomizeDependencies(@TempDir Path directory) {
        Path plan = Files.writeString(directory.resolve("main.tf.json"), "{}\n");
        Path marker = Files.writeString(directory.resolve("kustomization.yml"), "resources: []\n");
        Path manifest = Files.writeString(directory.resolve("pod.yaml"), "apiVersion: v1\nkind: Pod\n");
        Path docker = Files.writeString(directory.resolve("Dockerfile"), "FROM scratch\n");
        List<Path> files = List.of(plan, marker, manifest, docker);
        assertEquals(List.of(plan), CheckovInputs.select("terraform_json", directory, files));
        assertEquals(files, CheckovInputs.select("kustomize", directory, files));
        assertEquals(List.of(), CheckovInputs.select("kubernetes", directory, files));
        assertEquals(List.of(docker), CheckovInputs.select("dockerfile", directory, files));
    }
}
