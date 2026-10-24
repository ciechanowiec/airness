#!/usr/bin/env sh

# The node gates airness-parent binds, run by node-gate in a pure TypeScript module whose parent is
# airness-parent-node. The module installs @airness/node by its version, as a consumer does, from a registry
# on a local port that serves the tarball this reactor packed, so its lockfile carries the version and the
# package's real dependency set. One clean build proves each gate is bound and passes a conforming module,
# and that npm-inventory writes its evidence. One report-only build with a defect planted for each gate
# proves each gate reaches its finding through node-gate, and an enforcing build of the same tree proves the
# findings fail it. The rule variants are unit-tested by the Vitest suite of airness-node.
run_node_gate_cases() {
    node_library="${scratch}/node-library"
    mkdir -p "${node_library}"
    cp -R "${repository}/airness-it/fixtures/node-library/." "${node_library}/"
    install_airness_node "${node_library}"
    cat > "${node_library}/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>eu.ciechanowiec</groupId>
        <artifactId>airness-parent-node</artifactId>
        <version>1.0.8-SNAPSHOT</version>
    </parent>
    <groupId>com.example</groupId>
    <artifactId>library</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
    <properties>
        <airness.package.root>com.example</airness.package.root>
    </properties>
</project>
POM
    git -C "${node_library}" init --quiet
    git -C "${node_library}" config user.name Fixture
    git -C "${node_library}" config user.email fixture@example.invalid
    prepare_maven node_library_assets node "${node_library}" --quiet airness:assets-sync
    git -C "${node_library}" add --all
    git -C "${node_library}" commit --quiet \
        --message 'test(it): create a pure TypeScript module fixture' \
        --message 'The fixture proves the node gates are bound in the installed node parent.'

    run_maven node_library node "${node_library}" clean verify
    expect_exit node_library 'node gates: a conforming pure TypeScript module passes' 0
    expect_file_match "${node_library}/eslint.config.mjs" \
        'node gates: the node project carries the managed ESLint configuration' "@airness/node/eslint"
    expect_file_match "${node_library}/vitest.config.mts" \
        'node gates: the node project carries the managed Vitest configuration' "@airness/node/vitest"
    expect_file_match "${node_library}/pnpm-lock.yaml" \
        'node gates: the lockfile resolves @airness/node by its version' "'@airness/node@1[.]0[.]8-SNAPSHOT':"
    for node_gate in ${node_gates}; do
        expect_match node_library "node gates: ${node_gate} runs through node-gate and passes" \
            "Node gate ${node_gate} in [.] passed"
        expect_file_match "${node_library}/target/airness/node/module/${node_gate}.json" \
            "node gates: ${node_gate} writes its result in the contract" "\"gate\": \"${node_gate}\""
    done
    expect_file_match "${node_library}/target/airness/node/module/npm-inventory.json" \
        'node gates: npm-inventory lists its evidence in the result' '"dependencies[.]json"'
    expect_file_match "${node_library}/target/airness/node/module/dependencies.json" \
        'node gates: npm-inventory records the installed harness with its license' '"name": "@airness/node"'

    # One defect per gate. None can mask another, since each gate reads every file and reports them all.
    printf "export const wrong: number = 'text'\n" > "${node_library}/src/wrong.ts"
    printf 'export const formatted: number = 1;\n' > "${node_library}/src/formatted.ts"
    printf 'export const wait = (): Promise<number> => new Promise((resolve) => resolve(1))\n' \
        > "${node_library}/src/wait.ts"
    printf '/** The limit. */\nexport const limit: number = 42 * 3\n' > "${node_library}/src/limit.ts"
    mkdir -p "${node_library}/src/deep"
    printf "export { total } from '../total.js'\n" > "${node_library}/src/deep/read.ts"
    printf 'export const legacy = 1\n' > "${node_library}/src/legacy.js"
    printf "import { second } from './cycle-second.js'\n\nexport const first = (): number => second()\n" \
        > "${node_library}/src/cycle-first.ts"
    printf "import { first } from './cycle-first.js'\n\nexport const second = (): number => first()\n" \
        > "${node_library}/src/cycle-second.ts"
    printf 'export const loose = (value: any): number => value.length\n' > "${node_library}/src/loose.ts"
    printf "import { expect, it } from 'vitest'\nimport { total } from '@/total.js'\n\n%s\n" \
        "it('adds wrongly', () => {
  expect(total(1, 1)).toBe(3)
})" > "${node_library}/tests/wrong.spec.ts"
    printf '%s\n' 'allowBuilds:' '  left-pad: true' '' 'minimumReleaseAge: 1440' '' \
        'minimumReleaseAgeStrict: false' '' 'minimumReleaseAgeExclude:' '  - "@airness/node"' \
        > "${node_library}/pnpm-workspace.yaml"
    python3 - "${node_library}/package.json" <<'PY'
import json
import sys

path = sys.argv[1]
with open(path, encoding='utf-8') as manifest:
    document = json.load(manifest)
document['devDependencies']['typescript'] = '6.0.2'
document['airness'] = {
    'generatedArtefacts': [{'pattern': 'src/generated/**', 'reason': 'the API client generator writes it'}]
}
with open(path, 'w', encoding='utf-8') as manifest:
    json.dump(document, manifest, indent=2)
    manifest.write('\n')
