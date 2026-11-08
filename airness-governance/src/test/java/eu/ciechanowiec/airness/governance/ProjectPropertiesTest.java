package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

class ProjectPropertiesTest {

    @Test
    void rejectsAPropertyThatSetsAParameterTheParentsLeaveOpen() {
        Element pom = project(
            """
                <maven.test.failure.ignore>true</maven.test.failure.ignore>
                <checkstyle.maxAllowedViolations>500</checkstyle.maxAllowedViolations>
                <license.excludedGroups>.*</license.excludedGroups>
                <suppressionFile>accepted.xml</suppressionFile>
                <mdep.analyze.excludedClasses>com.example.*</mdep.analyze.excludedClasses>
                """
        );
        assertEquals(
            List.of(
                "Remove child property maven.test.failure.ignore; it can bypass the Airness verdict",
                "Remove child property checkstyle.maxAllowedViolations; it can bypass the Airness verdict",
                "Remove child property license.excludedGroups; it can bypass the Airness verdict",
                "Remove child property suppressionFile; it can bypass the Airness verdict",
                "Remove child property mdep.analyze.excludedClasses; it can bypass the Airness verdict"
            ),
            ProjectProperties.problems(pom).toList(),
            "Maven fills an unconfigured parameter from the property its descriptor names, so each of these "
                + "turns a failing check green without the plugin ever being declared"
        );
    }

    @Test
    void acceptsAPropertyThatTunesHowAnAllowedPluginRuns() {
        Element pom = project(
            """
                <argLine>-XX:+EnableDynamicAgentLoading</argLine>
                <surefire.timeout>600</surefire.timeout>
                <maven.compiler.showDeprecation>true</maven.compiler.showDeprecation>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                """
        );
        assertEquals(
            List.of(), ProjectProperties.problems(pom).toList(),
            "a property the table classes as the project's tunes a run and decides no verdict"
        );
    }

    @Test
    void answersForTheSettingsTheAirnessPluginReads() {
        assertAll(
            () -> assertTrue(ProjectProperties.reserves("jacoco.reportFile")),
            () -> assertTrue(ProjectProperties.reserves("skipTests")),
            () -> assertTrue(ProjectProperties.reserves("airness.enforce")),
            () -> assertFalse(ProjectProperties.reserves("airness.package.root")),
            () -> assertFalse(ProjectProperties.reserves("argLine")),
            () -> assertTrue(ProjectProperties.ownedByProject("airness.node.projects")),
            () -> assertFalse(ProjectProperties.ownedByProject("airness.enforce"))
        );
    }

    private static Element project(String properties) {
        return Xml.parse("<project><properties>" + properties + "</properties></project>").getDocumentElement();
    }
}
