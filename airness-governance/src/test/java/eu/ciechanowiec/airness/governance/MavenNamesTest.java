package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MavenNamesTest {

    @Test
    void readsAReverseDomainOfAtLeastTwoLowerCaseSegments() {
        assertAll(
            () -> assertTrue(MavenNames.reverseDomain("org.sample")),
            () -> assertTrue(MavenNames.reverseDomain("eu.ciechanowiec.tools_x")),
            () -> assertFalse(MavenNames.reverseDomain("sample")),
            () -> assertFalse(MavenNames.reverseDomain("org.Sample")),
            () -> assertFalse(MavenNames.reverseDomain("org.my-org")),
            () -> assertFalse(MavenNames.reverseDomain("org..sample"))
        );
    }

    @Test
    void recognizesTheExampleDomainsAndWhatLiesUnderThem() {
        assertAll(
            () -> assertTrue(MavenNames.exampleDomain("com.example")),
            () -> assertTrue(MavenNames.exampleDomain("org.example.shop")),
            () -> assertTrue(MavenNames.exampleDomain("net.example")),
            () -> assertFalse(MavenNames.exampleDomain("com.examples")),
            () -> assertFalse(MavenNames.exampleDomain("org.sample"))
        );
    }

    @Test
    void readsLowerCaseWordsJoinedBySingleHyphens() {
        assertAll(
            () -> assertTrue(MavenNames.kebab("task-tracker")),
            () -> assertTrue(MavenNames.kebab("web2")),
            () -> assertFalse(MavenNames.kebab("Task-tracker")),
            () -> assertFalse(MavenNames.kebab("task--tracker")),
            () -> assertFalse(MavenNames.kebab("task_tracker")),
            () -> assertFalse(MavenNames.kebab("task-"))
        );
    }

    @Test
    void readsThreeNumbersOptionallyASnapshot() {
        assertAll(
            () -> assertTrue(MavenNames.plainVersion("1.0.0")),
            () -> assertTrue(MavenNames.plainVersion("10.20.30-SNAPSHOT")),
            () -> assertFalse(MavenNames.plainVersion("1.0")),
            () -> assertFalse(MavenNames.plainVersion("1.0.0-RC1")),
            () -> assertFalse(MavenNames.plainVersion("1.0.0-snapshot")),
            () -> assertFalse(MavenNames.plainVersion("${revision}"))
        );
    }
}
