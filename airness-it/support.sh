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
    if [ "${heading}" != "${result_domain}" ]; then
        result_domain="${heading}"
        printf '\n  %s%s%s\n' "${style_bold}" "${heading}" "${style_off}"
    fi
}

pass() {
    enter "$1"
    passed=$((passed + 1))
    printf '    %sPASS%s  %s\n' "${style_pass}" "${style_off}" "${detail}"
}

fail() {
    enter "$1"
    failures=$((failures + 1))
    failed_cases="${failed_cases}$1
"
    printf '    %sFAIL%s  %s\n' "${style_fail}" "${style_off}" "${detail}"
    if [ -n "${2-}" ]; then
        printf '          %s%s%s\n' "${style_dim}" "$2" "${style_off}"
    fi
}

elapsed() {
    span="$1"
    if [ "${span}" -ge 3600 ]; then
        printf '%dh %02dm %02ds' "$((span / 3600))" "$(((span % 3600) / 60))" "$((span % 60))"
    elif [ "${span}" -ge 60 ]; then
        printf '%dm %02ds' "$((span / 60))" "$((span % 60))"
    else
        printf '%ds' "${span}"
    fi
}

execution_log() {
    printf '%s/%s.log\n' "${execution_logs}" "$1"
}

# What a failed check prints of a log. Its start names the command and what was set up, and its end is where
# Maven names the goal that failed and why, which in a log of thousands of lines lies far beyond its first few
# hundred. A log short enough to read whole is printed whole.
excerpt_log() {
    [ -f "$1" ] || return 0
    excerpt_lines="$(awk 'END { print NR }' "$1")"
    if [ "${excerpt_lines}" -le 220 ]; then
        cat "$1"
    else
        sed -n '1,60p' "$1"
        printf '  [... %s lines left out ...]\n' "$((excerpt_lines - 220))"
        tail -n 160 "$1"
    fi
}

execution_status() {
    sed -n '1p' "${execution_logs}/$1.status"
}

execution_findings() {
    printf '%s/%s.findings\n' "${execution_logs}" "$1"
}

# Normalizes the analyzer reports an execution wrote into executions/<id>.findings: one '#report <tool>'
# line per tool that wrote a report, then one finding per line as tool, path:line, rule and message
# separated by tabs. Only reports strictly newer than the execution's start marker are read, here and in
# findings.py alike, so a report an earlier execution left in the same fixture is never attributed to this
# one. An execution that wrote no report has an empty table, and a report that cannot be read leaves no
# table at all. Dependency trees and Git metadata are pruned, so a fixture carrying node_modules does not
# make every execution walk it.
collect_findings() {
    collection_findings="$(execution_findings "$1")"
    rm -f "${collection_findings}"
    collection_reports="$(find "$2" \( -name node_modules -o -name .git \) -prune -o -path '*/target/*' \
        -newer "$3" \( -name checkstyle-result.xml -o -name pmd.xml -o -name cpd.xml \
        -o -name qodana.sarif.json \) -print 2>/dev/null | head -n 1)"
    if [ -z "${collection_reports}" ]; then
        : > "${collection_findings}"
        return
    fi
    if ! python3 "${repository}/airness-it/findings.py" "$2" "$3" "${collection_findings}" \
        > "${execution_logs}/$1.findings.log" 2>&1; then
        rm -f "${collection_findings}"
    fi
}

# Asserts one finding of the machine-readable findings table: the tool that reported it, where, and a
# pattern read against its rule and message. The location is a path suffix, optionally with :line, and
# matches at a path boundary, so 'A.java:12' matches 'src/main/java/x/A.java:12' and not 'XA.java:12'.
expect_finding() {
    finding_arguments "$@"
    finding_unreadable
    if [ "${finding_is_unreadable}" = yes ]; then
        return
    fi
    finding_matches "${finding_table}"
    if [ "${finding_did_match}" = yes ]; then
        pass "${finding_label}"
    else
        fail "${finding_label}" "execution ${finding_execution} has no ${finding_tool} finding at \
${finding_location} matching /${finding_pattern}/"
        sed -n '1,120p' "${finding_table}" >&2
    fi
}

