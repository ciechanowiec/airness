#!/usr/bin/env sh

# Shared physical execution, logical assertion, fixture, timing, and cleanup support.

enter() {
    case "$1" in
        *': '*)
            heading="${1%%: *}"
            detail="${1#*: }"
            ;;
        *)
            heading='suite'
            detail="$1"
            ;;
    esac
    if [ "$heading" != "$result_domain" ]; then
        result_domain="$heading"
        printf '\n  %s%s%s\n' "$style_bold" "$heading" "$style_off"
    fi
}

pass() {
    enter "$1"
    passed=$((passed + 1))
    printf '    %sPASS%s  %s\n' "$style_pass" "$style_off" "$detail"
}

fail() {
    enter "$1"
    failures=$((failures + 1))
    failed_cases="$failed_cases$1
"
    printf '    %sFAIL%s  %s\n' "$style_fail" "$style_off" "$detail"
    if [ -n "${2-}" ]; then
        printf '          %s%s%s\n' "$style_dim" "$2" "$style_off"
    fi
}

elapsed() {
    span="$1"
    if [ "$span" -ge 3600 ]; then
        printf '%dh %02dm %02ds' "$((span / 3600))" "$(((span % 3600) / 60))" "$((span % 60))"
    elif [ "$span" -ge 60 ]; then
        printf '%dm %02ds' "$((span / 60))" "$((span % 60))"
    else
        printf '%ds' "$span"
    fi
}

execution_log() {
    printf '%s/%s.log\n' "$execution_logs" "$1"
}

execution_status() {
    sed -n '1p' "$execution_logs/$1.status"
}

record_timing() {
    operation_seconds="$1"
    operation_domain="$2"
    operation_id="$3"
    operation_status="$4"
    operation_type="$5"
    shift 5
    {
        printf '%s\t%s\t%s\t%s\t%s' \
            "$operation_seconds" "$operation_domain" "$operation_id" "$operation_status" "$operation_type"
        for operation_argument in "$@"; do
            printf '\t%s' "$operation_argument"
        done
        printf '\n'
    } >> "$timings"
}

run_maven() {
    execution_id="$1"
    execution_domain="$2"
    execution_directory="$3"
    shift 3
    execution_output="$(execution_log "$execution_id")"
    execution_started="$(date +%s)"
    set +e
    (cd "$execution_directory" && mvn --batch-mode --no-transfer-progress "$@") > "$execution_output" 2>&1
    execution_result=$?
    set -e
    execution_seconds="$(($(date +%s) - execution_started))"
    printf '%s\n' "$execution_result" > "$execution_logs/$execution_id.status"
    maven_processes=$((maven_processes + 1))
    physical_executions=$((physical_executions + 1))
    record_timing "$execution_seconds" "$execution_domain" "$execution_id" "$execution_result" maven "$@"
}

prepare_maven() {
    preparation_id="$1"
    preparation_domain="$2"
    preparation_directory="$3"
    shift 3
    run_maven "$preparation_id" "$preparation_domain" "$preparation_directory" "$@"
    preparation_status="$(execution_status "$preparation_id")"
    if [ "$preparation_status" -ne 0 ]; then
        printf '\n  %ssetup %s failed in %s%s\n' \
            "$style_fail" "$preparation_id" "$preparation_directory" "$style_off" >&2
        sed -n '1,220p' "$(execution_log "$preparation_id")" >&2
        exit 1
    fi
}

expect_exit() {
    assertion_execution="$1"
    assertion_label="$2"
    assertion_expected="$3"
    assertion_status="$(execution_status "$assertion_execution")"
    if [ "$assertion_status" -eq "$assertion_expected" ]; then
        pass "$assertion_label"
    else
        fail "$assertion_label" "execution $assertion_execution exited $assertion_status, expected $assertion_expected"
        sed -n '1,220p' "$(execution_log "$assertion_execution")" >&2
    fi
}

expect_match() {
    assertion_execution="$1"
    assertion_label="$2"
    assertion_pattern="$3"
    if grep -Eq "$assertion_pattern" "$(execution_log "$assertion_execution")"; then
        pass "$assertion_label"
    else
        fail "$assertion_label" "execution $assertion_execution did not match /$assertion_pattern/"
        sed -n '1,220p' "$(execution_log "$assertion_execution")" >&2
    fi
}

expect_no_match() {
    assertion_execution="$1"
    assertion_label="$2"
    assertion_pattern="$3"
    if grep -Eq "$assertion_pattern" "$(execution_log "$assertion_execution")"; then
        fail "$assertion_label" "execution $assertion_execution unexpectedly matched /$assertion_pattern/"
        sed -n '1,220p' "$(execution_log "$assertion_execution")" >&2
    else
        pass "$assertion_label"
    fi
}

