#!/usr/bin/env sh

# The node gates airness-parent binds, run by node-gate in a pure TypeScript module whose parent is
# airness-parent-node. The module links the @airness/node this reactor built, through pnpm's link: protocol,
# because the package is not published: the link names the built package directory, whose own node_modules
# hold the pinned analyzers. One clean build proves each gate is bound and passes a conforming module. One
# report-only build with a defect planted for each gate proves each gate reaches its finding through
# node-gate, and an enforcing build of the same tree proves the findings fail it. The rule variants are
# unit-tested by the Vitest suite of airness-node.
run_node_gate_cases() {
    node_library="${scratch}/node-library"
    mkdir -p "${node_library}"
    cp -R "${repository}/airness-it/fixtures/node-library/." "${node_library}/"
    link_airness_node "${node_library}"
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
    for node_gate in types biome oxlint eslint source-rules config-refs javascript-allowlist; do
        expect_match node_library "node gates: ${node_gate} runs through node-gate and passes" \
            "Node gate ${node_gate} in [.] passed"
        expect_file_match "${node_library}/target/airness/node/module/${node_gate}.json" \
            "node gates: ${node_gate} writes its result in the contract" "\"gate\": \"${node_gate}\""
    done

    # One defect per gate. None can mask another, since each gate reads every file and reports them all.
    printf "export const wrong: number = 'text'\n" > "${node_library}/src/wrong.ts"
    printf 'export const formatted: number = 1;\n' > "${node_library}/src/formatted.ts"
    printf 'export const wait = (): Promise<number> => new Promise((resolve) => resolve(1))\n' \
        > "${node_library}/src/wait.ts"
    printf '/** The limit. */\nexport const limit: number = 42 * 3\n' > "${node_library}/src/limit.ts"
    mkdir -p "${node_library}/src/deep"
    printf "export { total } from '../total.js'\n" > "${node_library}/src/deep/read.ts"
    printf 'export const legacy = 1\n' > "${node_library}/src/legacy.js"
    python3 - "${node_library}/package.json" <<'PY'
import json
import sys

path = sys.argv[1]
with open(path, encoding='utf-8') as manifest:
    document = json.load(manifest)
document['airness'] = {
    'generatedArtefacts': [{'pattern': 'src/generated/**', 'reason': 'the API client generator writes it'}]
}
with open(path, 'w', encoding='utf-8') as manifest:
    json.dump(document, manifest, indent=2)
    manifest.write('\n')
PY

    run_maven node_library_report node "${node_library}" verify -Dairness.enforce=false
    expect_exit node_library_report 'node gates: report-only mode prints every planted finding' 0
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
    for node_gate in types biome oxlint eslint source-rules config-refs javascript-allowlist; do
        expect_match node_library_report "node gates: ${node_gate} fails on its planted defect" \
            "Node gate ${node_gate} in [.] failed"
    done

    run_maven node_library_enforced node "${node_library}" verify
    expect_exit node_library_enforced 'node gates: a planted gate finding fails an enforcing build' 1
    expect_match node_library_enforced 'node gates: the first bound gate stops the enforcing build' \
        'Node gate types in [.] failed'
}

# Points a copied node project's link to @airness/node at the package this reactor built, by a path relative
# to the copy, which is the form pnpm writes into the lockfile.
link_airness_node() {
    link_target="$(python3 -c 'import os, sys; print(os.path.relpath(sys.argv[1], sys.argv[2]))' \
        "${repository}/airness-node" "$1")"
    for link_file in "$1/package.json" "$1/pnpm-lock.yaml"; do
        sed "s#@AIRNESS_NODE@#${link_target}#g" "${link_file}" > "${link_file}.linked"
        mv "${link_file}.linked" "${link_file}"
    done
}