# The negative counterpart, for a clean control beside planted defects. It requires a report from the
# named tool, so a tool that wrote nothing, or nothing readable, cannot pass a control by having said
# nothing.
expect_no_finding() {
    finding_arguments "$@"
    finding_unreadable
    if [ "${finding_is_unreadable}" = yes ]; then
        return
    fi
    finding_matches "${finding_table}"
    if [ "${finding_did_match}" = yes ]; then
        fail "${finding_label}" "execution ${finding_execution} unexpectedly has a ${finding_tool} finding at \
${finding_location} matching /${finding_pattern}/"
        sed -n '1,120p' "${finding_table}" >&2
    else
        pass "${finding_label}"
    fi
}

finding_arguments() {
    finding_execution="$1"
    finding_label="$2"
    finding_tool="$3"
    finding_location="$4"
    finding_pattern="$5"
    finding_table="$(execution_findings "${finding_execution}")"
}

# Fails the assertion when the table is missing or the named tool wrote no report into it.
finding_unreadable() {
    finding_is_unreadable=no
    if [ ! -f "${finding_table}" ]; then
        fail "${finding_label}" "execution ${finding_execution} left no readable findings table"
        sed -n '1,40p' "${execution_logs}/${finding_execution}.findings.log" >&2 2>/dev/null || true
        finding_is_unreadable=yes
        return 0
    fi
    if ! grep -qx "#report	${finding_tool}" "${finding_table}"; then
        fail "${finding_label}" "execution ${finding_execution} has no ${finding_tool} report to read"
        finding_is_unreadable=yes
        return 0
    fi
    return 0
}

# The values reach awk through the environment rather than -v, which would process backslash escapes
# and silently change a pattern such as '\['.
finding_matches() {
    finding_did_match=no
    if FINDING_TOOL="${finding_tool}" FINDING_LOCATION="${finding_location}" FINDING_PATTERN="${finding_pattern}" \
        awk -F '\t' '
        function at(where, wanted,    offset, before) {
            if (index(wanted, ":") == 0) {
                sub(/:[0-9]+$/, "", where)
            }
            if (length(where) < length(wanted)) {
                return 0
            }
            offset = length(where) - length(wanted)
            if (substr(where, offset + 1) != wanted) {
                return 0
            }
            before = substr(where, offset, 1)
            return offset == 0 || before == "/"
        }
        BEGIN {
            tool = ENVIRON["FINDING_TOOL"]
            location = ENVIRON["FINDING_LOCATION"]
            pattern = ENVIRON["FINDING_PATTERN"]
        }
        $1 == tool && at($2, location) && ($3 " " $4) ~ pattern { found = 1 }
        END { exit !found }
    ' "$1"; then
        finding_did_match=yes
    fi
}

record_timing() {
    operation_seconds="$1"
    operation_domain="$2"
    operation_id="$3"
    operation_status="$4"
    operation_type="$5"
    shift 5
    # Lanes share this file, so the record is assembled first and written by one printf. A command group
    # of several printf calls is several writes on one append descriptor, and two lanes interleave inside
    # a record. One short line is one atomic append.
    operation_separator="$(printf '\t')"
    operation_record="${operation_seconds}${operation_separator}${operation_domain}"
    operation_record="${operation_record}${operation_separator}${operation_id}"
    operation_record="${operation_record}${operation_separator}${operation_status}"
    operation_record="${operation_record}${operation_separator}${operation_type}"
    for operation_argument in "$@"; do
        operation_record="${operation_record}${operation_separator}${operation_argument}"
    done
    printf '%s\n' "${operation_record}" >> "${timings}"
}

