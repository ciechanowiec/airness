#!/usr/bin/env sh

# Real pages exercise the upstream sweep through the installed Maven/Node boundary. Each fixture is built
# once per state. The pure rule variants live in Vitest; this proves discovery, measurement and wiring.
prepare_browser_fixture() {
    browser_fixture="${scratch}/$1"
    mkdir -p "${browser_fixture}"
    cp -R "${repository}/airness-it/fixtures/node-next/." "${browser_fixture}/"
    install_airness_node "${browser_fixture}"
    cat > "${browser_fixture}/pom.xml" <<POM
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>eu.ciechanowiec</groupId><artifactId>airness-parent-node</artifactId>
        <version>${harness_version}</version>
    </parent>
    <groupId>com.example</groupId><artifactId>browser-contract</artifactId><version>1.0.0</version>
    <packaging>pom</packaging>
    <properties><airness.package.root>com.example</airness.package.root></properties>
</project>
POM
    git -C "${browser_fixture}" init --quiet --initial-branch=main
    git -C "${browser_fixture}" config user.name Fixture
    git -C "${browser_fixture}" config user.email fixture@example.invalid
    prepare_maven "$1-assets" node "${browser_fixture}" --quiet airness:assets-sync
    browser_generate create-a11y-page "${browser_fixture}" valid
}

browser_generate() {
    generator="$1"
    shift
    (cd "${repository}/airness-node" && "${node_home}/bin/node" "scripts/browser/${generator}.ts" "$@")
}

commit_browser_fixture() {
    git -C "${browser_fixture}" add --all
    git -C "${browser_fixture}" commit --quiet \
        --message "test(browser): record the $1 scenario" \
        --message 'The real application proves browser discovery, measurement and the installed harness boundary.'
}

expect_browser_line() {
    if grep -Fq -- "$2" "${execution_logs}/$1.log"; then
        pass "browser sweep reports $2"
    else
        fail "browser sweep reports $2" "missing from ${execution_logs}/$1.log"
    fi
}

