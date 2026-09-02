#!/usr/bin/env sh
# Verifies the boundaries that only installed Airness artifacts and real external processes can prove.
set -eu

repository="$(cd "$(dirname "$0")/.." && pwd)"
harness_version='1.0.7-SNAPSHOT'
declared="$(sed -n 's|^ *<version>\(.*\)</version> *$|\1|p' "$repository/pom.xml" | head -n 1)"
if [ "$declared" != "$harness_version" ]; then
    printf '%s declares %s, and this suite is written for %s\n' \
        "$repository/pom.xml" "$declared" "$harness_version" >&2
    exit 1
fi

selected_domain="${1-}"
case "$selected_domain" in
    ''|maven|analysis|templates|repository|spring|containers)
        ;;
    *)
        printf 'Unknown Airness integration domain: %s\n' "$selected_domain" >&2
        exit 2
        ;;
esac

scratch="$(mktemp -d "$HOME/.airness-it-XXXXXX")"
started="$(date +%s)"
failures=0
passed=0
failed_cases=''
maven_processes=0
physical_executions=0
result_domain=''
consumer_template="$scratch/.consumer-template"
local_repository="${MAVEN_REPO_LOCAL:-$HOME/.m2/repository}"

default_logs="$repository/logs/airness-it"
timings="${AIRNESS_IT_TIMINGS:-$default_logs/timings.tsv}"
execution_logs="$default_logs/executions"
mkdir -p "$(dirname "$timings")" "$execution_logs"
: > "$timings"
find "$execution_logs" -type f -delete

if [ -t 1 ] && [ -z "${NO_COLOR-}" ]; then
    style_bold="$(printf '\033[1m')"
    style_dim="$(printf '\033[2m')"
    style_pass="$(printf '\033[32m')"
    style_fail="$(printf '\033[31m')"
    style_off="$(printf '\033[0m')"
else
    style_bold=''
    style_dim=''
    style_pass=''
    style_fail=''
    style_off=''
fi

. "$repository/airness-it/support.sh"
. "$repository/airness-it/cases/maven.sh"
. "$repository/airness-it/cases/analysis.sh"
. "$repository/airness-it/cases/templates.sh"
. "$repository/airness-it/cases/repository.sh"
. "$repository/airness-it/cases/spring.sh"
. "$repository/airness-it/cases/qodana-fixture.sh"
. "$repository/airness-it/cases/containers.sh"

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

printf '\n  %sAirness consumer integration verification%s\n' "$style_bold" "$style_off"
printf '  %sharness %s, installed-artifact and process boundaries%s\n' \
    "$style_dim" "$harness_version" "$style_off"
if [ -n "$selected_domain" ]; then
    printf '  %spartial domain: %s; this run cannot claim the full integration verdict%s\n' \
        "$style_dim" "$selected_domain" "$style_off"
fi

case "$selected_domain" in
    '')
        run_maven_cases
        run_analysis_cases
        run_template_cases
        run_repository_cases
        run_spring_cases
        run_container_cases
        ;;
    maven)
        run_maven_cases
        ;;
    analysis)
        run_analysis_cases
        ;;
    templates)
        run_template_cases
        ;;
    repository)
        run_repository_cases
        ;;
    spring)
        run_spring_cases
        ;;
    containers)
        run_container_cases
        ;;
esac

print_timing_summary
total=$((passed + failures))
printf '\n  %s%s%s\n' "$style_dim" \
    '------------------------------------------------------------' "$style_off"
if [ "$failures" -ne 0 ]; then
    printf '  %sFailed%s\n' "$style_bold" "$style_off"
    printf '%s' "$failed_cases" | while IFS= read -r case_label; do
        if [ -n "$case_label" ]; then
            printf '    %s\n' "$case_label"
        fi
    done
    printf '\n  %s%s of %s assertions failed%s, %s\n' \
        "$style_fail" "$failures" "$total" "$style_off" \
        "$(elapsed "$(($(date +%s) - started))")"
    exit 1
fi
if [ -n "$selected_domain" ]; then
    printf '  %spartial %s domain: all %s assertions passed%s, %s\n' \
        "$style_pass" "$selected_domain" "$total" "$style_off" \
        "$(elapsed "$(($(date +%s) - started))")"
else
    printf '  %sall %s boundary assertions passed%s, %s\n' \
        "$style_pass" "$total" "$style_off" \
        "$(elapsed "$(($(date +%s) - started))")"
fi