# Serialises the executions that share the Dependency-Check database under
# ${user.home}/.cache/airness and the memory one Qodana container wants. Ownership is a plain variable
# rather than a file holding a process id, because $$ inside a lane subshell is the parent's identifier
# and every lane would claim the same one. The lock lives under the run's own scratch directory, so it
# does not serialise two concurrent invocations of this suite on one machine.
acquire_exclusive() {
    exclusive_waited=0
    while ! mkdir "${exclusive_lock}" 2>/dev/null; do
        if [ "${exclusive_waited}" -ge 1800 ]; then
            printf 'the exclusive lock was held for %ss, so it is being broken\n' "${exclusive_waited}" >&2
            rm -rf "${exclusive_lock}"
            continue
        fi
        command sleep 2
        exclusive_waited=$((exclusive_waited + 2))
    done
    exclusive_held=yes
}

release_exclusive() {
    exclusive_held=no
    rm -rf "${exclusive_lock}"
}

release_exclusive_on_exit() {
    if [ "${exclusive_held-no}" = yes ]; then
        rm -rf "${exclusive_lock}"
    fi
}

run_maven() {
    execution_id="$1"
    execution_domain="$2"
    execution_directory="$3"
    shift 3
    execution_output="$(execution_log "${execution_id}")"
    execution_exclusive=no
    case " $* " in
        *' -Pextended '*|*' airness:qodana '*)
            execution_exclusive=yes
            acquire_exclusive
            ;;
        *)
            ;;
    esac
    execution_marker="${execution_logs}/${execution_id}.started"
    : > "${execution_marker}"
    execution_started="$(date +%s)"
    set +e
    (cd "${execution_directory}" && mvn --batch-mode --no-transfer-progress "$@") > "${execution_output}" 2>&1
    execution_result=$?
    set -e
    execution_seconds="$(($(date +%s) - execution_started))"
    if [ "${execution_exclusive}" = yes ]; then
        release_exclusive
    fi
    collect_findings "${execution_id}" "${execution_directory}" "${execution_marker}"
    printf '%s\n' "${execution_result}" > "${execution_logs}/${execution_id}.status"
    maven_processes=$((maven_processes + 1))
    physical_executions=$((physical_executions + 1))
    record_timing "${execution_seconds}" "${execution_domain}" "${execution_id}" "${execution_result}" maven "$@"
}

prepare_maven() {
    preparation_id="$1"
    preparation_domain="$2"
    preparation_directory="$3"
    shift 3
    run_maven "${preparation_id}" "${preparation_domain}" "${preparation_directory}" "$@"
    preparation_status="$(execution_status "${preparation_id}")"
    preparation_log="$(execution_log "${preparation_id}")"
    if [ "${preparation_status}" -ne 0 ]; then
        printf '\n  %ssetup %s failed in %s%s\n' \
            "${style_fail}" "${preparation_id}" "${preparation_directory}" "${style_off}" >&2
        excerpt_log "${preparation_log}" >&2
        exit 1
    fi
}

expect_exit() {
    assertion_execution="$1"
    assertion_log="$(execution_log "${assertion_execution}")"
    assertion_label="$2"
    assertion_expected="$3"
    assertion_status="$(execution_status "${assertion_execution}")"
    if [ "${assertion_status}" -eq "${assertion_expected}" ]; then
        pass "${assertion_label}"
    else
        fail "${assertion_label}" "execution ${assertion_execution} exited ${assertion_status}, expected ${assertion_expected}"
        excerpt_log "${assertion_log}" >&2
    fi
}

expect_match() {
    assertion_execution="$1"
    assertion_log="$(execution_log "${assertion_execution}")"
    assertion_label="$2"
    assertion_pattern="$3"
    if grep -Eq "${assertion_pattern}" "${assertion_log}"; then
        pass "${assertion_label}"
    else
        fail "${assertion_label}" "execution ${assertion_execution} did not match /${assertion_pattern}/"
        excerpt_log "${assertion_log}" >&2
    fi
}

