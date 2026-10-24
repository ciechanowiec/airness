#!/usr/bin/env sh

# The repository rules ported from ploaness: image integrity, environment coherence, agent documentation
# references, skill manifests, generated-file denial, container definitions and workflows. Their variants
# are unit-tested in airness-governance and airness-maven-plugin. These runs prove each goal is bound in
# the installed parent: one report-only run up to the Extended scanners carries a planted defect for every
# rule, since none of the defects reads what another rule reads, and one enforcing Default run proves
# that the findings fail the build.
run_generic_rule_cases() {
    new_consumer generic-rules
    generic_consumer="${consumer_directory}"
    mkdir -p "${generic_consumer}/public" "${generic_consumer}/.claude/skills/Release_Notes" \
        "${generic_consumer}/.github/workflows" "${generic_consumer}/broken"
    printf 'services: not-a-mapping\n' > "${generic_consumer}/broken/compose.yaml"
    printf '\211PNG\r\n\032\n' > "${generic_consumer}/public/broken.png"
    cat > "${generic_consumer}/compose.yaml" <<'YAML'
services:
  database:
    image: postgres:17.5
    environment:
      POSTGRES_PASSWORD: ${DB_PASSWORD}
YAML
    printf '\nRead `docs/missing-guide.md` before changing the build.\n' >> "${generic_consumer}/AGENTS.md"
    printf '# Release notes\n\nDrafts release notes.\n' \
        > "${generic_consumer}/.claude/skills/Release_Notes/SKILL.md"
    printf 'FROM alpine:3.22\nUSER 65532\nRUN apk add curl\n' > "${generic_consumer}/Dockerfile"
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
    expect_match generic_batch 'generic rules: agent documentation names the missing file' \
        'AGENTS[.]md: docs/missing-guide[.]md'
    expect_match generic_batch 'generic rules: skill manifests name the missing frontmatter' \
        '[.]claude/skills/Release_Notes/SKILL[.]md [[]frontmatter[]]'
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

    run_maven generic_enforced scanners "${generic_consumer}" clean verify
    expect_exit generic_enforced 'generic rules: the findings fail an enforcing Default build' 1
    expect_match generic_enforced 'generic rules: the first ported rule stops the enforcing build' \
        'image-assets [(]airness-governance-sources[)]'
}
