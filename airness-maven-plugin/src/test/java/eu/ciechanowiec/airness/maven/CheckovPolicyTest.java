package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class CheckovPolicyTest {

    @Test
    @SneakyThrows
    void preservesPortableWorkloadsWithoutWeakeningTransportSecurity() {
        String exclusions = CheckovPolicy.exclusions();
        assertTrue(exclusions.contains("CKV_DOCKER_2"));
        assertTrue(exclusions.contains("CKV_GHA_7"));
        assertTrue(exclusions.contains("CKV2_ADO_1"));
        assertFalse(exclusions.contains("CKV2_DOCKER_2,"));
        assertFalse(CheckovPolicy.frameworks().contains("sca_image"));
        assertFalse(CheckovPolicy.frameworks().contains("secrets"));
        assertTrue(
            CheckovScan.arguments("dockerfile").containsAll(List.of("--skip-download", "--config-file", "--framework"))
        );
    }

    @Test
    @SneakyThrows
    void rejectsUnclassifiedOrMissingRules() {
        String listing = CheckovPolicy.rows().stream()
            .map(row -> "| 0 | " + row.getFirst() + " | type | resource | title | framework |")
            .collect(Collectors.joining("\n"));
        CheckovPolicy.verify(listing);
        assertThrows(IOException.class, () -> CheckovPolicy.verify(listing + "\n| 0 | CKV_NEW_1 | type | resource |"));
        assertThrows(IOException.class, () -> CheckovPolicy.verify(""));
    }

    @Test
    void rejectsMalformedDuplicateAndUnexplainedPolicyEntries() {
        for (
            String policy : List.of(
                "", "CKV_TEST_1\tenable\t\ttitle", "CKV_TEST_1\tunknown\treason\ttitle",
                "CKV_TEST_1\tenable\treason\ttitle\nCKV_TEST_1\tenable\treason\ttitle"
            )
        ) {
            assertThrows(IOException.class, () -> CheckovPolicy.parse(policy), policy);
        }
    }
}