expect_no_match() {
    assertion_execution="$1"
    assertion_log="$(execution_log "${assertion_execution}")"
    assertion_label="$2"
    assertion_pattern="$3"
    if grep -Eq "${assertion_pattern}" "${assertion_log}"; then
        fail "${assertion_label}" "execution ${assertion_execution} unexpectedly matched /${assertion_pattern}/"
        excerpt_log "${assertion_log}" >&2
    else
        pass "${assertion_label}"
    fi
}

# Asserts a pattern matches exactly the given number of lines of an execution's log, for a step that must run
# once rather than at least once.
expect_count() {
    assertion_execution="$1"
    assertion_log="$(execution_log "${assertion_execution}")"
    assertion_label="$2"
    assertion_pattern="$3"
    assertion_expected="$4"
    assertion_actual="$(grep -Ec -e "${assertion_pattern}" "${assertion_log}" || true)"
    if [ "${assertion_actual}" -eq "${assertion_expected}" ]; then
        pass "${assertion_label}"
    else
        fail "${assertion_label}" \
            "execution ${assertion_execution} matched /${assertion_pattern}/ ${assertion_actual} times, not ${assertion_expected}"
    fi
}

expect_before() {
    assertion_execution="$1"
    assertion_log="$(execution_log "${assertion_execution}")"
    assertion_label="$2"
    if awk -v earlier_pattern="$3" -v later_pattern="$4" '
        $0 ~ earlier_pattern && !earlier { earlier = NR }
        $0 ~ later_pattern && !later { later = NR }
        END { exit !(earlier && later && earlier < later) }
    ' "${assertion_log}"; then
        pass "${assertion_label}"
    else
        fail "${assertion_label}" "execution ${assertion_execution} did not run /$3/ before /$4/"
    fi
}

expect_count() {
    assertion_execution="$1"
    assertion_log="$(execution_log "${assertion_execution}")"
    assertion_label="$2"
    assertion_pattern="$3"
    assertion_expected="$4"
    assertion_actual="$(grep -Ec "${assertion_pattern}" "${assertion_log}" || true)"
    if [ "${assertion_actual}" -eq "${assertion_expected}" ]; then
        pass "${assertion_label}"
    else
        fail "${assertion_label}" \
            "execution ${assertion_execution} matched /${assertion_pattern}/ ${assertion_actual} time(s), expected ${assertion_expected}"
        excerpt_log "${assertion_log}" >&2
    fi
}

expect_file_match() {
    assertion_file="$1"
    assertion_label="$2"
    assertion_pattern="$3"
    if [ -f "${assertion_file}" ] && grep -Eq "${assertion_pattern}" "${assertion_file}"; then
        pass "${assertion_label}"
    else
        fail "${assertion_label}" "${assertion_file} did not match /${assertion_pattern}/"
    fi
}

expect_file_no_match() {
    assertion_file="$1"
    assertion_label="$2"
    assertion_pattern="$3"
    if [ ! -f "${assertion_file}" ]; then
        fail "${assertion_label}" "${assertion_file} does not exist"
    elif grep -Eq "${assertion_pattern}" "${assertion_file}"; then
        fail "${assertion_label}" "${assertion_file} unexpectedly matched /${assertion_pattern}/"
    else
        pass "${assertion_label}"
    fi
}

