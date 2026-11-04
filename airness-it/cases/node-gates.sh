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
        <version>1.0.10-SNAPSHOT</version>
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
    # mvn install attached the packed tarball to airness-node's artifact, where a consumer such as a stack layer
    # resolves it from the local repository rather than from a sibling checkout.
    if cmp -s "${local_repository}/eu/ciechanowiec/airness-node/${harness_version}/airness-node-${harness_version}.tgz" \
        "${repository}/airness-node/target/airness-node-${harness_version}.tgz"; then
        pass 'node gates: mvn install puts the packed @airness/node tarball in the local repository'
    else
        fail 'node gates: mvn install puts the packed @airness/node tarball in the local repository' \
            'eu/ciechanowiec/airness-node/<version>/airness-node-<version>.tgz is missing or differs from target'
    fi
    expect_file_match "${node_library}/eslint.config.mjs" \
        'node gates: the node project carries the managed ESLint configuration' "@airness/node/eslint"
    expect_file_match "${node_library}/vitest.config.mts" \
        'node gates: the node project carries the managed Vitest configuration' "@airness/node/vitest"
    lock_version="$(printf '%s' "${harness_version}" | sed 's/[.]/[.]/g')"
    expect_file_match "${node_library}/pnpm-lock.yaml" \
        'node gates: the lockfile resolves @airness/node by its version' "'@airness/node@${lock_version}':"
    if python3 -c "${locks_packed_integrity}" "${repository}/airness-node/target/airness-node-${harness_version}.tgz" \
        "${node_library}/pnpm-lock.yaml"; then
        pass 'node gates: the fixture installs the @airness/node this build packed, by its integrity'
    else
        fail 'node gates: the fixture installs the @airness/node this build packed, by its integrity' \
            'the lockfile integrity differs from airness-node/target, so a cached older build was installed'
    fi
    run_reproducible_pack_cases
    for node_gate in ${node_gates}; do
        expect_match node_library "node gates: ${node_gate} runs through node-gate and passes" \
            "Node gate ${node_gate} in [.] passed"
        expect_file_match "${node_library}/target/airness/node/module/${node_gate}.json" \
            "node gates: ${node_gate} writes its result in the contract" "\"gate\": \"${node_gate}\""
    done
    # The facets the library does not have read as not applicable, never as a pass.
    for node_gate in ${node_facet_gates}; do
        expect_match node_library "node gates: ${node_gate} runs through node-gate and has nothing to judge" \
            "Node gate ${node_gate} in [.] not applicable"
        expect_file_match "${node_library}/target/airness/node/module/${node_gate}.json" \
            "node gates: ${node_gate} writes not-applicable in the contract" '"status": "not-applicable"'
    done
    expect_file_match "${node_library}/target/airness/node/module/npm-inventory.json" \
        'node gates: npm-inventory lists its evidence in the result' '"dependencies[.]json"'
    expect_file_match "${node_library}/target/airness/node/module/dependencies.json" \
        'node gates: npm-inventory records the installed harness with its license' '"name": "@airness/node"'
    expect_match node_library 'npm evidence: the license allowlist and the freshness bound read the inventory' \
        'Version checking asked npm registries about [1-9][0-9]* package[(]s[)]'
    expect_match node_library 'npm evidence: the pinned Node and pnpm answer to the freshness bound' \
        'Version checking asked the Node release index and the npm registry about the toolchain'
    expect_match node_library 'npm evidence: closing weighs the node sources against the suppression ceiling' \
        'Node suppressions in force: 0 of 2 allowed'

    # One defect per gate. None can mask another, since each gate reads every file and reports them all.
    printf "export const wrong: number = 'text'\n" > "${node_library}/src/wrong.ts"
    printf 'export const formatted: number = 1;\n' > "${node_library}/src/formatted.ts"
    printf 'export const wait = (): Promise<number> => new Promise((resolve) => resolve(1))\n' \
        > "${node_library}/src/wait.ts"
    printf '/** The limit. */\nexport const limit: number = 42 * 3\n' > "${node_library}/src/limit.ts"
    printf '// eslint-disable-next-line no-console -- planted for the suppression ceiling\nconsole.info(1)\n' \
        > "${node_library}/src/noisy.ts"
    mkdir -p "${node_library}/src/deep"
    printf "export { total } from '../total.js'\n" > "${node_library}/src/deep/read.ts"
    printf 'export const legacy = 1\n' > "${node_library}/src/legacy.js"
    printf "import { second } from './cycle-second.js'\n\nexport const first = (): number => second()\n" \
        > "${node_library}/src/cycle-first.ts"
    printf "import { first } from './cycle-first.js'\n\nexport const second = (): number => first()\n" \
        > "${node_library}/src/cycle-second.ts"
    printf 'export const loose = (value: any): number => value.length\n' > "${node_library}/src/loose.ts"
    printf 'a {\n  colr: red;\n}\n' > "${node_library}/src/site.css"
    printf 'export const Badge = (): unknown => <span className="p-4 bg-[#0a7]" />\n' > "${node_library}/src/badge.tsx"
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
document['devDependencies']['tailwindcss'] = '4.3.3'
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
    expect_match node_library_report 'node gates: css reports the Stylelint finding' \
        'Unknown property "colr".*property-no-unknown'
    expect_match node_library_report 'node gates: tailwind-tokens reports the arbitrary value of a Tailwind project' \
        "src/badge[.]tsx:1:.* arbitrary Tailwind value \`-\[#0a7\]\`"
    expect_match node_library_report 'node gates: install-scripts reports the decision about nothing' \
        'allowBuilds[.]left-pad names no installed dependency that runs an install script'
    expect_match node_library_report 'node gates: release-age reports the lenient floor' \
        'minimumReleaseAgeStrict: must be true'
    expect_match node_library_report 'npm evidence: an inventory written past a failing wiring is named as such' \
        'npm evidence written past a failing wiring gate'
    expect_match node_library_report 'npm evidence: closing counts a node suppression against the ceiling' \
        'Node suppressions in force: 1 of 2 allowed'
    for node_gate in ${node_gates} ${node_facet_gates}; do
        if [ "${node_gate}" = npm-inventory ]; then
            continue
        fi
        expect_match node_library_report "node gates: ${node_gate} fails on its planted defect" \
            "Node gate ${node_gate} in [.] failed"
    done

    expect_match node_library_report 'node gates: a declared generated artefact is denied to agents through assets-sync' \
        'Edit[(]src/generated/[*][*][)]'
    # The rule assets-sync writes for it, so the enforcing build below stops at the first gate of verify.
    python3 - "${node_library}/.claude/settings.json" <<'PY'
