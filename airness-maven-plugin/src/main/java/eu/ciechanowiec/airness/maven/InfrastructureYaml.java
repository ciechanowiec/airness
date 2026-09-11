package eu.ciechanowiec.airness.maven;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Reads infrastructure document shapes without constructing application objects.
 */
@UtilityClass
final class InfrastructureYaml {

    private static final String CLOUDFORMATION_VERSION = "AWSTemplateFormatVersion";
    private static final String RESOURCES = "Resources";
    private static final String SCHEMA = "$schema";
    private static final String ARM_SCHEMA = "schema.management.azure.com";
    private static final String RESOURCE_CHANGES = "resource_changes";
    private static final String TERRAFORM_VERSION = "terraform_version";
    private static final String API_VERSION = "apiVersion";
    private static final String KIND = "kind";

    static List<Object> documents(String text) throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        List<Object> documents = new ArrayList<>();
        try {
            yaml.loadAll(text).forEach(value -> Optional.ofNullable(value).ifPresent(documents::add));
        } catch (YAMLException exception) {
            throw new IOException("Invalid infrastructure YAML", exception);
        }
        return List.copyOf(documents);
    }

    static String framework(String text) throws IOException {
        return documents(text).stream().map(InfrastructureYaml::framework)
            .filter(value -> !value.isEmpty()).findFirst().orElse("");
    }

    private static String framework(Object value) {
        if (value instanceof Map<?, ?> mapping) {
            return mappingFramework(mapping);
        }
        return value instanceof Collection<?> list && list.stream().anyMatch(InfrastructureYaml::play)
            ? "ansible" : "";
    }

    private static boolean play(Object value) {
        return value instanceof Map<?, ?> mapping && mapping.containsKey("hosts");
    }

    private static String mappingFramework(Map<?, ?> mapping) {
        String cloud = cloudFramework(mapping);
        return cloud.isEmpty() ? workloadFramework(mapping) : cloud;
    }

    private static String cloudFramework(Map<?, ?> mapping) {
        boolean cloudformation = mapping.containsKey(CLOUDFORMATION_VERSION) || mapping.containsKey(RESOURCES);
        if (cloudformation) {
            return "cloudformation";
        }
        boolean arm = String.valueOf(mapping.get(SCHEMA)).contains(ARM_SCHEMA);
        return arm ? "arm" : plannedFramework(mapping);
    }

    private static String workloadFramework(Map<?, ?> mapping) {
        if (!mapping.containsKey(API_VERSION) || !mapping.containsKey(KIND)) {
            return "";
        }
        return String.valueOf(mapping.get(API_VERSION)).contains("argoproj.io") ? "argo_workflows" : "kubernetes";
    }

    private static boolean terraformPlan(Map<?, ?> mapping) {
        return mapping.containsKey(RESOURCE_CHANGES) && mapping.containsKey(TERRAFORM_VERSION);
    }

    private static String plannedFramework(Map<?, ?> mapping) {
        return terraformPlan(mapping) ? "terraform_plan" : "";
    }
}
