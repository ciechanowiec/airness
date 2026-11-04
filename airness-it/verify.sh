#!/usr/bin/env sh
# Verifies the boundaries that only installed Airness artifacts and real external processes can prove.
set -eu

repository="$(cd "$(dirname "$0")/.." && pwd)"
harness_version='1.0.10-SNAPSHOT'
declared="$(sed -n 's|^ *<version>\(.*\)</version> *$|\1|p' "${repository}/pom.xml" | head -n 1)"
if [ "${declared}" != "${harness_version}" ]; then
    printf '%s declares %s, and this suite is written for %s\n' \
        "${repository}/pom.xml" "${declared}" "${harness_version}" >&2
    exit 1
fi

# The groups the verification workflow runs side by side, each on a runner of its own. Together they are every
# domain the complete suite runs, and each holds about a fifth of its execution time.
case "$*" in
    group-1)
        set -- spring
        ;;
    group-2)
        set -- maven containers
        ;;
    group-3)
        set -- browser
        ;;
    group-4)
        set -- node payload
        ;;
    group-5)
        set -- repository scanners analysis templates stylesheets
        ;;
    *)
        ;;
esac

# No domain runs the complete suite; one or more run only those domains, in the order named.
selected_domains="$*"
for selected_domain in "$@"; do
    case "${selected_domain}" in
        maven|analysis|templates|stylesheets|repository|spring|containers|scanners|node|browser|payload)
            ;;
        browser-evidence)
            ;;
        *)
            printf 'Unknown Airness integration domain: %s\n' "${selected_domain}" >&2
            exit 2
            ;;
    esac
done

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
. "${repository}/airness-it/cases/model-policy.sh"
. "${repository}/airness-it/cases/license-metadata.sh"
. "${repository}/airness-it/cases/sbom.sh"
. "${repository}/airness-it/cases/repository-routing.sh"
. "${repository}/airness-it/cases/plugin-vulnerabilities.sh"
. "${repository}/airness-it/cases/compliance-report.sh"
. "${repository}/airness-it/cases/managed-plugins.sh"
. "${repository}/airness-it/cases/incremental-evidence.sh"
. "${repository}/airness-it/cases/analysis.sh"
. "${repository}/airness-it/cases/early-analysis.sh"
. "${repository}/airness-it/cases/lifecycle.sh"
. "${repository}/airness-it/cases/templates.sh"
. "${repository}/airness-it/cases/stylesheets.sh"
. "${repository}/airness-it/cases/repository.sh"
. "${repository}/airness-it/cases/managed-files.sh"
. "${repository}/airness-it/cases/build-inputs.sh"
. "${repository}/airness-it/cases/artifact-packaging.sh"
. "${repository}/airness-it/cases/spring.sh"
. "${repository}/airness-it/cases/repository-proxies.sh"
. "${repository}/airness-it/cases/request-maps.sh"
. "${repository}/airness-it/cases/streaming-timeouts.sh"
. "${repository}/airness-it/cases/template-messages.sh"
. "${repository}/airness-it/cases/web-accessibility.sh"
. "${repository}/airness-it/cases/qodana-fixture.sh"
. "${repository}/airness-it/cases/containers.sh"
. "${repository}/airness-it/cases/scanners.sh"
. "${repository}/airness-it/cases/generic-rules.sh"
. "${repository}/airness-it/cases/node-projects.sh"
. "${repository}/airness-it/cases/frontend-coverage.sh"
. "${repository}/airness-it/cases/node-gates.sh"
. "${repository}/airness-it/cases/node-browser.sh"
. "${repository}/airness-it/cases/browser-evidence.sh"
. "${repository}/airness-it/cases/java-web.sh"
. "${repository}/airness-it/cases/payload.sh"

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

printf '\n  %sAirness consumer integration verification%s\n' "${style_bold}" "${style_off}"
printf '  %sharness %s, installed-artifact and process boundaries%s\n' \
    "${style_dim}" "${harness_version}" "${style_off}"
if [ -n "${selected_domains}" ]; then
    printf '  %spartial domains: %s; this run cannot claim the full integration verdict%s\n' \
        "${style_dim}" "${selected_domains}" "${style_off}"
else
    printf '  %stwo lanes: spring, containers, maven, payload | repository, the short domains, node, browser%s\n' \
        "${style_dim}" "${style_off}"
    printf '  %seach lane is replayed whole when it is joined; follow one live under %s%s\n' \
        "${style_dim}" "${lane_results}" "${style_off}"
fi

# Every case belongs to exactly one domain, and the complete suite, a partial run and each shard of the CI
# workflow all reach the cases through this mapping only, so the domains together are the complete suite.
run_domain() {
    case "$1" in
        maven)
            run_maven_cases
            ;;
        analysis)
            run_analysis_cases
            ;;
        templates)
            run_template_cases
            ;;
        stylesheets)
            run_stylesheet_cases
            ;;
        repository)
            run_repository_cases
            ;;
        spring)
            run_spring_cases
            run_streaming_timeout_cases
            run_template_message_cases
            run_web_accessibility_cases
            ;;
        containers)
            run_container_cases
            ;;
        scanners)
            run_scanner_cases
            ;;
        browser)
            run_node_browser_cases
            run_java_web_cases
            ;;
        browser-evidence)
            run_browser_evidence_cases
            ;;
        node)
            run_node_project_cases
            run_frontend_coverage_cases
            run_node_gate_cases
            run_node_next_cases
            ;;
        payload)
            run_payload_cases
            ;;
        *)
            exit 2
            ;;
    esac
}

# Started here, in the shell that outlives every lane and domain, so that no lane's end stops the server another
# still installs from, and so that it serves the stack packages the payload cases install whichever domain
# installs first.
prepare_payload_packages
start_airness_node_registry "${payload_package}" "${payload_mismatched_package}"
if [ -z "${selected_domains}" ]; then
    # The consumer template is built once here rather than inside a lane, because every lane clones
    # it. The partition is measured rather than chosen: each lane carries about half of the summed
    # execution time, and the Extended executions that hold the exclusive lock are split so that
    # neither lane spends most of its time waiting for the other to release it. No case reads a
    # fixture another lane builds.
    #
    # Two lanes rather than three, because a consumer build is not one processor: the compiler is
    # forked, and the analysis, the coverage agent and the test fork all want their own. Three of
    # them on a four-processor runner cost 2.07x on every execution, which is more than the third
    # lane returned. Two leave the runner subscribed rather than oversubscribed, and leave a
    # workstation running this a processor to answer its owner with.
    ensure_consumer_template
    start_lane spring spring containers maven payload
    start_lane repository repository scanners analysis templates stylesheets node browser
    # Joined in the order whose headings do not repeat across the seam.
    await_lanes spring repository
    join_lane spring
    join_lane repository
else
    for selected_domain in "$@"; do
        run_domain "${selected_domain}"
    done
fi

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
if [ -n "${selected_domains}" ]; then
    printf '  %spartial %s: all %s assertions passed%s, %s\n' \
        "${style_pass}" "${selected_domains}" "${total}" "${style_off}" \
        "${duration_label}"
else
    printf '  %sall %s boundary assertions passed%s, %s\n' \
        "${style_pass}" "${total}" "${style_off}" \
        "${duration_label}"
fi
