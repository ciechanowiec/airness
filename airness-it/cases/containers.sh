#!/usr/bin/env sh

run_qodana_profile() {
    create_qodana_fixture
    run_maven qodana_profile containers "$qodana_consumer" airness:qodana
    qodana_log="$(execution_log qodana_profile)"
    expect_exit qodana_profile 'qodana: the intended negative control fails enforcement' 1
    qodana_sarif="$qodana_consumer/target/qodana/qodana.sarif.json"
    if [ ! -f "$qodana_sarif" ]; then
        fail 'qodana: the run left no report to read'
        sed -n '1,220p' "$qodana_log" >&2
    else
        for dropped in InnerClassOnInterface ClassWithTooManyDependencies; do
            reported="$(grep -c "\"ruleId\": \"$dropped\"" "$qodana_sarif" || true)"
            if [ "$reported" -eq 0 ]; then
                pass "qodana: $dropped reports nothing on the fixture"
            else
                fail "qodana: $dropped reported $reported finding(s)"
            fi
        done
        if grep -q "'CommandRouter' is overly coupled" "$qodana_sarif"; then
            pass 'qodana: ClassCoupling still reports an over-coupled class'
        else
            fail 'qodana: ClassCoupling stopped reporting the over-coupled class'
            sed -n '1,220p' "$qodana_log" >&2
        fi
        for spared in CommandRunner JdkHeavy; do
            if grep -q "'$spared' is overly coupled" "$qodana_sarif"; then
                fail "qodana: ClassCoupling reported $spared"
            else
                pass "qodana: ClassCoupling leaves $spared alone"
            fi
        done
        if grep -q "Class 'Tool' has only 'static' members" "$qodana_sarif"; then
            pass 'qodana: a static-only class with a main is still reported'
        else
            fail 'qodana: the utility-class exemption reached a project without the annotation'
        fi
        for named in bar then; do
            if grep -q "Questionable name '$named'" "$qodana_sarif"; then
                fail "qodana: QuestionableName refused the domain word $named"
            else
                pass "qodana: QuestionableName leaves the domain word $named alone"
            fi
        done
        if grep -q "Questionable name 'foo'" "$qodana_sarif"; then
            pass 'qodana: QuestionableName still reports a placeholder'
        else
            fail 'qodana: QuestionableName stopped reporting a placeholder'
        fi
    fi
}

run_container_cases() {
    if ! docker info >/dev/null 2>&1; then
        fail 'containers: Docker did not answer, so boundary evidence cannot run'
        return
    fi
    run_extended_consumer
    run_secret_scanner_control
    run_qodana_profile
    run_spring_qodana
}

run_extended_consumer() {
    new_consumer extended-profile
    extended_consumer="$consumer_directory"
    git -C "$extended_consumer" commit --quiet --allow-empty --message 'wip'
    run_maven extended_profile containers "$extended_consumer" \
        clean package -Pextended -Dairness.enforce=false
    expect_exit extended_profile 'extended: a consumer completes the installed Extended profile in report-only mode' 0
    expect_count extended_profile 'extended: every repository governance goal is reached' \
        'airness:[^ ]+:(commit-history|commit-typography|linear-history|scan-secrets) \(airness-governance-extended\)' 4
    expect_match extended_profile 'extended: the real secret scanner runs in the profile' \
        'commits scanned|no leaks found'
    expect_match extended_profile 'extended: the real Qodana inspection runs in the profile' \
        'Qodana - Detailed summary|Analysis results:'
    expect_match extended_profile 'extended: report-only keeps the history finding visible' \
        'Commit messages that break the policy'
}

run_secret_scanner_control() {
    new_consumer secret-history
    secret_consumer="$consumer_directory"
    {
        printf '%s%s%s\n' '-----BEGIN OPEN' 'SSH PRIVATE ' 'KEY-----'
        printf '%s\n' 'b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAAB'
        printf '%s%s%s\n' '-----END OPEN' 'SSH PRIVATE ' 'KEY-----'
    } > "$secret_consumer/committed-credential.txt"
    git -C "$secret_consumer" add committed-credential.txt
    git -C "$secret_consumer" commit --quiet \
        --message 'test(secrets): add a committed scanner control' \
        --message 'The credential-shaped fixture proves that the container reads complete Git history.'
    rm "$secret_consumer/committed-credential.txt"
    git -C "$secret_consumer" add --all
    git -C "$secret_consumer" commit --quiet \
        --message 'test(secrets): remove the scanner control from the tree'
    run_maven secret_history containers "$secret_consumer" \
        airness:scan-secrets -Dairness.enforce=false
    expect_exit secret_history 'secrets: history-wide findings remain reportable' 0
    expect_match secret_history 'secrets: the removed working-tree value is still found in history' \
        'leaks found: 1'
    expect_match secret_history 'secrets: the container reports scanning commits' \
        '[0-9]+ commits scanned'
}

run_spring_qodana() {
    if [ -z "${spring_app-}" ] || [ ! -d "$spring_app" ]; then
        run_spring_cases
    fi
    run_maven spring_qodana containers "$spring_app" airness:qodana
    expect_exit spring_qodana 'spring: the conforming Spring Boot application passes Qodana' 0
    spring_sarif="$spring_app/target/qodana/qodana.sarif.json"
    expect_file_no_match "$spring_sarif" \
        'spring: the package-private Boot entry point is not read as a utility class' \
        '"ruleId": "UtilityClassWithoutPrivateConstructor"'
}