import json
import sys

path = sys.argv[1]
with open(path, encoding='utf-8') as settings:
    document = json.load(settings)
document['permissions']['deny'].append('Edit(src/generated/**)')
with open(path, 'w', encoding='utf-8') as settings:
    json.dump(document, settings, indent=2)
    settings.write('\n')
PY

    run_maven node_library_enforced node "${node_library}" verify
    expect_exit node_library_enforced 'node gates: a planted gate finding fails an enforcing build' 1
    expect_match node_library_enforced 'node gates: wiring, the first gate of verify, stops the enforcing build' \
        'Node gate wiring in [.] failed'
}

# Exits 0 when the lockfile given second records, for @airness/node, the integrity of the tarball given first.
# The comparison is of the sha512 text exactly, as pnpm compares it, never of the content behind it.
locks_packed_integrity='
import base64
import hashlib
import re
import sys

packed = "sha512-" + base64.b64encode(hashlib.sha512(open(sys.argv[1], "rb").read()).digest()).decode()
lockfile = open(sys.argv[2], encoding="utf-8").read()
match = re.search(r"\x27@airness/node@[^\x27]+\x27:\s*\n\s*resolution: \{integrity: ([^,}\s]+)", lockfile)
sys.exit(0 if match and match.group(1) == packed else 1)
'