PY
    install_airness_node "${node_library}"

    run_maven node_library_report node "${node_library}" verify -Dairness.enforce=false
    expect_exit node_library_report 'node gates: report-only mode prints every planted finding' 0
    expect_match node_library_report 'node gates: wiring reports a library at a version the harness does not run' \
        'typescript resolves to 6[.]0[.]2, but the harness runs 6[.]0[.]3'
    expect_match node_library_report 'node gates: types reports the type error' \
        "src/wrong[.]ts.*Type 'string' is not assignable to type 'number'"
    expect_match node_library_report 'node gates: biome reports the formatting defect' 'src/formatted[.]ts format'
    expect_match node_library_report 'node gates: oxlint reports the native rule' 'no-promise-executor-return'
    expect_match node_library_report 'node gates: eslint reports the type-aware rule' 'no-magic-numbers'
    expect_match node_library_report 'node gates: source-rules reports the parent-relative import' \
        'src/deep/read[.]ts:1 \[no-deep-relative-imports\]'
    expect_match node_library_report 'node gates: config-refs reports the exclusion that reaches nothing' \
        'generatedArtefacts entry "src/generated/[*][*]" excludes nothing'
    expect_match node_library_report 'node gates: javascript-allowlist reports the hand-written JavaScript' \
        'src/legacy[.]js hand-written JavaScript is banned'
    expect_match node_library_report 'node gates: arch reports the cycle' 'no-circular.*src/cycle-'
    expect_match node_library_report 'node gates: type-coverage reports the explicit any' 'src/loose[.]ts'
    expect_match node_library_report 'node gates: knip reports the unused file' 'src/legacy[.]js'
    expect_match node_library_report 'node gates: tests reports the failing spec' 'tests/wrong[.]spec[.]ts'
    expect_match node_library_report 'node gates: install-scripts reports the decision about nothing' \
        'allowBuilds[.]left-pad names no installed dependency that runs an install script'
    expect_match node_library_report 'node gates: release-age reports the lenient floor' \
        'minimumReleaseAgeStrict: must be true'
    for node_gate in ${node_gates}; do
        if [ "${node_gate}" = npm-inventory ]; then
            continue
        fi
        expect_match node_library_report "node gates: ${node_gate} fails on its planted defect" \
            "Node gate ${node_gate} in [.] failed"
    done

    run_maven node_library_enforced node "${node_library}" verify
    expect_exit node_library_enforced 'node gates: a planted gate finding fails an enforcing build' 1
    expect_match node_library_enforced 'node gates: wiring, the first gate of verify, stops the enforcing build' \
        'Node gate wiring in [.] failed'
}

# Every gate airness-parent binds, in the order the result files are asserted.
node_gates='wiring types biome oxlint eslint source-rules config-refs javascript-allowlist arch type-coverage knip
tests install-scripts release-age npm-inventory'

# Starts, once per process, the registry that serves the @airness/node tarball this reactor packed and the
# fixtures' scripted packages. It stops on its own once the shell that started it is gone.
start_airness_node_registry() {
    if [ -n "${airness_node_registry-}" ]; then
        return
    fi
    registry_port_file="${scratch}/.airness-node-registry.port"
    set --
    for fixture_package in "${repository}"/airness-it/fixtures/node-packages/*; do
        fixture_staging="${scratch}/.node-packages/$(basename "${fixture_package}")"
        mkdir -p "${fixture_staging}/package"
        cp -R "${fixture_package}/." "${fixture_staging}/package/"
        tar -czf "${fixture_staging}.tgz" -C "${fixture_staging}" package
        set -- "$@" "${fixture_staging}.tgz"
    done
    python3 "${repository}/airness-it/npm-registry.py" "${registry_port_file}" \
        "${repository}/airness-node/target/airness-node-${harness_version}.tgz" "$@" \
        > "${scratch}/.airness-node-registry.log" 2>&1 &
    registry_waited=0
    while [ ! -s "${registry_port_file}" ]; do
        if [ "${registry_waited}" -ge 30 ]; then
            printf '\n  %sthe @airness/node registry did not start%s\n' "${style_fail}" "${style_off}" >&2
            cat "${scratch}/.airness-node-registry.log" >&2
            exit 1
        fi
        command sleep 1
        registry_waited=$((registry_waited + 1))
    done
    airness_node_registry="http://127.0.0.1:$(cat "${registry_port_file}")/"
}

# Installs a copied node project's dependencies with the harness's Node and pnpm, @airness/node from the
# local registry by the scoped line in the project's .npmrc and every other package from the public one,
# which writes the lockfile the build then installs from. No script runs, as in node-dependencies.
install_airness_node() {
    start_airness_node_registry
    printf '@airness:registry=%s\n' "${airness_node_registry}" > "$1/.npmrc"
    # The versions node-install pins, read from the plugin's own pin file, so the lockfile is written by the
    # pnpm that node-dependencies later installs it with, whatever other versions the cache still holds.
    node_pins="${repository}/airness-maven-plugin/src/main/resources/eu/ciechanowiec/airness/node/toolchain.properties"
    node_toolchain="${local_repository}/.airness/toolchain"
    node_home="$(find "${node_toolchain}" -maxdepth 1 -type d \
        -name "node-$(sed -n 's/^node[.]version=//p' "${node_pins}")-*" | head -n 1)"
    pnpm_home="$(find "${node_toolchain}" -maxdepth 1 -type d \
        -name "pnpm-$(sed -n 's/^pnpm[.]version=//p' "${node_pins}")-*" | head -n 1)"
    install_log="${scratch}/install-$(printf '%s' "$1" | cksum | cut -d ' ' -f 1).log"
    if ! (cd "$1" && PATH="${node_home}/bin:${pnpm_home}:${PATH}" npm_config_manage_package_manager_versions=false \
        "${pnpm_home}/pnpm" install --ignore-scripts) > "${install_log}" 2>&1; then
        printf '\n  %sinstalling the dependencies of %s failed%s\n' "${style_fail}" "$1" "${style_off}" >&2
        cat "${install_log}" >&2
        exit 1
    fi
}
