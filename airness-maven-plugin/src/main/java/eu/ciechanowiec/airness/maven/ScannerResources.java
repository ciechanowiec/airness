package eu.ciechanowiec.airness.maven;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Materializes only programs and policy carried by the installed harness.
 */
@UtilityClass
final class ScannerResources {

    private static final String ROOT = "/eu/ciechanowiec/airness/scanners/";
    private static final Pattern RULE = Pattern.compile("(?s)\\R?[ \\t]*<suppress>.*?</suppress>");
    private static final Pattern PACKAGE_URL = Pattern.compile(
        "(?s)<packageUrl(\\s+regex=\"true\")?\\s*>\\s*(.*?)\\s*</packageUrl>"
    );

    /**
     * Copies a Dependency-Check suppression file, keeping only the rules whose package address matches a package the
     * scan will see. Dependency-Check fails on a rule that matches nothing, so a rule for a plugin only the Java
     * parents resolve, such as the Checkstyle and PMD wrappers, is left out of a module that never resolves it,
     * and a module that resolves it gets the rule unchanged. A rule that names no package address is kept.
     *
     * @param name     the packaged suppression file
     * @param output   the directory to write it into
     * @param packages the package addresses of the artifacts the scan reads, such as
     *                 {@code pkg:maven/org.apache.maven.plugins/maven-pmd-plugin@3.28.0?type=jar}
     * @return the written file
     * @throws IOException when the resource is missing or the file cannot be written
     */
    static Path copyApplicable(String name, Path output, Collection<String> packages) throws IOException {
        Path file = copy(name, output);
        String document = Files.readString(file);
        Matcher rules = RULE.matcher(document);
        StringBuilder kept = new StringBuilder();
        while (rules.find()) {
            Matcher address = PACKAGE_URL.matcher(rules.group());
            boolean applies = !address.find()
                || matchesAny(Optional.ofNullable(address.group(1)).isPresent(), address.group(2), packages);
            rules.appendReplacement(kept, Matcher.quoteReplacement(applies ? rules.group() : ""));
        }
        rules.appendTail(kept);
        Files.writeString(file, kept.toString());
        return file;
    }

    private static boolean matchesAny(boolean regex, String address, Collection<String> packages) {
        Pattern pattern = Pattern.compile(regex ? address : Pattern.quote(address));
        return packages.stream().anyMatch(candidate -> pattern.matcher(candidate).find());
    }

    static Path copy(String name, Path output) throws IOException {
        URL resource = Optional.ofNullable(ScannerResources.class.getResource(ROOT + name))
            .orElseThrow(() -> new IOException("Missing packaged scanner resource " + name));
        Path file = output.resolve(name);
        try (InputStream input = resource.openStream()) {
            Files.copy(input, file);
        }
        return file;
    }
}
