#!/usr/bin/env sh

# The Payload stack against a real Payload application, from the artefacts mvn install put in the local
# repository and the @airness/payload tarball the reactor packed. The conforming application must pass every
# gate. The same application with one defect planted per Payload gate must report each one in a single
# report-only build, and fail an enforcing build. A copy of the conforming application on an isolated PostgreSQL
# then proves a real custom admin view scan and the shipped access-boundary specification. The rule variants are
# proven by the Vitest suite of airness-payload-node; this proves each gate is bound in the installed parent and
# reaches its finding through airness:node-gate.

payload_gates='preflight payload-blocklist payload-source-rules admin-views payload-environment payload-generated
payload-defaults payload-migrations'

# A release of the package this reactor did not build: the same package at another version, which a fixture
# resolving it must fail wiring for, since airness-parent-payload names this reactor's version.
payload_mismatched_version=1.99.0

# Stages the packed @airness/payload and its repack at the mismatched version for the local registry, once per
# run, in the shell that starts the registry. The repack is written entry by entry with fixed owners, modes and
# times, so it is the same archive on every platform and every run.
prepare_payload_packages() {
    if [ -n "${payload_package-}" ]; then
        return
    fi
    payload_staging="$(mktemp -d "${scratch}/.payload-packages-XXXXXX")"
    payload_package="${payload_staging}/airness-payload.tgz"
    payload_mismatched_package="${payload_staging}/airness-payload-${payload_mismatched_version}.tgz"
    cp "${repository}/airness-payload-node/target/airness-payload-${harness_version}.tgz" "${payload_package}"
    python3 - "${payload_package}" "${payload_mismatched_package}" "${payload_mismatched_version}" <<'PY'
import gzip
import io
import json
import sys
import tarfile

source, target, version = sys.argv[1:]
with tarfile.open(source, 'r:gz') as archive:
    contents = {
        member.name: archive.extractfile(member).read() for member in archive.getmembers() if member.isfile()
    }
manifest = json.loads(contents['package/package.json'])
manifest['version'] = version
contents['package/package.json'] = (json.dumps(manifest, indent=2) + '\n').encode('utf-8')
packed = io.BytesIO()
with tarfile.open(fileobj=packed, mode='w', format=tarfile.USTAR_FORMAT) as archive:
    for name in sorted(contents):
        entry = tarfile.TarInfo(name)
        entry.size = len(contents[name])
        entry.mode = 0o644
        entry.mtime = 499162500
        archive.addfile(entry, io.BytesIO(contents[name]))
with open(target, 'wb') as stream:
    stream.write(gzip.compress(packed.getvalue(), mtime=0))
PY
}

# Copies the fixture, wires it to this harness version, installs it, generates the Payload files as a project
# commits them, writes the managed files and commits the result. The fixture keeps its manifests as
# package.json.fixture and pom.xml.fixture, so the repository never reads it as a node project or a module.
prepare_payload_site() {
    payload_site="${scratch}/payload-site"
    mkdir -p "${payload_site}"
    cp -R "${repository}/airness-it/fixtures/payload/site/." "${payload_site}/"
    for stored in package.json pom.xml; do
        sed "s/@AIRNESS_VERSION@/${harness_version}/g" "${payload_site}/${stored}.fixture" > "${payload_site}/${stored}"
        rm "${payload_site}/${stored}.fixture"
    done
    install_airness_node "${payload_site}"
    for generator in generate:types generate:importmap; do
        generator_log="${scratch}/payload-${generator#generate:}.log"
        if ! (cd "${payload_site}" && PATH="${node_home}/bin:${PATH}" node node_modules/payload/bin.js "${generator}") \
            > "${generator_log}" 2>&1; then
            printf '\n  %spayload %s failed in %s%s\n' \
                "${style_fail}" "${generator}" "${payload_site}" "${style_off}" >&2
            cat "${generator_log}" >&2
            exit 1
        fi
    done
    git -C "${payload_site}" init --quiet --initial-branch=main
    git -C "${payload_site}" config user.name Fixture
    git -C "${payload_site}" config user.email fixture@example.invalid
    prepare_maven payload_assets payload "${payload_site}" --quiet airness:assets-sync
    git -C "${payload_site}" add --all
    git -C "${payload_site}" commit --quiet \
        --message 'test(it): create the payload site fixture' \
        --message 'The fixture proves the Payload stack against a real Payload application.'
}

