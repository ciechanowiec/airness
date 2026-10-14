package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ReportLicensePolicy;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Reads the effective license check configuration rather than maintaining another allowlist.
 */
@UtilityClass
final class MavenReportPolicy {

    static ReportLicensePolicy from(MavenProject project) {
        Xpp3Dom configuration = Optional.ofNullable(project.getPlugin("org.codehaus.mojo:license-maven-plugin"))
            .map(plugin -> (Xpp3Dom) plugin.getConfiguration())
            .orElseThrow(() -> new IllegalStateException("Missing inherited license check configuration"));
        Set<String> allowed = Set.copyOf(values(configuration, "includedLicenses"));
        Map<String, String> aliases = values(configuration, "licenseMerges").stream()
            .flatMap(merge -> aliases(merge).entrySet().stream())
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        return new ReportLicensePolicy(allowed, aliases);
    }

    private static List<String> values(Xpp3Dom configuration, String name) {
        Xpp3Dom container = Optional.ofNullable(configuration.getChild(name))
            .orElseThrow(() -> new IllegalStateException("Missing inherited license policy " + name));
        return Arrays.stream(container.getChildren()).map(Xpp3Dom::getValue).map(String::strip).toList();
    }

    private static Map<String, String> aliases(String merge) {
        String[] parts = merge.split("\\|", -1);
        return Arrays.stream(parts).skip(1).map(String::strip)
            .collect(Collectors.toUnmodifiableMap(value -> value, _ -> parts[0].strip()));
    }
}