# A scanner keeps its staged tree when it reports a finding or fails, and prints where. A case that
# provokes one on purpose has read what it needed by the time it calls this, so the tree that execution
# named is removed, and nothing else: each path comes from the execution's own log and must be a direct
# child of the scanner cache, which a parallel lane or an earlier run never shares with it.
discard_scanner_evidence() {
    discard_cache="${HOME}/.cache/airness/scan"
    discard_log="$(execution_log "$1")"
    discard_trees="$(sed -n 's/^.*Scanner evidence: //p' "${discard_log}")"
    while IFS= read -r discard_tree; do
        case "${discard_tree}" in
            '')
                ;;
            */../*|*/..|"${discard_cache}"/*/*)
                fail "scanners: execution $1 named a scanner tree outside the cache" "${discard_tree}"
                ;;
            "${discard_cache}"/?*)
                rm -rf "${discard_tree}"
                ;;
            *)
                fail "scanners: execution $1 named a scanner tree outside the cache" "${discard_tree}"
                ;;
        esac
    done <<DISCARD
${discard_trees}
DISCARD
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
    consumer_directory="${scratch}/${consumer_name}"
    clone_tree "${consumer_template}" "${consumer_directory}"
}

ensure_consumer_template() {
    if [ -d "${consumer_template}" ]; then
        return
    fi
    build_consumer "${consumer_template}"
}

build_consumer() {
    fixture_directory="$1"
    mkdir -p "${fixture_directory}/src/main/java/com/example" "${fixture_directory}/src/test/java/com/example"
    cat > "${fixture_directory}/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>eu.ciechanowiec</groupId>
        <artifactId>airness-parent-java</artifactId>
        <version>1.0.10-SNAPSHOT</version>
    </parent>
    <groupId>com.example</groupId>
    <artifactId>consumer</artifactId>
    <version>9.4.2</version>
    <properties>
        <airness.package.root>com.example</airness.package.root>
    </properties>
</project>
POM
    cat > "${fixture_directory}/AGENTS.md" <<'INSTRUCTIONS'
# Consumer instructions

Run the Maven verification before committing a change.
INSTRUCTIONS
    cat > "${fixture_directory}/src/main/java/com/example/package-info.java" <<'JAVA'
/**
 * Isolated consumer types used to exercise the inherited harness.
 */
@NullMarked
package com.example;

import org.jspecify.annotations.NullMarked;
JAVA
    cat > "${fixture_directory}/src/main/java/com/example/Example.java" <<'JAVA'
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
    cat > "${fixture_directory}/src/test/java/com/example/ExampleTest.java" <<'JAVA'
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
    git -C "${fixture_directory}" init --quiet
    git -C "${fixture_directory}" config user.name Fixture
    git -C "${fixture_directory}" config user.email fixture@example.invalid
    prepare_maven setup_assets setup "${fixture_directory}" --quiet airness:assets-sync
    prepare_maven setup_format setup "${fixture_directory}" --quiet process-resources -Pformat -Dairness.enforce=false
    rm -rf "${fixture_directory}/target"
    git -C "${fixture_directory}" add --all
    git -C "${fixture_directory}" commit --quiet \
        --message 'test(it): create an isolated consumer fixture' \
        --message 'The fixture proves installed parent inheritance before boundary-specific copies change it.'
}

print_timing_summary() {
    finished="$(date +%s)"
    duration_label="$(elapsed "$((finished - started))")"
    printf '\n  %sTiming%s\n' "${style_bold}" "${style_off}"
    printf '    total elapsed: %s\n' "${duration_label}"
    # Counted from the record every execution writes rather than from the shell counters, because a lane
    # increments those inside its own subshell and the parent never sees them. Every site that raises a
    # counter writes exactly one record, so the two numbers are the same ones as a sequential run prints.
    timing_processes="$(awk -F '\t' '$5 == "maven" { total++ } END { printf "%d", total }' "${timings}")"
    timing_executions="$(awk 'END { printf "%d", NR }' "${timings}")"
    printf '    Maven processes: %s\n' "${timing_processes}"
    printf '    physical executions: %s\n' "${timing_executions}"
    printf '    by domain:\n'
    awk -F '\t' '{seconds[$2] += $1; counts[$2]++} END {
        for (domain in seconds) printf "      %s: %d execution(s), %ds\n", domain, counts[domain], seconds[domain]
    }' "${timings}" | sort
    printf '    slowest ten:\n'
    sort -t '	' -k1,1nr "${timings}" | head -n 10 | awk -F '\t' '{
        printf "      %ss  %s  %s (%s)\n", $1, $2, $3, $5
    }'
    printf '    timings: %s\n' "${timings}"
}

