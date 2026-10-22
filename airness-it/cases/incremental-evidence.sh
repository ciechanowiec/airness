#!/usr/bin/env sh

# Both phases first cover the unchanged production class; the next invocation deliberately loses one branch.
run_incremental_evidence_cases() {
    mkdir -p "${plugins_consumer}/target/airness"
    printf 'stale.Application\nopen GET /stale\n' > "${plugins_consumer}/target/airness/spring-context.evidence"
    perl -0pi -e 's/assertEquals\(0,/assertEquals(1,/; s/value\(false\)/value(true)/;
        s/coversTheDisabledOutcome/coversTheEnabledOutcomeAgain/' \
        "${plugins_consumer}/src/test/java/com/example/DecisionIT.java"
    run_maven incremental_missing_branch maven "${plugins_consumer}" verify
    expect_exit incremental_missing_branch 'incremental: an old covered branch cannot satisfy the next invocation' 1
    expect_match incremental_missing_branch 'incremental: the uncovered production branch fails its floor' \
        'Rule violated for class com[.]example[.]Decision: branches covered ratio'
    if [ ! -e "${plugins_consumer}/target/airness/spring-context.evidence" ]; then
        pass 'incremental: old Spring startup and security evidence is cleared before tests'
    else
        fail 'incremental: old Spring startup and security evidence is cleared before tests'
    fi
    cp "${plugins_fixture}/DecisionIT.java" "${plugins_consumer}/src/test/java/com/example/DecisionIT.java"
    run_maven incremental_combined maven "${plugins_consumer}" verify
    expect_exit incremental_combined 'incremental: both test phases still combine within the new invocation' 0
    expect_before incremental_combined 'incremental: evidence is prepared before Surefire' \
        'airness:[^ ]+:prepare-test-evidence ' 'surefire:[^ ]+:test '
    if python3 "${repository}/airness-it/managed-plugins-check.py" "${plugins_consumer}"; then
        pass 'incremental: restored unit and integration coverage contains both branches'
    else
        fail 'incremental: restored unit and integration coverage contains both branches'
    fi
}
