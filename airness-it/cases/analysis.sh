#!/usr/bin/env sh

run_analysis_cases() {
    new_consumer analysis-clean
    analysis_clean="${consumer_directory}"
    run_maven analysis_clean analysis "${analysis_clean}" checkstyle:check pmd:check pmd:cpd-check
    expect_exit analysis_clean 'analysis: clean packaged Checkstyle PMD and CPD configurations pass together' 0
    expect_match analysis_clean 'analysis: the Checkstyle goal actually executed' \
        'checkstyle:[^:]+:check'
    expect_match analysis_clean 'analysis: the PMD goal actually executed' \
        'pmd:[^:]+:check'
    expect_match analysis_clean 'analysis: the CPD goal actually executed' \
        'pmd:[^:]+:cpd-check'

    new_consumer analysis-findings
    analysis_findings="${consumer_directory}"
    cat > "${analysis_findings}/src/main/java/com/example/InferredLocal.java" <<'JAVA'
package com.example;

/** Carries the representative packaged-Checkstyle finding. */
final class InferredLocal {

    String value() {
        var value = "inferred";
        return value;
    }
}
JAVA
    cat > "${analysis_findings}/src/main/java/com/example/BlankJustification.java" <<'JAVA'
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
    cat > "${analysis_findings}/src/main/java/com/example/FirstScorer.java" <<'JAVA'
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
    sed 's/FirstScorer/SecondScorer/g' \
        "${analysis_findings}/src/main/java/com/example/FirstScorer.java" \
        > "${analysis_findings}/src/main/java/com/example/SecondScorer.java"

    run_maven analysis_report_only analysis "${analysis_findings}" \
        checkstyle:check pmd:check pmd:cpd-check -Dairness.enforce=false
    expect_exit analysis_report_only 'report-only: analyzer findings do not stop compatible goals' 0
    expect_match analysis_report_only 'checkstyle: the representative finding stays visible at its file' \
        'InferredLocal[.]java:.*Write the type'
    expect_no_match analysis_report_only 'checkstyle: the clean control stays free of the representative rule' \
        'Example[.]java:.*Write the type'
    expect_match analysis_report_only 'pmd: the representative rule ID stays visible at its file' \
        'BlankJustification:6 Rule:JustificationNeedsText'
    expect_match analysis_report_only 'cpd: the duplicated pair stays visible' \
        'has found [0-9]+ duplication'
    expect_match analysis_report_only 'analysis: report-only still executes CPD after PMD findings' \
        'pmd:[^:]+:cpd-check'

    run_maven checkstyle_enforcement analysis "${analysis_findings}" \
        checkstyle:check '-Dcheckstyle.includes=**/InferredLocal.java'
    expect_exit checkstyle_enforcement 'checkstyle: a representative packaged finding fails enforcement' 1
    expect_match checkstyle_enforcement 'checkstyle: enforcement names the exact fixture' \
        'InferredLocal[.]java:.*Write the type'

    run_maven pmd_enforcement analysis "${analysis_findings}" pmd:check
    expect_exit pmd_enforcement 'pmd: a representative packaged finding fails enforcement' 1
    expect_match pmd_enforcement 'pmd: enforcement names its rule ID' 'JustificationNeedsText'

    rm "${analysis_findings}/src/main/java/com/example/InferredLocal.java" \
        "${analysis_findings}/src/main/java/com/example/BlankJustification.java"
    run_maven cpd_enforcement analysis "${analysis_findings}" pmd:cpd-check
    expect_exit cpd_enforcement 'cpd: packaged duplication wiring fails enforcement' 1
    expect_match cpd_enforcement 'cpd: enforcement reports the duplicated pair' \
        'has found [0-9]+ duplication'
    run_variable_distance_cases
    run_detached_chain_dot_cases
    run_analysis_lifecycle
    run_early_analysis_lifecycle
    run_jspecify_annotation_locations

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

run_detached_chain_dot_cases() {
    new_consumer detached-chain-dot
    detached_chain_dot="${consumer_directory}"
    cat > "${detached_chain_dot}/src/main/java/com/example/JoinedValues.java" <<'JAVA'
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
    run_maven detached_chain_dot_refused analysis "${detached_chain_dot}" checkstyle:check
    expect_exit detached_chain_dot_refused 'analysis: a detached chained dot fails packaged Checkstyle' 1
    expect_match detached_chain_dot_refused 'analysis: the finding names the source and repair' \
        'JoinedValues[.]java:.*Join this dot.*AirnessDetachedChainDot'

    perl -0pi -e 's/\)\n        \.strip/).strip/' \
        "${detached_chain_dot}/src/main/java/com/example/JoinedValues.java"
    run_maven detached_chain_dot_joined analysis "${detached_chain_dot}" checkstyle:check
    expect_exit detached_chain_dot_joined 'analysis: joining the dot to the closing parenthesis passes' 0
}

run_variable_distance_cases() {
    new_consumer variable-distance
    variable_distance="${consumer_directory}"
    cat > "${variable_distance}/src/main/java/com/example/Reading.java" <<'JAVA'
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
    run_maven variable_distance_refused analysis "${variable_distance}" checkstyle:check
    expect_exit variable_distance_refused 'analysis: a distant local still fails the packaged distance check' 1
    expect_match variable_distance_refused 'analysis: the distance finding names the local and its rule' \
        'Reading[.]java:.*remembered.*VariableDeclarationUsageDistance'
    expect_no_match variable_distance_refused 'analysis: the distance diagnostic never recommends final' \
        'making that variable final'

    perl -0pi -e 's/String remembered =/final String remembered =/' \
        "${variable_distance}/src/main/java/com/example/Reading.java"
    run_maven variable_distance_final analysis "${variable_distance}" checkstyle:check
    expect_exit variable_distance_final 'analysis: final locals remain forbidden' 1
    expect_match variable_distance_final 'analysis: the final-local prohibition remains explicit' \
        'Local variables must not be declared final'
    expect_match variable_distance_final 'analysis: final no longer exempts a distant local from its check' \
        'Reading[.]java:.*remembered.*VariableDeclarationUsageDistance'
    expect_no_match variable_distance_final 'analysis: a final local receives no advice to add final' \
        'making that variable final'

    perl -0pi -e 's/        final String remembered = content.toString\(\);\n//; s/        return remembered;/        String remembered = content.toString();\n        return remembered;/' \
        "${variable_distance}/src/main/java/com/example/Reading.java"
    run_maven variable_distance_nearby analysis "${variable_distance}" checkstyle:check
    expect_exit variable_distance_nearby 'analysis: a local declared beside its use passes' 0
}