# The real admin view and access-boundary check, in a subshell of its own, so its database, its trap and its
# working directory end with it. A copy of the conforming site runs against a PostgreSQL container bound to a
# random loopback port, which is always removed. Payload generates the initial schema and applies it, seeds a
# user, and the Extended browser gates then scan the planted custom view and run the access-boundary spec.
run_payload_browser() (
    set -eu
    browser_container="airness-it-payload-postgres-${scratch##*-}"
    remove_payload_database() {
        docker rm --force --volumes "${browser_container}" > /dev/null 2>&1 || true
        release_exclusive_on_exit
    }
    trap remove_payload_database EXIT
    trap 'exit 130' INT
    trap 'exit 143' TERM

    docker run --detach --name "${browser_container}" --publish 127.0.0.1::5432 \
        --env POSTGRES_USER=fixture --env POSTGRES_PASSWORD=fixture --env POSTGRES_DB=fixture \
        postgres:18-alpine@sha256:10a5c69a83035ab5239f5620d708520e8b28f5aa1f2b20143e6024532edadfb5 > /dev/null
    database_waited=0
    until docker exec "${browser_container}" pg_isready --host=127.0.0.1 --username=fixture --dbname=fixture \
        > /dev/null 2>&1; do
        if [ "${database_waited}" -ge 60 ]; then
            docker logs "${browser_container}" >&2
            exit 1
        fi
        command sleep 1
        database_waited=$((database_waited + 1))
    done
    database_port="$(docker port "${browser_container}" 5432/tcp | sed -n 's/^127[.]0[.]0[.]1://p')"
    DATABASE_URL="postgres://fixture:fixture@127.0.0.1:${database_port}/fixture"
    export DATABASE_URL

    browser_site="${scratch}/payload-browser"
    mkdir -p "${browser_site}"
    (cd "${payload_site}" && tar --exclude=node_modules --exclude=target --exclude=.git -cf - .) \
        | (cd "${browser_site}" && tar -xf -)
    ln -s "${payload_site}/node_modules" "${browser_site}/node_modules"
    cp -R "${repository}/airness-it/fixtures/payload/browser/." "${browser_site}/"
    python3 - "${browser_site}" <<'PY'
import json
import socket
import sys
from pathlib import Path

root = Path(sys.argv[1])
config = root / 'src/payload.config.ts'
source = config.read_text().replace(
    'admin: { user:',
    "admin: { components: { views: { proof: { Component: '/views/Proof#Proof', path: '/proof' } } }, user:",
)
source = source.replace(
    'db: postgresAdapter({ pool:',
    'db: postgresAdapter({ migrationDir: path.resolve("target/browser-migrations"), pool:',
)
config.write_text(source)
manifest = root / 'package.json'
document = json.loads(manifest.read_text())
with socket.socket() as listener:
    listener.bind(('127.0.0.1', 0))
    document['airness']['serverUrl'] = f'http://127.0.0.1:{listener.getsockname()[1]}'
manifest.write_text(json.dumps(document, indent=2) + '\n')
PY
    cd "${browser_site}"
    PATH="${node_home}/bin:${PATH}"
    export PATH
    # Payload dynamically imports generated migrations outside its scoped tsImport call. Its own TypeScript
    # loader is registered globally, so Node does not read those generated type imports as runtime exports.
    tsx_loader="$(node --input-type=module <<'JS_LOADER'
import { createRequire } from 'node:module'
const payload = createRequire(import.meta.resolve('payload'))
console.log(payload.resolve('tsx/esm'))
JS_LOADER
)"
    payload_cli() {
        node --import "${tsx_loader}" node_modules/payload/bin.js "$@"
    }
    # A real initial schema is generated by Payload, then applied by its migration runner.
    mkdir -p target/browser-migrations
    payload_cli migrate:create browser_initial --force-accept-warning
    payload_cli migrate
    payload_cli generate:importmap
    payload_cli run scripts/seed.ts
    git init --quiet --initial-branch=main
    git config user.name Fixture
    git config user.email fixture@example.invalid
    git add --all
    git commit --quiet --message 'test(payload): prepare the real admin browser fixture' \
        --message 'The fixture runs custom view scans and anonymous access checks against Payload and PostgreSQL.'
    run_maven payload_browser payload "${browser_site}" -Pextended \
        airness:node-gate@node-gate-admin-views airness:node-gate@node-gate-build airness:node-gate@node-gate-e2e
    browser_build_status="$(execution_status payload_browser)"
    exit "${browser_build_status}"
)