# The @airness/node tarball is reproducible, so reinstalling an unchanged harness leaves every consumer lock
# valid. The reactor packed it, rebuilt the package from an emptied dist, packed it again under target/repack
# and failed on any byte that differed. This proves both packs are the installed one and that no TypeScript
# build cache is packed, and that the files allowlist rather than an emptied dist keeps such by-products out.
# The registry helper then refuses a lock that records another integrity and names the refresh, starts once
# that refresh has run as printed, with the pinned pnpm, and starts against a lockfile that resolves no
# @airness/node at the served version or does not exist yet.
run_reproducible_pack_cases() {
    packed_tarball="${repository}/airness-node/target/airness-node-${harness_version}.tgz"
    installed_tarball="${local_repository}/eu/ciechanowiec/airness-node/${harness_version}"
    installed_tarball="${installed_tarball}/airness-node-${harness_version}.tgz"
    if cmp -s "${packed_tarball}" "${repository}/airness-node/target/repack/airness-node-${harness_version}.tgz" \
        && cmp -s "${packed_tarball}" "${installed_tarball}"; then
        pass 'node gates: two packs of the unchanged @airness/node tree are byte-identical and installed'
    else
        fail 'node gates: two packs of the unchanged @airness/node tree are byte-identical and installed' \
            'airness-node/target/repack is missing or differs from the packed or the installed tarball'
    fi
    packed_entries="${scratch}/airness-node-entries.txt"
    tar -tzf "${installed_tarball}" > "${packed_entries}"
    expect_file_match "${packed_entries}" 'node gates: the @airness/node tarball packs its compiled entry point' \
        '^package/dist/bin[.]js$'
    expect_file_no_match "${packed_entries}" 'node gates: the @airness/node tarball packs no TypeScript build cache' \
        '[.]tsbuildinfo$'
    run_planted_by_product_case

    lock_case="${scratch}/node-lock-refresh"
    mkdir -p "${lock_case}"
    for lock_file in package.json pnpm-workspace.yaml pnpm-lock.yaml .npmrc; do
        cp "${node_library}/${lock_file}" "${lock_case}/${lock_file}"
    done
    # Another well-formed integrity, the one of no bytes at all, where the lock records @airness/node.
    python3 - "${lock_case}/pnpm-lock.yaml" <<'PY'
import re
import sys

path = sys.argv[1]
with open(path, encoding='utf-8') as stream:
    lockfile = stream.read()
other = 'sha512-z4PhNX7vuL3xVChQ1m2AB9Yg5AULVxXcg/SpIdNs6c5H0NE8XYXysP+DGNKHfuwvY7kxvUdBeoGlODJ6+SfaPg=='
pattern = r"('@airness/node@[^']+':\s*\n\s*resolution: \{integrity: )[^,}\s]+"
altered, count = re.subn(pattern, lambda match: match.group(1) + other, lockfile)
if count != 1:
    sys.exit('the fixture lockfile records no @airness/node integrity')
with open(path, 'w', encoding='utf-8') as stream:
    stream.write(altered)
PY
    mkdir -p "${lock_case}/other-version"
    python3 - "${lock_case}/pnpm-lock.yaml" "${lock_case}/other-version/pnpm-lock.yaml" "${harness_version}" <<'PY'
import sys

source, target, version = sys.argv[1:]
with open(source, encoding='utf-8') as stream:
    lockfile = stream.read()
served = f'@airness/node@{version}'
if served not in lockfile:
    sys.exit('the fixture lockfile names no @airness/node at the served version')
with open(target, 'w', encoding='utf-8') as stream:
    stream.write(lockfile.replace(served, '@airness/node@0.0.0-other'))
PY
    start_lock_registry stale "${lock_case}/pnpm-lock.yaml"
    if [ "${lock_registry_outcome}" = refused ]; then
        pass 'node gates: the registry refuses a lockfile that records another @airness/node integrity'
    else
        fail 'node gates: the registry refuses a lockfile that records another @airness/node integrity' \
            "npm-registry.py ${lock_registry_outcome} although the lockfile records another integrity"
    fi
    expect_file_match "${lock_case}/stale.log" \
        'node gates: the refusal names the package and both integrities' \
        "@airness/node@${lock_version}: the lockfile records sha512-z4PhNX7vuL3x.*, but .* has sha512-"
    expect_file_match "${lock_case}/stale.log" \
        'node gates: the refusal prints the lockfile-only install that refreshes the lock' \
        'pnpm_config_cache_dir="[$][(]mktemp -d[)]" pnpm install --lockfile-only --fix-lockfile --ignore-scripts'

    # The printed refresh, run as printed with the pinned pnpm against the registry the fixtures install from,
    # which the scope line of the copied .npmrc names.
    refresh_log="${lock_case}/refresh.log"
    refresh_cache="$(mktemp -d "${scratch}/.pnpm-refresh-XXXXXX")"
    if (cd "${lock_case}" && PATH="${node_home}/bin:${pnpm_home}:${PATH}" \
        pnpm_config_manage_package_manager_versions=false pnpm_config_store_dir="${scratch}/.pnpm-store" \
        pnpm_config_cache_dir="${refresh_cache}" \
        "${pnpm_home}/pnpm" install --lockfile-only --fix-lockfile --ignore-scripts) > "${refresh_log}" 2>&1 \
        && python3 -c "${locks_packed_integrity}" "${packed_tarball}" "${lock_case}/pnpm-lock.yaml"; then
        pass 'node gates: the printed refresh records the served integrity with the pinned pnpm'
    else
        fail 'node gates: the printed refresh records the served integrity with the pinned pnpm' \
            "see ${refresh_log}"
    fi

    start_lock_registry current "${lock_case}/pnpm-lock.yaml"
    if [ "${lock_registry_outcome}" = served ]; then
        pass 'node gates: the registry starts against a lockfile that records the served integrity'
    else
        fail 'node gates: the registry starts against a lockfile that records the served integrity' \
            "npm-registry.py ${lock_registry_outcome}; see ${lock_case}/current.log"
    fi

    # The stale integrity kept, but under another version: the comparison is by exact name@version, so a lock
    # that resolves no @airness/node at the served version is no mismatch.
    start_lock_registry other-version "${lock_case}/other-version/pnpm-lock.yaml"
    if [ "${lock_registry_outcome}" = served ]; then
        pass 'node gates: the registry starts against a lockfile without the served @airness/node version'
    else
        fail 'node gates: the registry starts against a lockfile without the served @airness/node version' \
            "npm-registry.py ${lock_registry_outcome}; see ${lock_case}/other-version.log"
    fi
    start_lock_registry missing "${lock_case}/missing/pnpm-lock.yaml"
    if [ "${lock_registry_outcome}" = served ]; then
        pass 'node gates: the registry starts against a lockfile not written yet'
    else
        fail 'node gates: the registry starts against a lockfile not written yet' \
            "npm-registry.py ${lock_registry_outcome}; see ${lock_case}/missing.log"
    fi
}

