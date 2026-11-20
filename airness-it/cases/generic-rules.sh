#!/usr/bin/env sh

# The generic repository rules: image integrity, environment coherence, hosted runner images, agent
# documentation references, skill manifests, Markdown documents, generated-file denial, compose isolation,
# container hardening, container definitions and workflows.
# Their variants are unit-tested in airness-governance and airness-maven-plugin. These runs prove each goal
# is bound in the installed parent: one report-only run up to the Extended scanners carries a planted defect
# for every rule, since none of the defects reads what another rule reads, and one enforcing Default run
# proves that the findings fail the build.
run_generic_rule_cases() {
    new_consumer generic-rules
    generic_consumer="${consumer_directory}"
    mkdir -p "${generic_consumer}/public" "${generic_consumer}/.claude/skills/Release_Notes" \
        "${generic_consumer}/.github/workflows" "${generic_consumer}/broken" "${generic_consumer}/docs"
    printf 'services: not-a-mapping\n' > "${generic_consumer}/broken/compose.yaml"
    printf '\211PNG\r\n\032\n' > "${generic_consumer}/public/broken.png"
    cat > "${generic_consumer}/compose.yaml" <<'YAML'
services:
  database:
    image: postgres:17.5
    container_name: shared-database
    privileged: true
    ports:
      - "5432:5432"
    environment:
      POSTGRES_PASSWORD: ${DB_PASSWORD}
      CACHE_TOKEN: shared
YAML
    printf '\nRead %sdocs/missing-guide.md%s before changing the build.\n' '`' '`' >> "${generic_consumer}/AGENTS.md"
    printf '# Release notes\n\nDrafts release notes.\n' \
        > "${generic_consumer}/.claude/skills/Release_Notes/SKILL.md"
    printf '# Guide\n\nThis paragraph is cut\nacross two lines and links to [the setup](setup.md).\n' \
        > "${generic_consumer}/docs/guide.md"
    printf 'FROM alpine:3.22\nUSER 65532\nRUN apk add curl\nARG NPM_TOKEN\n' > "${generic_consumer}/Dockerfile"
    cat > "${generic_consumer}/.github/workflows/ci.yml" <<'YAML'
name: 'Greeting'
on: push
permissions:
  contents: read
jobs:
  greet:
    runs-on: ubuntu-latest
    steps:
      - run: echo "${{ github.no_such_property }}"
YAML

    run_maven generic_batch scanners "${generic_consumer}" \
        clean prepare-package -Pextended -Dairness.enforce=false
    expect_exit generic_batch 'generic rules: every planted defect is reported without stopping the batch' 0
    expect_match generic_batch 'generic rules: image integrity names the truncated image' \
        'public/broken[.]png: truncated PNG [(]no IEND chunk[)]'
    expect_match generic_batch 'generic rules: environment coherence names the undocumented compose variable' \
        'DB_PASSWORD in compose[.]yaml'
    expect_match generic_batch 'generic rules: compose isolation names the host port every checkout asks for' \
        'compose[.]yaml:7: database publishes host port 5432'
    expect_match generic_batch 'generic rules: compose isolation names the container name every checkout asks for' \
        'compose[.]yaml:4: database is named shared-database'
    expect_match generic_batch 'generic rules: container hardening names the privileged service' \
        'compose[.]yaml:5: database runs privileged'
    expect_match generic_batch 'generic rules: container hardening names the port published beyond loopback' \
        'compose[.]yaml:7: database publishes 5432:5432 beyond loopback'
    expect_match generic_batch 'generic rules: container hardening names the credential the compose file writes' \
        'compose[.]yaml:10: database gives CACHE_TOKEN a value'
    expect_match generic_batch 'generic rules: container hardening names the credential the Dockerfile declares' \
        'Dockerfile:4: ARG NPM_TOKEN'
    expect_match generic_batch 'generic rules: runner images name the aliased hosted runner with its replacement' \
        '[.]github/workflows/ci[.]yml:7: ubuntu-latest - .*; use ubuntu-26[.]04'
    expect_match generic_batch 'generic rules: agent documentation names the missing file' \
        'AGENTS[.]md: docs/missing-guide[.]md'
    expect_match generic_batch 'generic rules: skill manifests name the missing frontmatter' \
        '[.]claude/skills/Release_Notes/SKILL[.]md [[]frontmatter[]]'
    expect_match generic_batch 'generic rules: Markdown names the link to a file nobody committed' \
        "docs/guide[.]md:4: 'setup[.]md' names no committed file"
    expect_match generic_batch 'generic rules: Markdown names the paragraph cut across lines' \
        'docs/guide[.]md:3: the paragraph is cut across lines'
    expect_match generic_batch 'generic rules: generated-file denial is bound in the generic parent' \
        'generated-denial [(]airness-governance-sources[)]'
    expect_match generic_batch 'generic rules: the Dockerfile is linted by the pinned hadolint' \
        'Dockerfile:3 DL3018 '
    expect_match generic_batch 'generic rules: both compose projects are found' \
        'Docker: 1 Dockerfile[(]s[)] and 2 compose project[(]s[)]'
    expect_match generic_batch 'generic rules: docker compose names the project that does not load' \
        'broken/compose[.]yaml: .*services must be a mapping'
    expect_no_match generic_batch 'generic rules: the loadable compose project is not reported' \
        '^  compose[.]yaml: '
    expect_match generic_batch 'generic rules: the workflow is linted by the pinned actionlint' \
        '[.]github/workflows/ci[.]yml:9 [[]expression[]]'
    discard_scanner_evidence generic_batch

    run_maven generic_enforced scanners "${generic_consumer}" clean verify
    expect_exit generic_enforced 'generic rules: the findings fail an enforcing Default build' 1
    expect_match generic_enforced 'generic rules: the first ported rule stops the enforcing build' \
        'image-assets [(]airness-governance-sources[)]'
}
