package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * The root {@code AGENTS.md} names, in backticks, every domain the integration suite accepts, so a domain added to
 * {@code airness-it/verify.sh} is documented in the same change and an agent changing a rule learns the partial
 * run that diagnoses it.
 */
class IntegrationDomainsDocumentationTest {

    private static final String DOMAIN_CHECK = "case \"${selected_domain}\" in";
    private static final String CASE_END = "esac";
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("(?m)^\\s+([a-z0-9|-]+)\\)\\s*$");
    private static final String ALTERNATIVE = "\\|";

    @Test
    void namesEveryIntegrationDomain() {
        List<String> domains = domains();
        assertFalse(domains.isEmpty(), "the domain check of the integration suite is found");
        String instructions = read("AGENTS.md");
        assertEquals(
            List.of(),
            domains.stream().filter(domain -> !instructions.contains('`' + domain + '`')).toList(),
            "each integration domain appears, in backticks, in the root AGENTS.md"
        );
    }

    private static List<String> domains() {
        String script = read("airness-it/verify.sh");
        int start = script.indexOf(DOMAIN_CHECK);
        String check = script.substring(start, script.indexOf(CASE_END, start));
        return DOMAIN_PATTERN.matcher(check).results()
            .flatMap(match -> Arrays.stream(match.group(1).split(ALTERNATIVE)))
            .toList();
    }

    @SneakyThrows
    private static String read(String relative) {
        return Files.readString(SelfModules.repository().resolve(relative));
    }
}