# Plants one defect per Payload gate, each read by its own gate only, so none can mask another.
plant_payload_defects() {
    python3 - "${payload_site}/package.json" "${payload_mismatched_version}" <<'PY'
import json
import sys

path = sys.argv[1]
with open(path, encoding='utf-8') as stream:
    manifest = json.load(stream)
manifest['dependencies']['tedious'] = '19.1.3'
manifest['devDependencies']['@types/react'] = '19.2.2'
manifest['devDependencies']['@airness/payload'] = sys.argv[2]
with open(path, 'w', encoding='utf-8') as stream:
    json.dump(manifest, stream, indent=2)
    stream.write('\n')
PY
    install_airness_node "${payload_site}"
    printf '%s\n' "import type { Payload } from 'payload'" '' \
        'export const everyPost = async (payload: Payload): Promise<unknown> =>' \
        "  await payload.find({ collection: 'posts' })" > "${payload_site}/src/lib/reads.ts"
    printf '%s\n' "import type { PayloadHandler } from 'payload'" '' \
        'export const recentPosts: PayloadHandler = async (req) =>' \
        "  Response.json(await req.payload.find({ collection: 'posts', req, depth: 0, limit: 10 }))" \
        > "${payload_site}/src/lib/recent-posts.ts"
    mkdir -p "${payload_site}/src/hooks"
    printf '%s\n' "import type { CollectionAfterChangeHook } from 'payload'" '' \
        'export const countPosts: CollectionAfterChangeHook = async ({ doc, req: { payload } }) => {' \
        "  await payload.count({ collection: 'posts' })" '  return doc' '}' > "${payload_site}/src/hooks/count-posts.ts"
    printf '%s\n' "import type { PayloadRequest } from 'payload'" '' \
        "export const signInTarget = (req: PayloadRequest): unknown => req.data?.['email']" \
        > "${payload_site}/src/hooks/sign-in-target.ts"
    python3 - "${payload_site}/src/payload.config.ts" "${payload_site}/src/collections/Posts.ts" \
        "${payload_site}/src/lib/environment.ts" "${payload_site}/src/payload-types.ts" \
        "${payload_site}/src/app/(payload)/admin/importMap.js" <<'PY'
import sys

config, posts, environment, types, import_map = sys.argv[1:]
with open(config, encoding='utf-8') as stream:
    text = stream.read()
text = text.replace(
    'admin: { user: Users.slug, importMap: { baseDir: directory } },',
    "admin: {\n    user: Users.slug,\n    importMap: { baseDir: directory },\n"
    "    components: { views: { reports: { Component: '/views/Reports', path: '/reports' } } },\n  },",
)
if 'reports' not in text:
    sys.exit('the admin view could not be planted in the configuration')
with open(config, 'w', encoding='utf-8') as stream:
    stream.write(text)
with open(posts, encoding='utf-8') as stream:
    text = stream.read()
with open(posts, 'w', encoding='utf-8') as stream:
    stream.write(
        text.replace('versions: true,', 'versions: true,\n  folders: true,')
        .replace(
            "import { anyone, nobody } from '@/access'",
            "import { anyone, nobody } from '@/access'\nimport { recentPosts } from '@/lib/recent-posts'",
        )
        .replace(
            "fields: [{ name: 'title', type: 'text', required: true }],",
            "hooks: { beforeChange: [({ data }) => data] },\n"
            "  endpoints: [{ path: '/recent', method: 'get', handler: recentPosts }],\n"
            "  fields: [{ name: 'title', type: 'text', required: true }],",
        )
    )
with open(environment, encoding='utf-8') as stream:
    text = stream.read()
text = text.replace(
    "DATABASE_URL: process.env['DATABASE_URL'],",
    "DATABASE_URL: process.env['DATABASE_URL'] ?? process.env['UNDOCUMENTED_URL'],",
)
with open(environment, 'w', encoding='utf-8') as stream:
    stream.write(text)
with open(types, 'a', encoding='utf-8') as stream:
    stream.write('// edited by hand\n')
with open(import_map, 'a', encoding='utf-8') as stream:
    stream.write('// edited by hand\n')
PY
}

# The variables a Payload configuration validates when it is imported, inert placeholders nothing connects to.
# The Payload CLI the site preparation runs and the browser check's application read them from the process.
export_payload_placeholders() {
    PAYLOAD_SECRET=airness-it-placeholder
    DATABASE_URL=postgres://127.0.0.1:5432/airness-it
    export PAYLOAD_SECRET DATABASE_URL
}