# Every pack the reactor makes starts from an emptied dist, so it cannot show that the files allowlist keeps a
# by-product out. This packs the built package, copied with a TypeScript build cache and a source map planted
# in its dist, with the pinned pnpm: neither may be packed, and the pack must be the installed tarball's bytes.
# A files entry that packed all of dist again would list both and change the bytes.
run_planted_by_product_case() {
    planted_package="${scratch}/airness-node-planted"
    mkdir -p "${planted_package}"
    cp -R "${repository}/airness-node/package.json" "${repository}/airness-node/dist" \
        "${repository}/airness-node/configs" "${planted_package}/"
    printf '{}\n' > "${planted_package}/dist/tsconfig.tsbuildinfo"
    printf '{}\n' > "${planted_package}/dist/x.js.map"
    planted_tarball="${scratch}/airness-node-planted-pack/airness-node-${harness_version}.tgz"
    planted_entries="${scratch}/airness-node-planted-entries.txt"
    if (cd "${planted_package}" && PATH="${node_home}/bin:${pnpm_home}:${PATH}" \
        pnpm_config_manage_package_manager_versions=false \
        "${pnpm_home}/pnpm" pack --pack-destination "${scratch}/airness-node-planted-pack") \
        > "${scratch}/airness-node-planted-pack.log" 2>&1; then
        tar -tzf "${planted_tarball}" > "${planted_entries}"
    else
        fail 'node gates: the pinned pnpm packs the built @airness/node with planted by-products' \
            "see ${scratch}/airness-node-planted-pack.log"
    fi
    expect_file_no_match "${planted_entries}" \
        'node gates: a TypeScript build cache planted in dist is not packed' '[.]tsbuildinfo$'
    expect_file_no_match "${planted_entries}" 'node gates: a source map planted in dist is not packed' '[.]map$'
    if cmp -s "${planted_tarball}" "${installed_tarball}"; then
        pass 'node gates: planted by-products leave the pack byte-identical to the installed tarball'
    else
        fail 'node gates: planted by-products leave the pack byte-identical to the installed tarball' \
            "${planted_tarball} is missing or differs from ${installed_tarball}"
    fi
}