expect_count() {
    assertion_execution="$1"
    assertion_label="$2"
    assertion_pattern="$3"
    assertion_expected="$4"
    assertion_actual="$(grep -Ec "$assertion_pattern" "$(execution_log "$assertion_execution")" || true)"
    if [ "$assertion_actual" -eq "$assertion_expected" ]; then
        pass "$assertion_label"
    else
        fail "$assertion_label" \
            "execution $assertion_execution matched /$assertion_pattern/ $assertion_actual time(s), expected $assertion_expected"
        sed -n '1,220p' "$(execution_log "$assertion_execution")" >&2
    fi
}

expect_file_match() {
    assertion_file="$1"
    assertion_label="$2"
    assertion_pattern="$3"
    if [ -f "$assertion_file" ] && grep -Eq "$assertion_pattern" "$assertion_file"; then
        pass "$assertion_label"
    else
        fail "$assertion_label" "$assertion_file did not match /$assertion_pattern/"
    fi
}

expect_file_no_match() {
    assertion_file="$1"
    assertion_label="$2"
    assertion_pattern="$3"
    if [ ! -f "$assertion_file" ]; then
        fail "$assertion_label" "$assertion_file does not exist"
    elif grep -Eq "$assertion_pattern" "$assertion_file"; then
        fail "$assertion_label" "$assertion_file unexpectedly matched /$assertion_pattern/"
    else
        pass "$assertion_label"
    fi
}

clone_tree() {
    rm -rf "$2"
    if ! cp -c -R "$1" "$2" 2>/dev/null; then
        rm -rf "$2"
        cp -R "$1" "$2"
    fi
}

new_consumer() {
    consumer_name="$1"
    ensure_consumer_template
    consumer_directory="$scratch/$consumer_name"
    clone_tree "$consumer_template" "$consumer_directory"
}

ensure_consumer_template() {
    if [ -d "$consumer_template" ]; then
        return
    fi
    build_consumer "$consumer_template"
}

build_consumer() {
    fixture_directory="$1"
    mkdir -p "$fixture_directory/src/main/java/com/example" "$fixture_directory/src/test/java/com/example"
    cat > "$fixture_directory/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>eu.ciechanowiec</groupId>
        <artifactId>airness-parent</artifactId>
        <version>1.0.7-SNAPSHOT</version>
    </parent>
    <groupId>com.example</groupId>
    <artifactId>consumer</artifactId>
    <version>9.4.2</version>
    <properties>
        <airness.package.root>com.example</airness.package.root>
    </properties>
</project>
POM
    cat > "$fixture_directory/AGENTS.md" <<'INSTRUCTIONS'
# Consumer instructions

Run the Maven verification before committing a change.
INSTRUCTIONS
    cat > "$fixture_directory/src/main/java/com/example/package-info.java" <<'JAVA'
/**
 * Isolated consumer types used to exercise the inherited harness.
 */
@NullMarked
package com.example;

import org.jspecify.annotations.NullMarked;
JAVA
    cat > "$fixture_directory/src/main/java/com/example/Example.java" <<'JAVA'
package com.example;

/**
 * A small consumer fixture.
 */
public final class Example {

    /**
     * Supplies a stable value.
     *
     * @return the value
     */
    public int value() {
        return 1;
    }
}
JAVA
    cat > "$fixture_directory/src/test/java/com/example/ExampleTest.java" <<'JAVA'
package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExampleTest {

    @Test
    void readsTheProductionValue() {
        assertEquals(1, new Example().value());
    }
}
JAVA
    git -C "$fixture_directory" init --quiet
    git -C "$fixture_directory" config user.name Fixture
    git -C "$fixture_directory" config user.email fixture@example.invalid
    prepare_maven setup_assets setup "$fixture_directory" --quiet airness:assets-sync
    prepare_maven setup_format setup "$fixture_directory" --quiet process-resources -Pformat -Dairness.enforce=false
    rm -rf "$fixture_directory/target"
    git -C "$fixture_directory" add --all
    git -C "$fixture_directory" commit --quiet \
        --message 'test(it): create an isolated consumer fixture' \
        --message 'The fixture proves installed parent inheritance before boundary-specific copies change it.'
}

print_timing_summary() {
    printf '\n  %sTiming%s\n' "$style_bold" "$style_off"
    printf '    total elapsed: %s\n' "$(elapsed "$(($(date +%s) - started))")"
    printf '    Maven processes: %s\n' "$maven_processes"
    printf '    physical executions: %s\n' "$physical_executions"
    printf '    by domain:\n'
    awk -F '\t' '{seconds[$2] += $1; counts[$2]++} END {
        for (domain in seconds) printf "      %s: %d execution(s), %ds\n", domain, counts[domain], seconds[domain]
    }' "$timings" | sort
    printf '    slowest ten:\n'
    sort -t '	' -k1,1nr "$timings" | head -n 10 | awk -F '\t' '{
        printf "      %ss  %s  %s (%s)\n", $1, $2, $3, $5
    }'
    printf '    timings: %s\n' "$timings"
}

cleanup() {
    rm -rf "$scratch" 2>/dev/null || true
}