run_payload_cases() {
    prepare_payload_packages
    start_airness_node_registry "${payload_package}" "${payload_mismatched_package}"
    export_payload_placeholders

    prepare_payload_site
    # The conforming build carries none of them: the Payload layer hands its analysis placeholders to the gates
    # that import the configuration, knip's Payload plugin among them, so a clean checkout verifies without a
    # workflow supplying them.
    unset PAYLOAD_SECRET DATABASE_URL
    run_maven payload_site payload "${payload_site}" clean verify
    export_payload_placeholders
    expect_exit payload_site 'payload: the conforming application passes mvn clean verify' 0
    for gate in ${payload_gates}; do
        expect_match payload_site "payload: ${gate} runs through node-gate and passes" "Node gate ${gate} in [.] passed"
    done
    expect_match payload_site 'payload: the node gates run on the Payload application' 'Node gate eslint in [.] passed'
    # The collections are reached only through the Payload configuration, which the Payload layer makes a
    # production entry point of knip's production run.
    expect_match payload_site 'payload: knip counts what only the Payload configuration reaches as production code' \
        'Node gate knip in [.] passed'
    expect_file_match "${payload_site}/eslint.config.mjs" \
        'payload: the Payload layer overrides the node ESLint configuration with the layered one' \
        '@airness/node/eslint-layered'
    expect_match payload_site 'payload: generated-denial reads the Payload generated files' \
        'Generated-file denial read [0-9]+ generated and pinned file'
    expect_file_match "${payload_site}/.claude/settings.json" \
        'payload: agents are denied the generated Payload types' 'Edit[(]src/payload-types[.]ts[)]'

    set +e
    run_payload_browser > "${scratch}/payload-browser.log" 2>&1
    browser_status=$?
    set -e
    if [ "${browser_status}" -eq 0 ]; then
        pass 'payload: the real admin view and the shipped access-boundary spec pass'
    else
        browser_build_log="$(execution_log payload_browser)"
        fail 'payload: the real admin view and the shipped access-boundary spec pass' \
            "see ${scratch}/payload-browser.log and ${browser_build_log}"
        excerpt_log "${scratch}/payload-browser.log" >&2
    fi

    plant_payload_defects
    run_maven payload_report payload "${payload_site}" verify -Dairness.enforce=false
    expect_exit payload_report 'payload: report-only mode prints every planted finding' 0
    expect_match payload_report 'payload: preflight reports a type package at another version than the layer pins' \
        '@types/react resolves to 19[.]2[.]2, but the Payload layer runs 19[.]3[.]0'
    expect_match payload_report 'payload: payload-blocklist reports a driver Payload has no adapter for' \
        'tedious is refused by the Payload layer'
    expect_match payload_report 'payload: payload-source-rules reports an unbounded read' \
        'src/lib/reads[.]ts:4 \[no-unbounded-find\]'
    expect_match payload_report 'payload: payload-source-rules reports a handler served from outside src/endpoints' \
        'src/collections/Posts[.]ts:[0-9]+ \[require-endpoint-module\]'
    expect_match payload_report 'payload: admin-views reports a custom view no specification scans' \
        'src/payload[.]config[.]ts:[0-9]+ \[admin-view-un(driven|scanned)\]'
    expect_match payload_report 'payload: payload-environment reports an undocumented variable' \
        'UNDOCUMENTED_URL: read by src/lib/environment[.]ts but documented in no example file'
    expect_match payload_report 'payload: payload-generated reports the hand-edited types' \
        'src/payload-types[.]ts changed when regenerated'
    expect_match payload_report 'payload: payload-generated reports the hand-edited import map' \
        'src/app/[(]payload[)]/admin/importMap[.]js changed when regenerated'
    expect_match payload_report 'payload: wiring holds the resolved package to the airness-parent-payload version' \
        "resolves @airness/payload ${payload_mismatched_version}, but the module's Airness parent names \
${harness_version}"
    expect_match payload_report \
        'payload: payload-defaults reports the folder collection Payload builds with its default access' \
        'payload-folders'
    expect_match payload_report 'payload: payload-migrations reports a schema change no migration records' \
        'ALTER TABLE "posts" ADD COLUMN "folder_id"'
    for gate in ${payload_gates}; do
        expect_match payload_report "payload: ${gate} fails on its planted defect" "Node gate ${gate} in [.] failed"
    done
    expect_match payload_report \
        'payload: eslint applies the Payload layer rules to an inline function in a collection' \
        'error  No inline functions in collection, global, field or block configs'
    expect_match payload_report 'payload: eslint refuses payload destructured out of req in a hook' \
        'error  Do not destructure payload out of the request'
    expect_match payload_report 'payload: eslint refuses a hook reading the request body' \
        'error  Read req.data only in an endpoint handler'

    # One enforcing build of the defective tree proves the findings fail it; it stops at the first, preflight.
    # That a post-integration-test gate's finding fails an enforcing build is node-gate's generic proof.
    run_maven payload_enforced payload "${payload_site}" verify
    expect_exit payload_enforced 'payload: a planted Payload finding fails an enforcing build' 1
    expect_match payload_enforced 'payload: preflight, the first Payload gate, stops the enforcing build' \
        'Node gate preflight in [.] failed'

    unset PAYLOAD_SECRET DATABASE_URL
}
