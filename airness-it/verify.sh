#!/usr/bin/env sh
# Verifies the boundaries that only installed Airness artifacts and real external processes can prove.
set -eu

repository="$(cd "$(dirname "$0")/.." && pwd)"
harness_version='1.0.7-SNAPSHOT'
declared="$(sed -n 's|^ *<version>\(.*\)</version> *$|\1|p' "${repository}/pom.xml" | head -n 1)"
if [ "${declared}" != "${harness_version}" ]; then
    printf '%s declares %s, and this suite is written for %s\n' \
        "${repository}/pom.xml" "${declared}" "${harness_version}" >&2
    exit 1
fi

selected_domain="${1-}"
case "${selected_domain}" in
    ''|maven|analysis|templates|repository|spring|containers|scanners)
        ;;
    *)
        printf 'Unknown Airness integration domain: %s\n' "${selected_domain}" >&2
        exit 2
        ;;
esac

# Lowered once here, before anything forks, because niceness is inherited: every lane and every Maven
# process under it runs at this priority. On a workstation the suite is a background chore that must not
# fight the editor or another build for the machine. It costs a dedicated runner nothing, since niceness
# only decides who yields when something else wants the processor. Containers are outside this: they are
# scheduled by the Docker daemon rather than by a child of this shell.
renice 19 -p "$$" > /dev/null 2>&1 || true

scratch="$(mktemp -d "${HOME}/.airness-it-XXXXXX")"
started="$(date +%s)"
failures=0
passed=0
failed_cases=''
maven_processes=0
physical_executions=0
result_domain=''
consumer_template="${scratch}/.consumer-template"
local_repository="${MAVEN_REPO_LOCAL:-${HOME}/.m2/repository}"
lane_results="${scratch}/.lanes"
exclusive_lock="${scratch}/.extended.lock"
mkdir -p "${lane_results}"

default_logs="${repository}/logs/airness-it"
timings="${AIRNESS_IT_TIMINGS:-${default_logs}/timings.tsv}"
execution_logs="${default_logs}/executions"
mkdir -p "$(dirname "${timings}")" "${execution_logs}"
: > "${timings}"
find "${execution_logs}" -type f -delete

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

. "${repository}/airness-it/support.sh"
. "${repository}/airness-it/cases/maven.sh"
. "${repository}/airness-it/cases/analysis.sh"
. "${repository}/airness-it/cases/lifecycle.sh"
. "${repository}/airness-it/cases/templates.sh"
. "${repository}/airness-it/cases/repository.sh"
. "${repository}/airness-it/cases/build-inputs.sh"
. "${repository}/airness-it/cases/artifact-packaging.sh"
. "${repository}/airness-it/cases/spring.sh"
. "${repository}/airness-it/cases/repository-proxies.sh"
. "${repository}/airness-it/cases/request-maps.sh"
. "${repository}/airness-it/cases/streaming-timeouts.sh"
. "${repository}/airness-it/cases/template-messages.sh"
. "${repository}/airness-it/cases/qodana-fixture.sh"
. "${repository}/airness-it/cases/containers.sh"
. "${repository}/airness-it/cases/scanners.sh"

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

printf '\n  %sAirness consumer integration verification%s\n' "${style_bold}" "${style_off}"
printf '  %sharness %s, installed-artifact and process boundaries%s\n' \
    "${style_dim}" "${harness_version}" "${style_off}"
if [ -n "${selected_domain}" ]; then
    printf '  %spartial domain: %s; this run cannot claim the full integration verdict%s\n' \
        "${style_dim}" "${selected_domain}" "${style_off}"
else
    printf '  %stwo lanes: spring, containers and the short domains | streaming and repository%s\n' \
        "${style_dim}" "${style_off}"
    printf '  %seach lane is replayed whole when it is joined; follow one live under %s%s\n' \
        "${style_dim}" "${lane_results}" "${style_off}"
fi

case "${selected_domain}" in
    '')
        # The consumer template is built once here rather than inside a lane, because every lane clones
        # it. The partition is measured rather than chosen, and the one ordering it encodes is that the
        # container cases read the fixture the spring cases build, so those two share a lane.
        #
        # Two lanes rather than three, because a consumer build is not one processor: the compiler is
        # forked, and the analysis, the coverage agent and the test fork all want their own. Three of
        # them on a four-processor runner cost 2.07x on every execution, which is more than the third
        # lane returned. Two leave the runner subscribed rather than oversubscribed, and leave a
        # workstation running this a processor to answer its owner with.
        ensure_consumer_template
        start_lane spring run_spring_cases run_container_cases run_template_message_cases \
            run_maven_cases run_analysis_cases run_template_cases
        start_lane repository run_streaming_timeout_cases run_repository_cases run_scanner_cases
        # Joined in the order whose headings do not repeat across the seam.
        join_lane spring
        join_lane repository
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
        run_streaming_timeout_cases
        run_template_message_cases
        ;;
    containers)
        run_container_cases
        ;;
    scanners)
        run_scanner_cases
        ;;
    *)
        exit 2
        ;;
esac

print_timing_summary
total=$((passed + failures))
printf '\n  %s%s%s\n' "${style_dim}" \
    '------------------------------------------------------------' "${style_off}"
if [ "${failures}" -ne 0 ]; then
    printf '  %sFailed%s\n' "${style_bold}" "${style_off}"
    printf '%s' "${failed_cases}" | while IFS= read -r case_label; do
        if [ -n "${case_label}" ]; then
            printf '    %s\n' "${case_label}"
        fi
    done
    printf '\n  %s%s of %s assertions failed%s, %s\n' \
        "${style_fail}" "${failures}" "${total}" "${style_off}" \
        "${duration_label}"
    exit 1
fi
if [ -n "${selected_domain}" ]; then
    printf '  %spartial %s domain: all %s assertions passed%s, %s\n' \
        "${style_pass}" "${selected_domain}" "${total}" "${style_off}" \
        "${duration_label}"
else
    printf '  %sall %s boundary assertions passed%s, %s\n' \
        "${style_pass}" "${total}" "${style_off}" \
        "${duration_label}"
fi
