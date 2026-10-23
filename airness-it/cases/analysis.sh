#!/usr/bin/env sh

run_analysis_cases() {
    # One fixture carries every planted analyzer defect that no other one can mask: each lives in a file
    # of its own and is read by a different rule. The report-only run asserts every one of them, at its
    # file and line, from the analyzers' own reports, and the enforcing run beside it proves that what the
    # batch reports fails a build. The rule variants this batch replaced (the final and nearby locals, the
    # joined dot, an ordinary Spring component, a named request map) are proven in process by
    # CheckstyleVariantTest; what stays here is that the installed parent selects and wires each rule.
    new_consumer analysis-batch
    analysis_batch="${consumer_directory}"
    write_analysis_batch "${analysis_batch}"

    run_maven analysis_batch analysis "${analysis_batch}" \
        checkstyle:check pmd:check pmd:cpd-check -Dairness.enforce=false
    expect_exit analysis_batch 'report-only: analyzer findings do not stop compatible goals' 0
    expect_match analysis_batch 'analysis: the Checkstyle goal actually executed' \
        'checkstyle:[^:]+:check'
    expect_match analysis_batch 'analysis: the PMD goal actually executed' \
        'pmd:[^:]+:check'
    expect_match analysis_batch 'analysis: report-only still executes CPD after PMD findings' \
        'pmd:[^:]+:cpd-check'
    expect_finding analysis_batch 'checkstyle: the representative finding stays visible at its file' \
        checkstyle 'src/main/java/com/example/InferredLocal.java:7' 'Write the type'
    expect_no_finding analysis_batch 'checkstyle: the clean control stays free of the representative rule' \
        checkstyle 'src/main/java/com/example/Example.java' 'Write the type'
    expect_finding analysis_batch 'pmd: the representative rule ID stays visible at its file' \
        pmd 'src/main/java/com/example/BlankJustification.java:6' '^JustificationNeedsText '
    expect_finding analysis_batch 'cpd: the duplicated pair stays visible' \
        cpd 'src/main/java/com/example/FirstScorer.java' 'SecondScorer[.]java'
    expect_finding analysis_batch 'analysis: a distant local fails the packaged distance check' \
        checkstyle 'src/main/java/com/example/Reading.java:9' \
        '^VariableDeclarationUsageDistance .*remembered'
    expect_no_match analysis_batch 'analysis: the distance diagnostic never recommends final' \
        'making that variable final'
    expect_finding analysis_batch 'analysis: a detached chained dot fails packaged Checkstyle' \
        checkstyle 'src/main/java/com/example/JoinedValues.java:14' '^AirnessDetachedChainDot .*Join this dot'
    expect_finding analysis_batch 'checkstyle: an Airness rule reports on test sources too' \
        checkstyle 'src/test/java/com/example/RatioTest.java:11' 'floating-point literal suffix in upper case'
    expect_finding analysis_batch 'checkstyle: the plain parent keeps the finality rule for a repository' \
        checkstyle 'src/main/java/com/example/Catalogue.java:8' '^RequireFinalClass '

    run_maven analysis_batch_enforced analysis "${analysis_batch}" checkstyle:check
    expect_exit analysis_batch_enforced 'checkstyle: the batched packaged findings fail enforcement' 1
    expect_match analysis_batch_enforced 'checkstyle: enforcement names the exact fixture' \
        'InferredLocal[.]java:.*Write the type'
    # Every batched Checkstyle finding is named by the failing run too, so a severity below the failing
    # threshold could not hide behind the one representative finding.
    expect_match analysis_batch_enforced 'checkstyle: enforcement names the distant local' \
        'Reading[.]java:.*VariableDeclarationUsageDistance'
    expect_match analysis_batch_enforced 'checkstyle: enforcement names the detached dot' \
        'JoinedValues[.]java:.*AirnessDetachedChainDot'
    expect_match analysis_batch_enforced 'checkstyle: enforcement names the test-source finding' \
        'RatioTest[.]java:.*floating-point literal suffix in upper case'
    expect_match analysis_batch_enforced 'checkstyle: enforcement names the plain-parent repository' \
        'Catalogue[.]java:.*RequireFinalClass'
    run_analysis_lifecycle
    run_early_analysis_lifecycle
    run_jspecify_annotation_locations
    run_modernization_cases
}

