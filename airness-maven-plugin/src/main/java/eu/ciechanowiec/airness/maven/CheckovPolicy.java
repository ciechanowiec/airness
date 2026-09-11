package eu.ciechanowiec.airness.maven;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * The packaged, exhaustive Checkov rule inventory and its centrally justified exclusions.
 */
@UtilityClass
final class CheckovPolicy {

    private static final String RESOURCE = "/eu/ciechanowiec/airness/scanners/checkov-policy.tsv";
    private static final int COLUMNS = 4;

    static List<String> frameworks() {
        return List.of(
            "ansible", "argo_workflows", "arm", "azure_pipelines", "bicep", "bitbucket_pipelines",
            "circleci_pipelines", "cloudformation", "dockerfile", "github_actions", "gitlab_ci",
            "helm", "kubernetes", "kustomize", "serverless", "terraform", "terraform_json", "terraform_plan"
        );
    }

    static String exclusions() throws IOException {
        return rows().stream().filter(row -> "exclude".equals(row.get(1)))
            .map(List::getFirst).collect(Collectors.joining(","));
    }

    static void verify(String listing) throws IOException {
        Set<String> actual = listing.lines().filter(line -> line.startsWith("|"))
            .map(line -> line.split("\\|", -1)).filter(columns -> columns.length > COLUMNS)
            .map(columns -> columns[2].strip()).filter(id -> id.matches("CKV2?_[A-Z0-9_]+"))
            .collect(Collectors.toUnmodifiableSet());
        Set<String> expected = rows().stream().map(List::getFirst).collect(Collectors.toUnmodifiableSet());
        if (!actual.equals(expected)) {
            throw new IOException(
                "Checkov rule registry differs from its packaged fleet policy; regenerate the inventory"
            );
        }
    }

    static List<List<String>> rows() throws IOException {
        URL resource = Optional.ofNullable(CheckovPolicy.class.getResource(RESOURCE))
            .orElseThrow(() -> new IOException("Missing Checkov policy " + RESOURCE));
        try (InputStream input = resource.openStream()) {
            return parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    static List<List<String>> parse(String text) throws IOException {
        List<List<String>> rows = text.lines().filter(line -> !line.startsWith("#")).filter(line -> !line.isBlank())
            .map(line -> Arrays.asList(line.split("\t", -1))).toList();
        boolean malformed = rows.stream().anyMatch(CheckovPolicy::malformed);
        long unique = rows.stream().map(List::getFirst).distinct().count();
        if (rows.isEmpty() || malformed || unique != rows.size()) {
            throw new IOException("Checkov policy must name each rule once with a disposition and justification");
        }
        return rows;
    }

    private static boolean malformed(List<String> row) {
        return row.size() != COLUMNS || !row.getFirst().matches("CKV2?_[A-Z0-9_]+")
            || Stream.of("enable", "exclude").noneMatch(row.get(1)::equals)
            || row.stream().anyMatch(String::isBlank);
    }
}