# Starts the managed registry helper on the packed tarball, named $1 for its port file and log and checked
# against the lockfile $2, and waits until it serves, exits or 30 seconds pass. It never outlives the wait, so a helper that should have
# refused cannot hang the suite. Sets lock_registry_outcome to served, refused (a non-zero exit before the port
# file), "exited with 0", "wrote its port file and exited" or "neither served nor exited".
start_lock_registry() {
    python3 "${repository}/airness-node/.airness/npm-registry.py" --port-file "${lock_case}/$1.port" \
        --fresh "${packed_tarball}" --lockfile "$2" > "${lock_case}/$1.log" 2>&1 &
    lock_registry=$!
    lock_registry_waited=0
    while [ ! -s "${lock_case}/$1.port" ] && kill -0 "${lock_registry}" 2>/dev/null \
        && [ "${lock_registry_waited}" -lt 30 ]; do
        command sleep 1
        lock_registry_waited=$((lock_registry_waited + 1))
    done
    if kill -0 "${lock_registry}" 2>/dev/null; then
        if [ -s "${lock_case}/$1.port" ]; then
            lock_registry_outcome=served
        else
            lock_registry_outcome='neither served nor exited'
        fi
        kill "${lock_registry}" 2>/dev/null || true
        wait "${lock_registry}" 2>/dev/null || true
    elif wait "${lock_registry}"; then
        lock_registry_outcome='exited with 0'
    elif [ -e "${lock_case}/$1.port" ]; then
        lock_registry_outcome='wrote its port file and exited'
    else
        lock_registry_outcome=refused
    fi
}

# Every Default gate airness-parent binds that applies to every node project, in the order the result files are
# asserted, and the Default facet gates, which a library without their facet reads as not applicable.
node_gates='wiring types biome oxlint eslint source-rules config-refs javascript-allowlist arch type-coverage knip
tests install-scripts release-age npm-inventory'
node_facet_gates='css tailwind-tokens'

