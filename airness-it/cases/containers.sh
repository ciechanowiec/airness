#!/usr/bin/env sh

run_qodana_profile() {
    create_qodana_fixture
    add_early_qodana_sources "${qodana_consumer}"
    run_maven qodana_profile containers "${qodana_consumer}" airness:qodana
    qodana_log="$(execution_log qodana_profile)"
    expect_exit qodana_profile 'qodana: the intended negative control fails enforcement' 1
    qodana_sarif="${qodana_consumer}/target/qodana/qodana.sarif.json"
    if [ ! -f "${qodana_sarif}" ]; then
        fail 'qodana: the run left no report to read'
        excerpt_log "${qodana_log}" >&2
    else
        verify_early_qodana_findings "${qodana_sarif}"
        for dropped in InnerClassOnInterface ClassWithTooManyDependencies Singleton MagicNumber SwitchStatement; do
            reported="$(grep -c "\"ruleId\": \"${dropped}\"" "${qodana_sarif}" || true)"
            if [ "${reported}" -eq 0 ]; then
                pass "qodana: ${dropped} reports nothing on the fixture"
            else
                fail "qodana: ${dropped} reported ${reported} finding(s)"
            fi
        done
        expect_file_match "${qodana_sarif}" \
            'qodana: a switch without a default is still reported' \
            '"ruleId": "SwitchStatementsWithoutDefault"'
        # The same reports read through the findings table, which places each one at its file and line.
        expect_finding qodana_profile 'qodana: the switch without a default is placed at its statement' \
            qodana 'src/main/java/com/example/IncompleteDispatch.java:26' '^SwitchStatementsWithoutDefault '
        expect_finding qodana_profile 'qodana: the over-coupled class is placed at its declaration' \
            qodana 'src/main/java/com/example/CommandRouter.java:9' '^ClassCoupling .*overly coupled'
        if grep -q "'CommandRouter' is overly coupled" "${qodana_sarif}"; then
            pass 'qodana: ClassCoupling still reports an over-coupled class'
        else
            fail 'qodana: ClassCoupling stopped reporting the over-coupled class'
            excerpt_log "${qodana_log}" >&2
        fi
        for spared in CommandRunner JdkHeavy; do
            if grep -q "'${spared}' is overly coupled" "${qodana_sarif}"; then
                fail "qodana: ClassCoupling reported ${spared}"
            else
                pass "qodana: ClassCoupling leaves ${spared} alone"
            fi
        done
        if grep -q "'Consignment' has too many fields" "${qodana_sarif}"; then
            pass 'qodana: FieldCount still reports a class carrying too much state of its own'
        else
            fail 'qodana: FieldCount stopped reporting a class carrying too much state of its own'
            excerpt_log "${qodana_log}" >&2
        fi
        if grep -q "'Soundings' has too many fields" "${qodana_sarif}"; then
            fail 'qodana: FieldCount counted the constants a class holds as state of its own'
        else
            pass 'qodana: FieldCount leaves the bounds and tables a class holds as constants alone'
        fi
        if grep -q "Class 'Tool' has only 'static' members" "${qodana_sarif}"; then
            pass 'qodana: a static-only class with a main is still reported'
        else
            fail 'qodana: the utility-class exemption reached a project without the annotation'
        fi
        for named in bar 'then'; do
            if grep -q "Questionable name '${named}'" "${qodana_sarif}"; then
                fail "qodana: QuestionableName refused the domain word ${named}"
            else
                pass "qodana: QuestionableName leaves the domain word ${named} alone"
            fi
        done
        if grep -q "Questionable name 'foo'" "${qodana_sarif}"; then
            pass 'qodana: QuestionableName still reports a placeholder'
        else
            fail 'qodana: QuestionableName stopped reporting a placeholder'
        fi
    fi
}

run_container_cases() {
    if ! docker info >/dev/null 2>&1; then
        fail 'containers: Docker did not answer, so boundary evidence cannot run'
        return
    fi
    run_extended_consumer
    run_secret_scanner_control
    run_qodana_profile
    run_linked_worktree_checks
    run_image_cases
}