run_node_browser_cases() {
    run_browser_evidence_cases
    prepare_browser_fixture browser-visual
    # A scaffold is created only by initialization, remains editable, and stays deleted after normal sync.
    prepare_maven browser_init node "${browser_fixture}" --quiet airness:assets-init
    expect_file_match "${browser_fixture}/.github/workflows/verify-full.yml" \
        'browser workflow: explicit initialization creates the full-verification scaffold' 'Pextended'
    rm "${browser_fixture}/.github/workflows/verify-full.yml"
    prepare_maven browser_resync node "${browser_fixture}" --quiet airness:assets-sync
    if [ ! -e "${browser_fixture}/.github/workflows/verify-full.yml" ]; then
        pass 'browser workflow: normal synchronization preserves a deleted scaffold'
    else
        fail 'browser workflow: normal synchronization preserves a deleted scaffold' 'the scaffold was recreated'
    fi
    run_maven browser_delta_alone node "${browser_fixture}" -Pdelta -DskipTests validate
    expect_exit browser_delta_alone 'browser delta: the profile requires Extended even in Skip mode' 1
    expect_match browser_delta_alone 'browser delta: the refusal gives the complete command' 'use mvn verify -Pextended,delta'
    run_maven browser_delta_release node "${browser_fixture}" -Pextended,delta,release -DskipTests validate
    expect_exit browser_delta_release 'browser delta: release validation refuses scoped verification' 1
    expect_match browser_delta_release 'browser delta: release requires the complete sweep' 'remove the delta profile'
    browser_generate create-visual-pages "${browser_fixture}" valid
    commit_browser_fixture 'repaired browser pages'
    run_maven browser_valid node "${browser_fixture}" -Pextended,delta \
        airness:node-gate@node-gate-build airness:node-gate@node-gate-e2e
    expect_exit browser_valid 'browser sweep: repaired real pages pass the Chromium sweep' 0
    expect_browser_line browser_valid '[airness-sweep]'
    browser_generate create-visual-pages "${browser_fixture}" defects
    commit_browser_fixture 'planted browser defects'
    run_maven browser_defects node "${browser_fixture}" -Pextended,delta \
        airness:node-gate@node-gate-build airness:node-gate@node-gate-e2e
    expect_exit browser_defects 'browser sweep: real defects fail the gate' 1
    while IFS= read -r finding; do
        expect_browser_line browser_defects "${finding}"
    done < "${repository}/airness-it/browser-findings.txt"

    prepare_browser_fixture browser-layout
    browser_generate create-layout-pages "${browser_fixture}" repaired
    commit_browser_fixture 'repaired layout pages'
    run_maven browser_layout_valid node "${browser_fixture}" -Pextended,delta \
        airness:node-gate@node-gate-build airness:node-gate@node-gate-e2e
    expect_exit browser_layout_valid 'browser layout: real repaired pages and a scanned dynamic route pass' 0
    browser_generate create-layout-pages "${browser_fixture}" defects
    commit_browser_fixture 'planted layout defects'
    run_maven browser_layout_defects node "${browser_fixture}" -Pextended,delta \
        airness:node-gate@node-gate-build airness:node-gate@node-gate-e2e
    expect_exit browser_layout_defects 'browser layout: real layout defects fail the gate' 1
    for finding in '[attached] /verify' '[text-overlap] /text-overlap' '[overflow] /overflow' \
        '[clipped-text] /clipped' '[clipped-text] /truncated' '[exemption] /unexplained' '[exemption] /stale'; do
        expect_browser_line browser_layout_defects "${finding}"
    done
    for viewport in 390x844 1280x800; do
        expect_browser_line browser_layout_defects "/open-disclosure-overlap @${viewport} (chromium, resting): overlapping text:"
        expect_browser_line browser_layout_defects "/closed-summary-overlap @${viewport} (chromium, resting): overlapping text:"
        expect_browser_line browser_layout_defects "/closed-disclosure-box @${viewport} (chromium, resting): touching boxes"
    done

    prepare_browser_fixture browser-clock
    browser_generate create-clock-case "${browser_fixture}"
    commit_browser_fixture 'fixed verification clocks'
    EXPECTED_YEAR=2100
    export EXPECTED_YEAR
    run_maven browser_clock node "${browser_fixture}" -Pextended,delta \
        airness:node-gate@node-gate-build airness:node-gate@node-gate-e2e
    expect_exit browser_clock 'browser clock: build, server, browser and test worker share the fixed year' 0
    python3 - "${browser_fixture}/package.json" <<'PY'
import json
import sys
from pathlib import Path
file = Path(sys.argv[1])
manifest = json.loads(file.read_text())
manifest['airness']['clock'] = 'real'
file.write_text(json.dumps(manifest, indent=2) + '\n')
PY
    EXPECTED_YEAR="$(date -u +%Y)"
    export EXPECTED_YEAR
    commit_browser_fixture 'real verification clocks'
    run_maven browser_real_clock node "${browser_fixture}" -Pextended,delta \
        airness:node-gate@node-gate-build airness:node-gate@node-gate-e2e
    expect_exit browser_real_clock 'browser clock: an explicit real clock reaches every process' 0
    unset EXPECTED_YEAR

    prepare_browser_fixture browser-scope
    browser_generate create-scope-case "${browser_fixture}"
    commit_browser_fixture 'scope baseline'
    git -C "${browser_fixture}" remote add origin .
    git -C "${browser_fixture}" update-ref refs/remotes/origin/main HEAD
    printf '\n/* change the first route only */\n' >> "${browser_fixture}/src/app/alpha/page.tsx"
    run_maven browser_delta node "${browser_fixture}" -Pextended,delta \
        airness:node-gate@node-gate-build airness:node-gate@node-gate-e2e
    expect_exit browser_delta 'browser delta: a scoped sweep passes through the installed harness' 0
    expect_browser_line browser_delta 'delta sweep:'
    if python3 - "${browser_fixture}/target/airness/node/module/e2e.json" <<'PY_SCOPE'
import json
import sys
from pathlib import Path
result = Path(sys.argv[1])
record = json.loads(result.read_text())
scopes = [json.loads((result.parent / name).read_text()) for name in record['evidence']]
assert len(scopes) == 1
scope = scopes[0]
assert scope['isComplete'] is False
assert [route['target'] for route in scope['routes']] == ['/alpha']
assert scope['stories'] == []
assert scope['baseline'] and scope['branch'] == 'origin/main'
PY_SCOPE
    then
        pass 'browser delta: evidence selects only the changed alpha route and records its baseline'
    else
        fail 'browser delta: evidence selects only the changed alpha route and records its baseline' 'unexpected scope evidence'
    fi

    for contract in engine-webgl settle-fonts server-lifecycle; do
        if (cd "${repository}/airness-node" && "${node_home}/bin/node" "scripts/browser/${contract}.ts") > "${scratch}/browser-${contract}.log" 2>&1; then
            pass "browser engines: ${contract} passes in every pinned engine"
        else
            fail "browser engines: ${contract} passes in every pinned engine" "see browser-${contract}.log"
        fi
    done
}
