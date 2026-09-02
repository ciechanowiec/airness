#!/usr/bin/env sh

run_analysis_cases() {
    new_consumer analysis-clean
    analysis_clean="$consumer_directory"
    run_maven analysis_clean analysis "$analysis_clean" checkstyle:check pmd:check pmd:cpd-check
    expect_exit analysis_clean 'analysis: clean packaged Checkstyle PMD and CPD configurations pass together' 0
    expect_match analysis_clean 'analysis: the Checkstyle goal actually executed' \
        'checkstyle:[^:]+:check'
    expect_match analysis_clean 'analysis: the PMD goal actually executed' \
        'pmd:[^:]+:check'
    expect_match analysis_clean 'analysis: the CPD goal actually executed' \
        'pmd:[^:]+:cpd-check'

    new_consumer analysis-findings
    analysis_findings="$consumer_directory"
    cat > "$analysis_findings/src/main/java/com/example/InferredLocal.java" <<'JAVA'
package com.example;

/** Carries the representative packaged-Checkstyle finding. */
final class InferredLocal {

    String value() {
        var value = "inferred";
        return value;
    }
}
JAVA
    cat > "$analysis_findings/src/main/java/com/example/BlankJustification.java" <<'JAVA'
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
    cat > "$analysis_findings/src/main/java/com/example/FirstScorer.java" <<'JAVA'
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
        "$analysis_findings/src/main/java/com/example/FirstScorer.java" \
        > "$analysis_findings/src/main/java/com/example/SecondScorer.java"

    run_maven analysis_report_only analysis "$analysis_findings" \
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

    run_maven checkstyle_enforcement analysis "$analysis_findings" \
        checkstyle:check '-Dcheckstyle.includes=**/InferredLocal.java'
    expect_exit checkstyle_enforcement 'checkstyle: a representative packaged finding fails enforcement' 1
    expect_match checkstyle_enforcement 'checkstyle: enforcement names the exact fixture' \
        'InferredLocal[.]java:.*Write the type'

    run_maven pmd_enforcement analysis "$analysis_findings" pmd:check
    expect_exit pmd_enforcement 'pmd: a representative packaged finding fails enforcement' 1
    expect_match pmd_enforcement 'pmd: enforcement names its rule ID' 'JustificationNeedsText'

    rm "$analysis_findings/src/main/java/com/example/InferredLocal.java" \
        "$analysis_findings/src/main/java/com/example/BlankJustification.java"
    run_maven cpd_enforcement analysis "$analysis_findings" pmd:cpd-check
    expect_exit cpd_enforcement 'cpd: packaged duplication wiring fails enforcement' 1
    expect_match cpd_enforcement 'cpd: enforcement reports the duplicated pair' \
        'has found [0-9]+ duplication'
}