# The image gate against a real daemon and a real application: a node server whose image migrates the empty database
# it is given and then serves. Its own wrapper migrates the database before every other gate, as a governed project's
# does, so only the image gate's empty services can tell an image that migrates at startup from one that does not.
# The rule variants are proven by the Vitest suite of airness-node; this proves the gate is bound in the installed
# parent, builds with the daemon, starts the wrapper's services empty and reaches them from the container on the
# run's network, and leaves no image behind.
prepare_image_fixture() {
    image_fixture="${scratch}/node-image"
    mkdir -p "${image_fixture}"
    cp -R "${repository}/airness-it/fixtures/node-image/." "${image_fixture}/"
    install_airness_node "${image_fixture}"
    cat > "${image_fixture}/pom.xml" <<POM
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>eu.ciechanowiec</groupId><artifactId>airness-parent-node</artifactId>
        <version>${harness_version}</version>
    </parent>
    <groupId>com.example</groupId><artifactId>image-contract</artifactId><version>1.0.0</version>
    <packaging>pom</packaging>
    <properties><airness.package.root>com.example</airness.package.root></properties>
</project>
POM
    git -C "${image_fixture}" init --quiet --initial-branch=main
    git -C "${image_fixture}" config user.name Fixture
    git -C "${image_fixture}" config user.email fixture@example.invalid
    # The managed ignore files keep the installed dependencies and the registry's .npmrc out of the commit and the
    # build context, as in a governed project.
    prepare_maven image-assets containers "${image_fixture}" --quiet airness:assets-sync
    commit_image_fixture 'an image that migrates at startup'
}

commit_image_fixture() {
    git -C "${image_fixture}" add --all
    git -C "${image_fixture}" commit --quiet --message "test(image): record $1" \
        --message 'The real application proves the image gate against the installed harness and a real daemon.'
}

# The images the gate tags are its own, so none may remain once a run of it is over, whatever its verdict.
expect_no_gate_images() {
    left="$(docker images --quiet --filter 'reference=airness-image-*' 2>/dev/null)"
    if [ -z "${left}" ]; then
        pass "$1"
    else
        fail "$1" "images left behind: ${left}"
    fi
}

run_image_cases() {
    prepare_image_fixture
    run_maven image_boots containers "${image_fixture}" -Pextended airness:node-gate@node-gate-image
    expect_exit image_boots 'image: an image that migrates the empty database it is given passes' 0
    expect_match image_boots 'image: the gate builds and boots the image' 'Node gate image in [.] passed'
    # A gate that passes leaves its output in its own log rather than replaying it into the build's.
    image_log="$(find "${image_fixture}/target/airness/node-logs" -name gate-image.log 2>/dev/null | head -n 1)"
    expect_file_match "${image_log:-${image_fixture}/target/airness/node-logs/gate-image.log}" \
        'image: the wrapper starts the services empty, with no preparation run' \
        'answer empty, as the image gate asks, with no preparation run'
    expect_no_gate_images 'image: the gate removes the image it built once the image passes'

    python3 - "${image_fixture}/src/server.ts" <<'PY_UNMIGRATED'
import sys
from pathlib import Path
server = Path(sys.argv[1])
source = server.read_text()
for line in ("import { migrate } from './migrate.ts'\n", 'await migrate(databaseUrl)\n'):
    if line not in source:
        sys.exit(f'the fixture server no longer holds {line!r}')
    source = source.replace(line, '')
server.write_text(source)
PY_UNMIGRATED
    commit_image_fixture 'an image that never migrates'
    run_maven image_unmigrated containers "${image_fixture}" -Pextended airness:node-gate@node-gate-image
    expect_exit image_unmigrated 'image: an image that leaves an empty database without its schema fails' 1
    expect_match image_unmigrated 'image: the gate names the path that answered 500' 'GET / answered 500'
    expect_match image_unmigrated "image: the failure shows the container's log" 'relation "greeting" does not exist'
    expect_no_gate_images 'image: the gate removes the image it built once the image fails'

    git -C "${image_fixture}" checkout --quiet HEAD~1 -- src/server.ts
    python3 - "${image_fixture}/Dockerfile" <<'PY_PUBLIC'
import sys
from pathlib import Path
dockerfile = Path(sys.argv[1])
source = dockerfile.read_text()
if 'COPY src ./src\n' not in source:
    sys.exit('the fixture Dockerfile no longer copies src')
dockerfile.write_text(source.replace('COPY src ./src\n', 'COPY src ./src\nCOPY public ./public\n'))
PY_PUBLIC
    commit_image_fixture 'a Dockerfile that copies a directory the build never made'
    run_maven image_unbuilt containers "${image_fixture}" -Pextended airness:node-gate@node-gate-image
    expect_exit image_unbuilt 'image: a Dockerfile that cannot build fails' 1
    expect_match image_unbuilt 'image: the gate says the image does not build' \
        'Dockerfile: the image does not build from the member.s directory'

    git -C "${image_fixture}" rm --quiet Dockerfile
    commit_image_fixture 'a project without a Dockerfile'
    run_maven image_absent containers "${image_fixture}" -Pextended airness:node-gate@node-gate-image
    expect_exit image_absent 'image: a node project without a Dockerfile completes the gate' 0
    expect_match image_absent 'image: the gate does not apply without a Dockerfile' \
        'Node gate image in [.] not applicable'
}