write_analysis_batch() {
    cat > "$1/src/main/java/com/example/InferredLocal.java" <<'JAVA'
package com.example;

/** Carries the representative packaged-Checkstyle finding. */
final class InferredLocal {

    String value() {
        var value = "inferred";
        return value;
    }
}
JAVA
    cat > "$1/src/main/java/com/example/BlankJustification.java" <<'JAVA'
package com.example;

import eu.ciechanowiec.airness.Justification;

/** Carries the representative packaged-PMD finding. */
@Justification(" ")
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
final class BlankJustification {

    private BlankJustification() {
    }
}
JAVA
    cat > "$1/src/main/java/com/example/FirstScorer.java" <<'JAVA'
package com.example;

import java.util.List;
import java.util.Locale;

/** Carries a block long enough to cross the duplication bound. */
final class FirstScorer {

    private FirstScorer() {
    }

    static int score(List<String> values) {
        int total = 0;
        for (int index = 0; index < values.size(); index++) {
            String entry = values.get(index);
            if (entry.isEmpty()) {
                continue;
            }
            String trimmed = entry.trim().toLowerCase(Locale.ROOT);
            if (trimmed.startsWith("a") || trimmed.startsWith("b")) {
                total = total + trimmed.length() * 2;
            } else if (trimmed.endsWith("z")) {
                total = total - trimmed.length();
            } else {
                total = total + 1;
            }
            if (total > 1000) {
                total = 1000;
            }
        }
        return total;
    }
}
JAVA
    sed 's/FirstScorer/SecondScorer/g' "$1/src/main/java/com/example/FirstScorer.java" \
        > "$1/src/main/java/com/example/SecondScorer.java"
    cat > "$1/src/main/java/com/example/Reading.java" <<'JAVA'
package com.example;

/**
 * Holds a value across several operations to exercise the distance diagnostic.
 */
final class Reading {

    String before(StringBuilder content) {
        String remembered = content.toString();
        content.append('a');
        content.append('b');
        content.append('c');
        content.append('d');
        return remembered;
    }
}
JAVA
    cat > "$1/src/main/java/com/example/JoinedValues.java" <<'JAVA'
package com.example;

import java.util.List;

/**
 * Joins the supplied values into one readable line.
 */
final class JoinedValues {

    String read(List<String> values) {
        return String.join(
            ",", values
        )
        .strip();
    }
}
JAVA
    cat > "$1/src/main/java/com/example/Catalogue.java" <<'JAVA'
package com.example;

import org.springframework.stereotype.Repository;

/**
 * A catalogue whose public lookup can be called through a Spring proxy.
 */
@Repository
public class Catalogue {

    /**
     * Reads a normalized catalogue key.
     *
     * @param key the requested key
     * @return the normalized key
     */
    public String lookup(String key) {
        return key.strip();
    }
}
JAVA
    cat > "$1/src/test/java/com/example/RatioTest.java" <<'JAVA'
package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RatioTest {

    @Test
    void halvesTheValue() {
        assertEquals(0.5f, new Example().value() / 2.0F);
    }
}
JAVA
}

run_jspecify_annotation_locations() {
    new_consumer jspecify-locations
    jspecify_consumer="${consumer_directory}"
    cat > "${jspecify_consumer}/src/main/java/com/example/Nullness.java" <<'JAVA'
package com.example;

import org.jspecify.annotations.Nullable;

public final class Nullness {

    private Nullness() {
    }

    public static @Nullable int value() {
        return 1;
    }

    public static String @Nullable [] values() {
        return new String[] {"value"};
    }
}
JAVA
    run_maven jspecify_invalid analysis "${jspecify_consumer}" clean compiler:compile
    expect_exit jspecify_invalid 'nullness: an annotation on a primitive fails compilation' 1
    expect_match jspecify_invalid 'nullness: the new checker names the meaningless annotation' \
        'JSpecifyUnrecognizedAnnotationLocation.*|nullness annotation on a primitive type'
    expect_match jspecify_invalid 'nullness: the compiler locates the invalid return annotation' \
        'Nullness[.]java:.*10'

    run_maven jspecify_report analysis "${jspecify_consumer}" clean compiler:compile -Dairness.enforce=false
    expect_exit jspecify_report 'nullness: report-only compilation preserves its established behavior' 0
    expect_match jspecify_report 'nullness: report-only still reports the new checker' \
        'JSpecifyUnrecognizedAnnotationLocation'

    perl -0pi -e 's/\@Nullable int/int/' "${jspecify_consumer}/src/main/java/com/example/Nullness.java"
    run_maven jspecify_repaired analysis "${jspecify_consumer}" clean compiler:compile
    expect_exit jspecify_repaired 'nullness: a valid array annotation and repaired primitive compile' 0
    expect_no_match jspecify_repaired 'nullness: the repaired source carries no annotation-location finding' \
        '\[JSpecifyUnrecognizedAnnotationLocation\]'
}

run_modernization_cases() {
    new_consumer modernization-compiler
    modernization_compiler="${consumer_directory}"
    cat > "${modernization_compiler}/src/main/java/com/example/Draw.java" <<'JAVA'
package com.example;

/** Carries the representative Airness compiler finding. */
final class Draw {

    private Draw() {
    }

    static double next() {
        return Math.random();
    }
}
JAVA
    run_maven modernization_compiler analysis "${modernization_compiler}" compile
    expect_exit modernization_compiler 'error prone: an Airness compiler rule fails the compile' 1
    expect_match modernization_compiler 'error prone: the finding names the Airness rule at its file' \
        'Draw[.]java:.*AirnessJdkApiHasAModernForm'

    run_maven modernization_compiler_report analysis "${modernization_compiler}" compile -Dairness.enforce=false
    expect_exit modernization_compiler_report 'report-only: an Airness compiler finding does not stop the compile' 0
    expect_match modernization_compiler_report 'report-only: the Airness compiler finding stays visible' \
        'Draw[.]java:.*AirnessJdkApiHasAModernForm'

    new_consumer build-practices
    build_practices="${consumer_directory}"
    sed 's|^    </properties>|    </properties>\
    <url>https://example.com</url>|' "${build_practices}/pom.xml" > "${build_practices}/pom.xml.next"
    mv "${build_practices}/pom.xml.next" "${build_practices}/pom.xml"
    run_maven build_practices analysis "${build_practices}" validate
    expect_exit build_practices 'build practices: a pom element out of order fails validation' 1
    expect_match build_practices 'build practices: the finding names both elements' \
        'url is written after properties'

    new_consumer build-practices-clean
    run_maven build_practices_clean analysis "${consumer_directory}" validate
    expect_exit build_practices_clean 'build practices: the template consumer passes validation' 0
}