# Starts the registry that serves the @airness/node tarball this reactor packed and the fixtures' scripted
# packages. It stops on its own once the shell that started it is gone, so the full suite starts it before the
# lanes and both lanes share that one server for the whole run. A lane that starts one of its own, as a single
# domain does, gets its own port file and staging directory: sharing them let a lane read the port of the other
# lane's server and lose it, mid-case, when that lane finished first.
# Further tarballs a caller names, such as a stack layer's packages, are served as fresh releases beside
# @airness/node. The registry is started once per run, so a caller that needs them starts it first.
start_airness_node_registry() {
    if [ -n "${airness_node_registry-}" ]; then
        return
    fi
    registry_staging="$(mktemp -d "${scratch}/.airness-node-registry-XXXXXX")"
    registry_port_file="${registry_staging}/port"
    registry_archive="${registry_staging}/airness-node.tgz"
    cp "${repository}/airness-node/target/airness-node-${harness_version}.tgz" "${registry_archive}"
    for fixture_package in "${repository}"/airness-it/fixtures/node-packages/*; do
        fixture_staging="${registry_staging}/packages/$(basename "${fixture_package}")"
        mkdir -p "${fixture_staging}/package"
        cp -R "${fixture_package}/." "${fixture_staging}/package/"
        tar -czf "${fixture_staging}.tgz" -C "${fixture_staging}" package
    done
    python3 "${repository}/airness-assets/src/main/resources/airness/project-files/node/.airness/npm-registry.py.asset" \
        --port-file "${registry_port_file}" \
        --fresh "${registry_archive}" "$@" --old "${registry_staging}"/packages/*.tgz \
        > "${registry_staging}/registry.log" 2>&1 &
    registry_waited=0
    while [ ! -s "${registry_port_file}" ]; do
        if [ "${registry_waited}" -ge 30 ]; then
            printf '\n  %sthe @airness/node registry did not start%s\n' "${style_fail}" "${style_off}" >&2
            cat "${registry_staging}/registry.log" >&2
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
    pinned_node_version="$(sed -n 's/^node[.]version=//p' "${node_pins}")"
    pinned_pnpm_version="$(sed -n 's/^pnpm[.]version=//p' "${node_pins}")"
    node_home="$(find "${node_toolchain}" -maxdepth 1 -type d \
        -name "node-${pinned_node_version}-*" | head -n 1)"
    pnpm_home="$(find "${node_toolchain}" -maxdepth 1 -type d \
        -name "pnpm-${pinned_pnpm_version}-*" | head -n 1)"
    install_log="${scratch}/install-$(printf '%s' "$1" | cksum | cut -d ' ' -f 1).log"
    # A cache of this run's own. pnpm keeps registry metadata per registry host and port across runs, and for a
    # version its cached metadata already names it reuses the cached integrity, so a port the system handed out
    # before would install the @airness/node an earlier build packed. The store is this run's too, so nothing a
    # fixture installs comes from outside the run. The lockfile then names the integrity of this build's tarball,
    # which node-dependencies installs by, frozen and content-addressed, whatever its own cache holds. pnpm 12
    # reads these settings from pnpm_config_ variables only: an npm_config_ one is silently ignored. The lockfile
    # is written here, also over one a case's changed manifest outdates, so it is never frozen, as pnpm freezes it
    # by default wherever CI is set.
    if ! (cd "$1" && PATH="${node_home}/bin:${pnpm_home}:${PATH}" pnpm_config_manage_package_manager_versions=false \
        pnpm_config_cache_dir="${scratch}/.pnpm-cache" pnpm_config_store_dir="${scratch}/.pnpm-store" \
        "${pnpm_home}/pnpm" install --no-frozen-lockfile --ignore-scripts) > "${install_log}" 2>&1; then
        printf '\n  %sinstalling the dependencies of %s failed%s\n' "${style_fail}" "$1" "${style_off}" >&2
        cat "${install_log}" >&2
        exit 1
    fi
    # Asserted once per run, where the first install wrote them: pnpm used the run's own cache and store.
    if [ -z "${airness_node_store_checked-}" ]; then
        airness_node_store_checked=1
        stored_file="$(find "${scratch}/.pnpm-store" -mindepth 1 -print 2>/dev/null | head -n 1)"
        cached_file="$(find "${scratch}/.pnpm-cache" -mindepth 1 -print 2>/dev/null | head -n 1)"
        if [ -n "${stored_file}" ] && [ -n "${cached_file}" ]; then
            pass 'node gates: the fixture install uses the pnpm cache and store of the run'
        else
            fail 'node gates: the fixture install uses the pnpm cache and store of the run' \
                "pnpm wrote nothing under ${scratch}/.pnpm-store or .pnpm-cache, so it ignored the variables"
        fi
    fi
}

# The facets of a Next.js application, run by node-gate in a pure TypeScript module whose parent is
# airness-parent-node. One clean Default build proves the stylesheet facet applies, the Tailwind facet does
# not, the Next.js lint, type and coverage rules accept a conforming application, and the assets goal seeds the
# proxy into the node project because it declares next. The Extended gates then run as the bound executions of
# the extended profile, by their ids, without the rest of that profile: the SBOM, the production build, the
# client bundle and the end-to-end suite pass. A report-only run of the same executions after a validate, with
# a faint paragraph added and the budget lowered, proves the accessibility sweep and the budget reach their
# findings (a header defect would stop the sweep at its own header check first, so the header rule is proven by
# Vitest), and that node-projects passes over the package.json the build wrote under .next. The
# variants are unit-tested by the Vitest suite of airness-node.
run_node_next_cases() {
    node_next="${scratch}/node-next"
    mkdir -p "${node_next}"
    cp -R "${repository}/airness-it/fixtures/node-next/." "${node_next}/"
    install_airness_node "${node_next}"
    cat > "${node_next}/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>eu.ciechanowiec</groupId>
        <artifactId>airness-parent-node</artifactId>
        <version>1.0.10-SNAPSHOT</version>
    </parent>
    <groupId>com.example</groupId>
    <artifactId>site</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
    <properties>
        <airness.package.root>com.example</airness.package.root>
    </properties>
</project>
POM
    git -C "${node_next}" init --quiet
    git -C "${node_next}" config user.name Fixture
    git -C "${node_next}" config user.email fixture@example.invalid
    prepare_maven node_next_assets node "${node_next}" --quiet airness:assets-sync
    git -C "${node_next}" add --all
    git -C "${node_next}" commit --quiet \
        --message 'test(it): create a Next.js application fixture' \
        --message 'The fixture proves the facet gates are bound in the installed node parent.'

    expect_file_match "${node_next}/src/proxy.ts" 'node next: the assets goal seeds the proxy of a Next.js application' \
        "response[.]headers[.]set[(]'x-content-type-options', 'nosniff'[)]"
    run_maven node_next node "${node_next}" clean verify
    expect_exit node_next 'node next: a conforming Next.js application passes Default verification' 0
    expect_match node_next 'node next: the stylesheet facet applies and passes' 'Node gate css in [.] passed'
    expect_match node_next 'node next: the Tailwind facet does not apply without tailwindcss' \
        'Node gate tailwind-tokens in [.] not applicable'
    expect_match node_next 'node next: the suite leaves the App Router entry points and the proxy to e2e' \
        'Node gate tests in [.] passed'

    run_maven node_next_extended node "${node_next}" -Pextended airness:node-gate@node-gate-npm-sbom \
        airness:node-gate@node-gate-build airness:node-gate@node-gate-bundle airness:node-gate@node-gate-e2e
    expect_exit node_next_extended 'node next: the Extended facet gates pass a conforming Next.js application' 0
    for node_gate in npm-sbom build bundle e2e; do
        expect_match node_next_extended "node next: ${node_gate} runs through node-gate and passes" \
            "Node gate ${node_gate} in [.] passed"
    done
    expect_file_match "${node_next}/target/airness/node/module/npm-sbom.json" \
        'node next: npm-sbom lists its evidence in the result' '"bom[.]cdx[.]json"'
    expect_file_match "${node_next}/target/airness/node/module/bom.cdx.json" \
        'node next: the SBOM is CycloneDX and names the resolved next by its package URL' '"pkg:npm/next@16[.]4[.]3"'
    expect_match node_next_extended 'node next: the bundle gate measures the build' 'client JavaScript .* gzipped'

    sed "s|<h1>|<p style={{ color: '#c8c8c8' }}>Faint</p>\\n      <h1>|" "${node_next}/src/app/page.tsx" \
        > "${node_next}/src/app/page.tsx.planted"
    mv "${node_next}/src/app/page.tsx.planted" "${node_next}/src/app/page.tsx"
    python3 - "${node_next}/package.json" <<'PY'
import json
import sys

path = sys.argv[1]
with open(path, encoding='utf-8') as manifest:
    document = json.load(manifest)
document['airness']['bundleBudgetBytes'] = 1
with open(path, 'w', encoding='utf-8') as manifest:
    json.dump(document, manifest, indent=2)
    manifest.write('\n')
PY
    run_maven node_next_report node "${node_next}" -Pextended -Dairness.enforce=false validate \
        airness:node-gate@node-gate-build airness:node-gate@node-gate-bundle airness:node-gate@node-gate-e2e
    expect_exit node_next_report 'node next: report-only mode prints the planted facet findings' 0
    expect_no_match node_next_report 'node next: node-projects passes over the package.json the build wrote' \
        'no gate checks'
    expect_match node_next_report 'node next: bundle reports a client bundle over the lowered budget' \
        'is over budget'
    expect_match node_next_report 'node next: the shipped accessibility sweep reports the faint text' 'color-contrast'
    expect_match node_next_report 'node next: e2e fails on its planted defect' 'Node gate e2e in [.] failed'
}