# A worktree made by git worktree add keeps its history in the main clone. Mounted alone, it handed gitleaks
# a pointer to nowhere, and gitleaks scanned no commit and reported a clean history; Qodana refused it. Both
# now mount the clone's Git directories beside the worktree, so a worktree reads as the clone does.
run_linked_worktree_checks() {
    secret_worktree="${scratch}/secret-history-worktree"
    git -C "${secret_consumer}" worktree add --quiet --detach "${secret_worktree}"
    run_maven secret_worktree containers "${secret_worktree}" \
        airness:scan-secrets -Dairness.enforce=false
    expect_exit secret_worktree 'worktree: the secret scan runs in a linked worktree' 0
    expect_match secret_worktree 'worktree: the secret scan reads the history the clone holds' \
        'leaks found: 1'
    expect_no_match secret_worktree 'worktree: Git inside the scanner finds the repository' \
        'not a git repository'

    git -C "${qodana_consumer}" add --all
    git -C "${qodana_consumer}" commit --quiet --message 'test(qodana): record the inspected sources'
    qodana_worktree="${scratch}/qodana-profile-worktree"
    git -C "${qodana_consumer}" worktree add --quiet --detach "${qodana_worktree}"
    run_maven qodana_worktree containers "${qodana_worktree}" airness:qodana
    expect_exit qodana_worktree 'worktree: Qodana inspects a linked worktree and reports its findings' 1
    expect_finding qodana_worktree 'worktree: Qodana places the findings in the worktree' \
        qodana 'src/main/java/com/example/IncompleteDispatch.java:26' '^SwitchStatementsWithoutDefault '
    expect_no_match qodana_worktree 'worktree: Qodana no longer asks for a standalone clone' \
        'standalone clone'
}

run_extended_consumer() {
    new_consumer extended-profile
    extended_consumer="${consumer_directory}"
    git -C "${extended_consumer}" commit --quiet --allow-empty --message 'wip'
    run_maven extended_profile containers "${extended_consumer}" \
        clean verify -Pextended -Dairness.enforce=false
    expect_exit extended_profile 'extended: a consumer completes the installed Extended profile in report-only mode' 0
    expect_count extended_profile 'extended: every repository governance goal is reached' \
        'airness:[^ ]+:(commit-history|commit-typography|linear-history|scan-secrets) \(airness-governance-extended\)' 4
    expect_match extended_profile 'extended: the real secret scanner runs in the profile' \
        'commits scanned|no leaks found'
    expect_match extended_profile 'extended: the real Qodana inspection runs in the profile' \
        'Qodana - Detailed summary|Analysis results:'
    expect_match extended_profile 'extended: report-only keeps the history finding visible' \
        'Commit messages that break the policy'
    expect_analysis_before_qodana extended_profile
}

run_secret_scanner_control() {
    new_consumer secret-history
    secret_consumer="${consumer_directory}"
    {
        printf '%s%s%s\n' '-----BEGIN OPEN' 'SSH PRIVATE ' 'KEY-----'
        printf '%s\n' 'b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAAB'
        printf '%s%s%s\n' '-----END OPEN' 'SSH PRIVATE ' 'KEY-----'
    } > "${secret_consumer}/committed-credential.txt"
    git -C "${secret_consumer}" add committed-credential.txt
    git -C "${secret_consumer}" commit --quiet \
        --message 'test(secrets): add a committed scanner control' \
        --message 'The credential-shaped fixture proves that the container reads complete Git history.'
    rm "${secret_consumer}/committed-credential.txt"
    git -C "${secret_consumer}" add --all
    git -C "${secret_consumer}" commit --quiet \
        --message 'test(secrets): remove the scanner control from the tree'
    run_maven secret_history containers "${secret_consumer}" \
        airness:scan-secrets -Dairness.enforce=false
    expect_exit secret_history 'secrets: history-wide findings remain reportable' 0
    expect_match secret_history 'secrets: the removed working-tree value is still found in history' \
        'leaks found: 1'
    expect_match secret_history 'secrets: the container reports scanning commits' \
        '[0-9]+ commits scanned'
}