# Runs the named domains in a subshell whose output is held back until the lane is joined, so
# that concurrent lanes cannot interleave a transcript. The counts are the last thing the lane writes:
# a lane that dies leaves none, and the parent reads that as a failure rather than as nothing to add.
start_lane() {
    lane_name="$1"
    shift
    # A lane inherits the tallies rather than resetting them, so that the parent keeps the only writable
    # copy and no assertion can be counted twice. That holds because every lane is started before any
    # lane is joined, which is the one thing worth refusing outright.
    if [ "${passed}" -ne 0 ] || [ "${failures}" -ne 0 ]; then
        printf 'every lane must be started before the first lane is joined\n' >&2
        exit 1
    fi
    (
        trap release_exclusive_on_exit EXIT
        for lane_domain in "$@"; do
            run_domain "${lane_domain}"
        done
        printf '%s %s\n' "${passed}" "${failures}" > "${lane_results}/${lane_name}.counts"
        printf '%s' "${failed_cases}" > "${lane_results}/${lane_name}.failed"
    ) > "${lane_results}/${lane_name}.out" 2>&1 &
    printf '%s\n' "$!" > "${lane_results}/${lane_name}.pid"
}

# Lists a process and every process it started, parents first.
process_tree() {
    printf '%s\n' "$1"
    for tree_child in $(pgrep -P "$1" 2>/dev/null); do
        process_tree "${tree_child}"
    done
}

# Waits until every named lane has ended, but stops the others as soon as one dies: a dead lane already
# denies the verdict, so letting the rest run on would only hold the failure back for the rest of their run.
# A lane has died when it has ended without writing its counts. Each sleep is a foreground command, so the
# shell reaps any lane that ended meanwhile, and kill -0 no longer finds it.
await_lanes() {
    while :; do
        lanes_running=no
        for lane_name in "$@"; do
            lane_pid="$(cat "${lane_results}/${lane_name}.pid")"
            if kill -0 "${lane_pid}" 2>/dev/null; then
                lanes_running=yes
            elif [ ! -f "${lane_results}/${lane_name}.counts" ]; then
                for lane_other in "$@"; do
                    lane_pid="$(cat "${lane_results}/${lane_other}.pid")"
                    if [ "${lane_other}" != "${lane_name}" ] && kill -0 "${lane_pid}" 2>/dev/null; then
                        printf '%s' "${lane_name}" > "${lane_results}/${lane_other}.stopped"
                        lane_tree="$(process_tree "${lane_pid}")"
                        printf '%s\n' "${lane_tree}" | xargs kill 2>/dev/null || true
                    fi
                done
                return
            fi
        done
        if [ "${lanes_running}" = no ]; then
            return
        fi
        command sleep 5
    done
}

join_lane() {
    lane_name="$1"
    lane_pid="$(cat "${lane_results}/${lane_name}.pid")"
    set +e
    wait "${lane_pid}"
    lane_status=$?
    set -e
    cat "${lane_results}/${lane_name}.out"
    if [ -f "${lane_results}/${lane_name}.stopped" ]; then
        lane_cause="$(cat "${lane_results}/${lane_name}.stopped")"
        fail "lanes: the ${lane_name} lane was stopped when the ${lane_cause} lane died" \
            'the assertions printed above are not counted'
        return
    fi
    if [ "${lane_status}" -ne 0 ] || [ ! -f "${lane_results}/${lane_name}.counts" ]; then
        fail "lanes: the ${lane_name} lane died before it reported a result" \
            "it exited ${lane_status}, so the assertions printed above are not counted"
        return
    fi
    read -r lane_passed lane_failures < "${lane_results}/${lane_name}.counts"
    passed=$((passed + lane_passed))
    failures=$((failures + lane_failures))
    if [ -s "${lane_results}/${lane_name}.failed" ]; then
        failed_cases="${failed_cases}$(cat "${lane_results}/${lane_name}.failed")
"
    fi
}

cleanup() {
    rm -rf "${scratch}" 2>/dev/null || true
}
