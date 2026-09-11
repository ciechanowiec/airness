package eu.ciechanowiec.airness.maven;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Routes infrastructure to its native framework and requires evidence that the framework actually ran.
 */
@UtilityClass
final class CheckovInputs {

    private static final String TERRAFORM_KIND = "terraform";
    private static final String KUBERNETES_KIND = "kubernetes";
    private static final String BICEP_SUFFIX = ".bicep";
    private static final String TERRAFORM_SUFFIX = ".tf";
    private static final Path WORKFLOWS = Path.of(".github", "workflows");
    private static final Path CIRCLECI = Path.of(".circleci");
    private static final Pattern TERRAFORM = Pattern.compile("(?m)^\\s*(?:resource|data|module)\\s+\"");
    private static final Pattern DOCKER = Pattern.compile("Dockerfile(?:\\..+)?|.+\\.[Dd]ockerfile");
    private static final Pattern YAML = Pattern.compile(".+\\.ya?ml");
    private static final Pattern DATA = Pattern.compile(".+\\.(?:ya?ml|json)");
    private static final Set<String> RENDERERS = Set.of("helm", "kustomize", "ansible");
    private static final Map<String, String> NAMED = Map.of(
        "Chart.yaml", "helm", "kustomization.yaml", "kustomize", "kustomization.yml", "kustomize",
        "Kustomization", "kustomize", "azure-pipelines.yml", "azure_pipelines", "azure-pipelines.yaml",
        "azure_pipelines",
        "bitbucket-pipelines.yml", "bitbucket_pipelines", ".gitlab-ci.yml", "gitlab_ci",
        "serverless.yml", "serverless", "serverless.yaml", "serverless"
    );
    private static final List<Map.Entry<Pattern, String>> NATIVE_SYNTAX = List.of(
        Map.entry(Pattern.compile("AWSTemplateFormatVersion|AWS::"), "cloudformation"),
        Map.entry(Pattern.compile("schema\\.management\\.azure\\.com"), "arm"),
        Map.entry(Pattern.compile("(?s)(?=.*resource_changes)(?=.*terraform_version)"), "terraform_plan"),
        Map.entry(Pattern.compile("(?s)(?=.*argoproj\\.io)(?=.*Workflow)"), "argo_workflows"),
        Map.entry(Pattern.compile("(?m)^kind:\\s*\\w|(?s)(?=.*\"apiVersion\")(?=.*\"kind\")"), KUBERNETES_KIND),
        Map.entry(Pattern.compile("(?m)^\\s*(?:-\\s*)?hosts:"), "ansible")
    );

    static void verify(Path input, Collection<Path> selected, Set<String> frameworks) throws IOException {
        for (Path file : selected) {
            String framework = required(input.relativize(file), Files.readString(file));
            if (!framework.isEmpty() && !frameworks.contains(framework)) {
                throw new IOException(
                    "Checkov supplied no " + framework + " evidence for " + input.relativize(file)
                        + "; provide supported, locally resolvable inputs and the required renderer"
                );
            }
        }
    }

    static String required(Path relative, String text) {
        String name = relative.getFileName().toString();
        String named = NAMED.getOrDefault(name, "");
        if (!named.isEmpty()) {
            return named;
        }
        String typed = sourceType(name, text);
        return typed.isEmpty() ? pathType(relative, text) : typed;
    }

    private static String sourceType(String name, String text) {
        if (DOCKER.matcher(name).matches()) {
            return "dockerfile";
        }
        return name.endsWith(BICEP_SUFFIX) ? "bicep" : terraformType(name, text);
    }

    private static String pathType(Path relative, String text) {
        String name = relative.getFileName().toString();
        if (pipeline(relative, WORKFLOWS)) {
            return "github_actions";
        }
        return pipeline(relative, CIRCLECI) ? "circleci_pipelines" : structuredFile(name, text);
    }

    static List<Path> select(String framework, Path root, Collection<Path> files) throws IOException {
        List<Path> selected = new ArrayList<>();
        for (Path file : files) {
            if (matches(framework, root, file)) {
                selected.add(file);
            }
        }
        return withDependencies(framework, files, selected);
    }

    private static boolean matches(String framework, Path root, Path file) throws IOException {
        boolean renderedKubernetes = KUBERNETES_KIND.equals(framework) && rendered(root, file);
        boolean readable = candidate(file) && !renderedKubernetes;
        return readable && belongsTo(framework, root, file);
    }

    static boolean candidate(Path file) {
        String name = file.getFileName().toString();
        return NAMED.containsKey(name) || name.matches(".+\\.(?:ya?ml|json|tf|tfvars|bicep)")
            || DOCKER.matcher(name).matches();
    }

    private static boolean rendered(Path root, Path file) {
        return Stream.iterate(file.getParent(), path -> path.startsWith(root), Path::getParent)
            .anyMatch(
                path -> Stream.of("Chart.yaml", "kustomization.yaml", "kustomization.yml", "Kustomization").map(
                    path::resolve
                ).anyMatch(Files::isRegularFile)
            );
    }

    private static boolean terraformFile(String framework, String name) {
        boolean hclSuffix = name.endsWith(TERRAFORM_SUFFIX) || name.endsWith(".tfvars");
        boolean hcl = TERRAFORM_KIND.equals(framework) && hclSuffix;
        boolean json = "terraform_json".equals(framework) && name.endsWith(".tf.json");
        return hcl || json;
    }

    private static String structured(String text) {
        try {
            return InfrastructureYaml.framework(text);
        } catch (IOException _) {
            // Native parsers still receive their tagged or malformed input and decide its validity.
            return NATIVE_SYNTAX.stream().filter(entry -> entry.getKey().matcher(text).find())
                .map(Map.Entry::getValue).findFirst().orElse("");
        }
    }

    private static boolean pipeline(Path relative, Path directory) {
        return relative.startsWith(directory) && YAML.matcher(relative.getFileName().toString()).matches();
    }

    private static List<Path> withDependencies(String framework, Collection<Path> files, List<Path> selected) {
        return RENDERERS.contains(framework) && !selected.isEmpty() ? List.copyOf(files) : List.copyOf(selected);
    }

    private static String structuredFile(String name, String text) {
        return DATA.matcher(name).matches() ? structured(text) : "";
    }

    private static String terraformType(String name, String text) {
        boolean hcl = name.endsWith(TERRAFORM_SUFFIX) && TERRAFORM.matcher(text).find();
        if (hcl) {
            return TERRAFORM_KIND;
        }
        boolean json = name.endsWith(".tf.json") && text.contains("\"resource\"");
        return json ? "terraform_json" : "";
    }

    private static boolean belongsTo(String framework, Path root, Path file) throws IOException {
        return framework.equals(required(root.relativize(file), Files.readString(file)))
            || terraformFile(framework, file.getFileName().toString());
    }
}
