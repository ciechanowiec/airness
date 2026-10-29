# Airness Agent Guide

This guide maps the governing standard to the Airness harness for every repository, whatever stacks it holds.
The guide of each stack layer present adds to it, and the managed blocks of `AGENTS.md` name them. After them,
continue through the lower `AGENTS.md` layers in the order the standard declares. Exact analyzer rules remain in
the executable harness.

## Layer 1: Governing Standard

- Read `README-guideline-software-project.adoc` before using this Airness-specific mapping.

## Layer 2: Harness Invariants

- Each module inherits the Airness parent for what it contains: `airness-parent` for a monorepo root or a module
  with no code, `airness-parent-java` for Java, `airness-parent-spring-boot` for a Spring Boot project, or
  `airness-parent-node` for a module whose main content is JavaScript or TypeScript. That
  choice is the only place a module states which kind it is, and it is what decides which rules apply. A module
  that inherits `airness-parent-java` also follows `.airness/agent-guide-java.md`. Do not weaken, replace,
  duplicate, or version the plugins, dependencies, analyzers, rules, thresholds, or managed files that the parent
  owns.
- Use exactly Java 25, Maven 3.10.0 or later, and a Git working tree. Default verification reads Maven Central and
  Docker Hub and fails when either cannot be read, and Extended verification also needs a reachable Docker daemon
  that can read the repository through a bind mount. Extended verification refuses to start under `-o` or offline
  mode, because Maven skips a goal whose descriptor requires online mode, the vulnerability scan is one of those,
  and an offline run would otherwise report clean without having read a single advisory. Packaging also requires
  online Maven mode because Maven otherwise skips SBOM generation, including with `-DskipTests`.
  On macOS with Colima, a
  repository under Downloads, Desktop or Documents is unreadable inside a container until the terminal that starts
  Colima is granted access to that folder under System Settings, Privacy and Security, Files and Folders, so keep
  the repository elsewhere or grant it.
  Keep every production and test package under the `airness.package.root` declared in the root `pom.xml`.
- Treat every finding and every tool, setup, or compilation failure as a failed verification. Reporting findings with
  `-Dairness.enforce=false` is not a pass. A build using `-DskipTests` produces no Airness verdict.

## Layer 3: Governed Domains

Airness governs all of the following domains:

- **Toolchain and Maven model:** runtime versions, project and parent coordinates, package ownership, inherited plugin
  ownership, raw-model anti-bypass checks, effective dependency convergence, the ordering and local use of declared
  properties, valid Airness parameters, and current build practices: no Java version below 25 stated in the project
  file, no superseded coordinate, a version on every managed dependency, a groupId on every plugin, `project.`-prefixed
  expressions, the documented order of pom elements, and Java 21 or later in every workflow's `setup-java`.
  Every module ends with `airness:module-complete`, the last execution of `verify`: a project binds nothing in
  `verify` and names no goal after it, and either is refused at `validate`. Bind a project step to an earlier
  phase, such as `post-integration-test`, and name a command-line goal before the lifecycle
  (`mvn sonar:sonar verify`) or run it in an invocation of its own. Once every module of the build has
  completed, one closing step compares the committable tree with the one the build started from, holds the
  Java sources and the node projects' own code to their suppression ceilings, and under Extended verification
  writes the compliance report and looks every npm package up in the OSV database.
- **Repository files and instructions:** managed, seeded, and forbidden files; the root license file; agent
  instruction files; editor and Git configuration; and an unchanged committable tree during verification. Managed
  files follow what the repository contains rather than which parent a module names: the root carries the files
  of every stack present in any module, `AGENTS.md`, `.gitignore` and `.dockerignore` lead with one marked block
  per stack, the lines below the blocks belong to the project, and each declared node project carries the node
  files in its own directory. The editor
  configuration is held against the files a commit would carry, so what git is configured to ignore is passed over
  rather than reported: build output and a tool's scratch are not content this style governs. Neither is a file
  a generator owns (one a stack layer, `airness.generated.files` or a node project's `airness.generatedArtefacts`
  declares), which the editor configuration and the typography check leave unread.
  Ignored files selected as Java or resource inputs are different: Maven can consume them locally
  while a checkout omits them. The per-module `build-inputs` preflight rejects that mismatch at
  `validate`, `process-resources` and `process-test-resources`, using Maven's roots and file filters.
  Track an intended repository input, including a vendored distribution file hidden by an ignore
  pattern, or generate it beneath a reactor project's configured Maven build directory. Generated
  inputs there and ordinary non-ignored new files remain valid. No project allowlist is provided,
  and report-only mode does not excuse a preflight failure. Arbitrary custom-tool file reads are
  outside this check, so it does not establish general provenance for everything a build can read.
- **Repository content:** every image of a supported format (PNG, JPEG, GIF, WebP, ICO) decodes; every file path
  an `AGENTS.md` names in backticks exists beside it or at the root, unless it is a module specifier of a
  dependency the `package.json` there declares; every npm script it names, backticked or after `pnpm run`, is
  declared by that `package.json`; every `.claude/skills/**/SKILL.md` opens with
  frontmatter whose kebab-case `name` equals its directory, whose `description` says when to use it, and whose
  keys are all ones the agent runtime knows; every
  variable a compose file interpolates without a default is documented in `.env.example` (or `.env.sample`,
  `.env.template`) and supplied by every workflow that runs Maven through `verify`; and `.claude/settings.json`
  denies `Edit(...)` of every generated file a stack layer, `airness.generated.files` or a node project's
  `airness.generatedArtefacts` declares and of every pinned managed file, with no local settings allowing one
  back. `mvn airness:assets-sync` writes those rules beside the project's own.
- **Node projects:** every `package.json` in a module belongs to a node project the module lists in
  `airness.node.projects`, or to a member of a listed pnpm workspace, and every listed directory holds one. A
  module that lists nothing is checked too. A module that lists a project gets the Node and pnpm the Airness
  release pins, verified by SHA-256 and cached in the local Maven repository, and each project's locked
  dependencies, installed with no lifecycle script run. No project may pick a pnpm or Node of its own through
  `packageManager`, `devEngines`, `useNodeVersion`, `nodeVersion` or `executionEnv`. Build a frontend into the
  artifact only with an `airness:node-run` execution, the one declaration of the Airness plugin a project may
  write, with an id of its own and a phase before `prepare-package`. `com.github.eirslett` artifacts, however
  they reach the build, and `exec` or `antrun` steps that run `node`, `npm`, `npx`, `pnpm`, `yarn`, `bun` or
  `deno` are refused, because they run a Node other than the one the checks use. Each node project declares
  `@airness/node` as a development dependency, installed by its version, and in `verify` each of its gates runs
  in every declared project and in each member of a declared pnpm workspace: `wiring` (the lockfile resolves
  `@airness/node` at the version of the module's `airness-parent`, never from a local path, with no unapproved override,
  patch, package extension or pnpmfile hook, and `vitest`,
  `@vitest/coverage-v8` and `typescript` at the versions the harness runs), `types` (the project's `tsconfig.json` extends
  `@airness/node/tsconfig.json`, sets no compiler option but `paths`, `baseUrl`, `plugins`, `types`, `rootDir`
  and `outDir`, keeps the include and exclude the harness names, and type-checks), `biome` (formatting and fast lint under the shipped configuration, with
  no `biome.json` or `.biome.json` below the project root), `oxlint` (native rules, with no project Oxlint configuration),
  `eslint` (type-aware lint under the shipped configuration, whatever the project keeps, with no warning
  allowed and at least one file to lint), `source-rules` (no parent-relative import under a source root, no credential guard that opens when
  the credential is unset), `config-refs` (every carve-out and declared exclusion reaches a file) and
  `javascript-allowlist` (no hand-written JavaScript outside the allowlist), `arch` (no cycle, unresolvable
  import, or production import of a spec or development-only dependency), `type-coverage` (no `any` and no type
  assertion in hand-written logic, and no `typeCoverage` key), `knip` (no unused file, export or dependency),
  `tests` (the member's own Vitest under the shipped configuration, with 80% per-file coverage), `install-scripts`
  (`allowBuilds` in `pnpm-workspace.yaml` decides every dependency that runs an install script, and only those;
  `node-dependencies` installs with every script off and then runs only the approved ones) and `release-age`
  (`minimumReleaseAge` of at least 1440 with `minimumReleaseAgeStrict: true`, and no resolved release younger
  than a day by the registry pnpm fetched it from, read on every run, and no git, tarball or directory
  resolution), and the facets, each not applicable where a member lacks it: `css` (Stylelint and SCSS formatting
  over authored stylesheets, with no project Stylelint configuration) and `tailwind-tokens` (no arbitrary
  Tailwind value in a member that declares `tailwindcss`). `tests` also fails a spec outside
  `tests/**/*.spec.ts` and, in a browser application, `tests/e2e/**/*.e2e.spec.ts`. `npm-inventory` writes the npm
  dependency evidence in `prepare-package` and judges nothing. In `package`, every npm package it lists that
  this host installed answers to the license allowlist the Maven artifacts answer to, read as its SPDX
  identifiers, every package it lists to the npm blocklist, every direct dependency but the harness packages
  to the freshness bound, read from the registry pnpm installs it from with its configured token, and a node
  project without that evidence fails. The pinned Node answers to the bound in long-term-support lines, and
  pnpm to the ordinary bound. Extended verification adds `npm-sbom` (a CycloneDX
  document beside `dependencies.json`), and for a member that declares `next` `build` and `bundle` (gzipped
  client JavaScript within 1100 KiB), and for a browser application `e2e`: the harness's accessibility, layout and
  security-header specs and the member's own end-to-end specs under the shipped Playwright configuration. A
  browser application that is not a Next.js application declares the script that serves it as `serve`. A
  Next.js application gets the React and Next.js lint rules and Biome domains, `arch` keeps `src/app` on top, the
  pages the crawl renders and `src/proxy.ts` are left to `e2e` rather than to the coverage floor, and the assets
  goal seeds `src/proxy.ts`. A node project declares only what
  the harness cannot know, under the `airness` key of its `package.json`: further `sourceRoots`, `serve`,
  `serverUrl`, `clock`, `auxiliaryServers`, lower `bundleBudgetBytes` and `accessibilityRouteBudget`, and `generatedArtefacts`, `frameworkGlue`,
  `coverageExclude`, `pureLogicRoots`, `accessibilitySkipRoutes` and `javascriptAllowlist` entries that each name a `pattern` and a
  `reason`, an allowlist entry also the `runtime` of `node` or `cloudfront-js-2.0` that runs it. Diagnose one
  finding with `pnpm exec airness-node gate <id> --verbose`, which gives no verdict.
- **Maven repository routing:** Airness owns `.mvn/maven.config`, `.mvn/jvm.config` and `.mvn/settings.xml`,
  since a `-D` option in the JVM options would set a property such as `skipTests` for every build. The startup
  configuration loads the latter as Maven's global settings and mirrors every remote repository to
  Maven Central, including repositories introduced by dependency POMs and plugin POM inspection.
  The local Maven cache and locally installed snapshots remain available. Remote snapshots and
  artifacts absent from Central are not supported by this policy. Deployment destinations are separate
  from download mirrors. User settings retain credentials and proxies, but may not add or replace mirrors.
  The mandatory `repository-policy` preflight checks the three files, the selected global settings and the
  effective mirror and resolver. Neither `-DskipTests`, report-only mode nor `airness.assets.unmanaged`
  exempts these files or this check. Run `mvn airness:assets-sync` to adopt an update, then restart Maven.
  If missing startup settings prevent Maven from starting, run
  `mvn -gs '${maven.conf}/settings.xml' airness:assets-sync` to restore them, then remove the override.
  The managed startup files apply before parent and plugin resolution. Preflight detects overrides
  when the lifecycle reaches `validate`; it cannot undo earlier downloads in an overridden invocation.
  Initial adoption, explicit repair and standalone third-party goals do not provide an Airness verdict.
- **Dependencies:** explicit scopes, exactly named versions, no project-declared repositories or system paths,
  released dependencies for a released project, one version and one owning artifact per class, unused dependencies,
  declared mocking libraries, licenses, known vulnerabilities, available stable package and container-image updates,
  container tag-digest drift, and the maximum permitted coordinate freshness gap. Some software is refused by name
  whatever license its own manifest declares, because the license that matters belongs to a server it exists to
  reach, to a binary it downloads, or to a container image, which has no manifest at all: the MongoDB server and its
  drivers, starters and embedded forms, Redis images from 7.4, the Elastic images from 7.11 with their clients and
  APM agent, MinIO, LocalStack, the Bitnami catalogue, CockroachDB from 24.3, the HashiCorp tools under the Business
  Source License, Redpanda, the Confluent services under the Confluent Community License, Couchbase, Camunda 8,
  the Timescale images without an `-oss` tag, self-hosted Sentry from 23.11, the proprietary database servers and
  brokers with the drivers that reach only them, Oracle JDK and Oracle GraalVM whether an image ships them, a
  workflow or `.sdkmanrc` installs them, or the build runs on them, the Selenium images that ship Chrome or Edge,
  the commercial Vaadin, Liquibase, Flyway and Hazelcast artifacts, the Develocity extension, iText, Ghostscript,
  MuPDF, the Gatling Highcharts module, and the AGPL services with a permissive equivalent, among them the Grafana
  stores, k6, Plausible, Metabase, Garage and Citus. The check reads the raw `pom.xml` of every module including
  inactive profiles and build extensions, the resolved set, `.mvn/extensions.xml`, every Dockerfile, compose file,
  workflow and composite action, and every Testcontainers literal in the sources, and a refusal names the
  replacement. Where a project turned non-open at a known release the refusal starts there, a tag that cannot be
  placed against that release is refused rather than guessed, and the freshness bound never demands an upgrade into
  a refused release. Every container image the repository names is pinned to a tag or a digest: `latest` or no tag
  lets what is pulled change without the repository changing. There is no setting that widens or narrows the list.
  Compose images remain in scope beside `build` unless a literal service-level `pull_policy: build`
  establishes local output. External Dockerfile `COPY --from` references are read alongside base images;
  stage names and numeric stage indexes identify local stages rather than registry images.
- **Software bill of materials:** every module generates `target/bom.json` during `package`, using CycloneDX 1.6
  JSON and the configured build directory. It lists direct and transitive compile, provided and runtime dependencies,
  including optional dependencies, and excludes test dependencies. Maven installs and deploys it with the `cyclonedx`
  classifier. POM modules generate their own inventory too. No timestamp or serial number varies between builds.
  Generation and its prerequisites remain mandatory with `-DskipTests` and report-only mode. Native skip flags and
  child replacement configuration cannot disable the SBOM. This is the Maven dependency graph, not a container or
  distribution-file inventory. Publication checks include the SBOM beside the POM and any required JARs.
- **Repository assurance:** secret scanning, Qodana analysis, the ceiling on how many suppressions the repository
  holds, complete Git history, commit-message policy, commit typography, linear history, and history-wide
  compliance.
- **Scripts and infrastructure:** Extended verification runs ShellCheck over supported shell entry points and their
  sourced repository libraries. The declared shell determines the dialect. Default findings and the compatible
  optional checks are errors. Checkov reads supported infrastructure and CI inputs through their own framework,
  so application settings and analyzer configuration are not treated as cloud templates. The fleet policy keeps
  security requirements and omits requirements for a particular deployment architecture, availability target,
  commercial security service, or signing product. A CLI container needs no health endpoint. A release workflow
  may declare manual inputs. Extended verification also lints every Dockerfile with hadolint at its default
  info threshold, loads every compose project with `docker compose config`, and lints every workflow with actionlint.
  These checks run before Qodana, from staged copies of the committable tree. Each copy is staged in
  `~/.cache/airness/scan/<tool>-<number>`, beneath the home directory because Docker mounts a folder it does not
  share as an empty one, and Colima shares only the home directory by default. A scan that passes deletes its
  copy. A finding or a failure keeps it, with the scanner output beside it, and prints
  `Scanner evidence: <path>`. `mvn clean` does not remove a kept copy, so delete it by hand once it has been read.
  Missing tools, unreadable inputs, incomplete reports and parsing failures fail verification.

Do not treat a domain omitted from one command's output as ungoverned. The parent POM, bundled configurations, and
Airness goals together define the executable contract.

### Commit Messages

Exact analyzer rules stay in the executable harness. This domain is the one exception. No format command repairs a
commit message, no source file holds it, and a published one cannot be rewritten. An agent satisfies the policy only
while writing the message, so the policy is declared here in full.

| Rule | Value |
| --- | --- |
| Header | `type: subject` or `type(scope): subject`, and nothing after the type but the scope |
| Type | one of `build`, `chore`, `ci`, `docs`, `feat`, `fix`, `perf`, `refactor`, `test` |
| Scope | optional, and when present drawn from `a-z`, `0-9`, `.`, and `-` |
| Subject | 15 to 72 characters, counted after the `: ` that closes the header prefix |
| Subject ending | no trailing period |
| Junk words | `wip`, `tmp`, `temp`, `misc`, `stuff`, `asdf`, `fixup`, as whole words anywhere in the header |
| Body | required when the commit changes more than 2 files or more than 50 added plus deleted lines |
| Body separator | everything after the first line, stripped, and a blank line after it is the Git convention |
| Typography | plain ASCII only: no em dash, en dash, one-character ellipsis, or curly quotation mark |
| Attribution | no marker naming an AI agent or an agent session, in any commit |

No commit shape is exempt from the table above, including the headers Git writes by default for a merge
and for a revert. Every tool that makes one of those offers to edit the message, so a conforming message
is always reachable: a revert says what it undid and why, like any other change. A breaking change is
described in the body rather than marked in the header, because a marker a reader has to notice says less
than a sentence saying what breaks. The attribution ban binds in prose as well as by pattern: a marker
that no pattern yet knows is still a violation.

Four traps account for most failures:

- The junk-word scan reads the whole header, so `chore(temp): rotate the build cache keys` fails on its scope.
- The 15-character floor rejects a short subject such as `fix typo`.
- The body threshold counts added and deleted lines together, so a routine change crosses it quickly.
- The attribution and typography scans read the body as well as the header, so a signature or an em dash in an
  explanation fails exactly as it would in a subject.

Check a commit as soon as it is recorded, with
`mvn airness:commit-history airness:commit-typography airness:linear-history`. All three goals read the repository
rather than the module, so none of them needs a build, and an unpublished commit can still be amended.

### Assertions

The harness checks that every test reaches an assertion and that no assertion compares one written-out value with
another. Neither check decides the question those two stand in for, which is whether the assertion would notice if
the behaviour went missing. That half binds in prose: a test fails when the behaviour its name states is removed
from the code under test. Write each test so that it would, and read it back that way before finishing.

A test helper that asserts carries a name starting with `assert` or `verify`, such as `check.assertSingleAt(...)`.
An assertion is recognized by that prefix wherever the helper is declared. A helper named otherwise is followed at
most within its own file, so a test that reaches its assertion only through such a helper in another type reports
as unproven. Rename the helper or move the assertion into the test's own source rather than suppressing the
finding.

### History Shape

The history is linear, so a merge commit is prohibited wherever it sits, including between two side branches. A
branch takes the work of another by `git rebase` or `git cherry-pick`. The check counts the parents Git recorded
rather than reading the header, so a subject that mentions merging is an ordinary commit, and rewording a merge does
not hide it.

The rule reaches back to the first commit and has no exception. Avoid a merge rather than plan to repair one: once
it is published, only a fresh history satisfies the rule.

## Layer 4: Command Workflow

1. Read `airness.package.root` from the root `pom.xml` before adding or moving Java packages.
2. Run `mvn airness:assets-sync` only to create or restore managed repository files, then review and commit its edits.
3. Run `mvn process-resources -Pformat` to apply formatting and import order, then review the resulting source.
4. Run `mvn clean verify` for Default verification. Verify rather than package, because a project that repackages
   its archive produces the archive that ships during package, and the artifact-content check reads it afterwards.
5. Run `mvn clean verify -Pextended` before finishing. Extended verification includes Default verification and adds
   the known-vulnerability scan, the HTML/PDF compliance report, and the history, secret, and Qodana checks. Default verification alone never reads the
   vulnerability database. Maven resolves effective plugin dependencies from the clean and deploy lifecycles,
   requested goals, and project build extensions, including plugin dependency overrides. Unused lifecycle defaults
   enter the scan when requested. A full Airness reactor build collects its own plugin inputs in the parent module.
   Confirmed incorrect product or version matches have narrowly scoped, dated harness exceptions; project exceptions append
   to them, and unused exceptions fail verification. Never pass `-o` to this command, which refuses an offline build outright. Default verification
   also needs online Maven mode from `prepare-package` onward to generate its SBOM; earlier phases retain their
   existing offline behavior.

### Browser Verification

Default verification opens no browser. Extended verification runs the complete browser sweep, including
Chromium, Firefox and WebKit, alongside the security-header, stack-layer and project specifications. Prepare
the Playwright image matching the node harness's pinned `@playwright/test` before the run. A missing image
fails with its pull command. The host Chromium used for compliance reports is a separate toolchain.

`mvn clean verify -Pextended,delta` runs the Extended checks with the automatic sweep restricted to affected
routes and stories in Chromium. The delta profile requires Extended and cannot be used for a release. It
never narrows the project's or a layer's own specifications. Its result and report explicitly state the scope,
and its report is a draft rather than a complete Extended verdict. Use unscoped Extended verification for a
release and a full assessment. `pnpm exec airness-node scope --json` explains the selected targets without
running verification. The baseline is the remote default branch's merge-base; committed, staged, unstaged and
untracked changes all count. Unknown baselines and global inputs broaden the sweep, while unreadable graphs
fail. No pass or scope is cached.

The node setting `clock` selects an ISO instant or `real`; by default the build, application, test worker and
browser start at `2100-01-01T12:00:00.000Z`. Application clocks advance normally; browser Date stays fixed
while timers and animation clocks continue. `auxiliaryServers` names the command and URL
of each server the application needs before it starts, with an optional member-relative `cwd`. The sweep
finds Storybook stories on those servers. The node harness owns the general browser rules; the installed
Payload layer supplies its admin-view content selectors through `airness.layer.browser`.

Resting and reflow measurements keep motion reduced and animations still. Keyboard and disclosure checks
also run with normal motion, preserving transitions and animations. They verify that activation opens the
disclosure and that focus becomes perceivable, including ancestor opacity, clipping and inert state. Pointer
geometry and pixel-contrast comparisons remain in the still passes. Text clipped by a non-scrollable ancestor
is a finding; inactive subtrees and text deliberately outside a scrollable region are distinguished from it.

Authored Next.js `not-found` boundaries are scanned through their real application layouts, with concrete
locale and dynamic prefixes taken from discovered routes. The expected top-level document 404 is allowed;
failed subresources remain findings. A boundary with no resolvable public prefix fails discovery.

A route delegated to project specifications needs a completed `expectSweptPage(page)` scan from
`@airness/node/a11y` in the current run. The helper records evidence only after every required viewport passes
and the URL remains unchanged. Discovery clears earlier evidence before dependent specifications start;
teardown checks it after they finish. A source-level call that never executes fails as `route-unexecuted`.
Project and layer specifications depend on discovery. Playwright's `--no-deps` is available for a focused
diagnostic, which establishes no complete sweep verdict.

`expectPerceivable(control)` from `@airness/node/a11y` checks painted visibility within 1.5 seconds, without
moving focus or scrolling on the test's behalf. Use it for time-sensitive keyboard regressions.

`mvn airness:assets-init` synchronizes managed files and creates absent optional scaffolds, including a nightly
and release full-verification workflow in a repository with node projects. Scaffolds belong to the project:
ordinary `assets-sync` and verification neither require nor recreate them. Customize the workflow's services
and environment, or delete it when verification runs elsewhere.

### Node Application Preparation and Security Corrections

A node project may declare `airness.pretest` and `airness.testWrapper` as arrays of literal command
arguments in its root `package.json`. The preparation runs before tests, production builds and browser
verification. The wrapper receives the pinned Node command and its arguments, so it can provide a disposable
database or another isolated environment. Its failure and the actual child command's failure both fail the
gate; a wrapper that never completes the requested command fails too. Malformed arrays are refused.

Vitest collects TypeScript and TSX specifications. Server integration specs run in Node, component specs in
Happy DOM with native dialog and fetch support, and browser-application unit specs retain their jsdom environment. Application suites and wrapped suites
run files serially. The harness network guard loads before an optional project `vitest.setup.ts`, whose imports
therefore register matchers and property-test settings against the project's own dependencies.
An optional `vitest.global-setup.ts` runs in Vitest's Node host, after the same network guard, and may
start real local services for component tests. Use Vitest's provided context to share their addresses and
return a teardown that closes them; server code stays out of the jsdom realm.

`airness-node format` applies the same member boundaries, generated-file exclusions and configurations as the
Biome, ESLint and stylesheet gates. Review its edits before running Maven verification.

Only exact security replacements approved by the installed harness are allowed under `overrides` in
`pnpm-workspace.yaml`. The generic catalogue belongs to Airness; the trusted Payload layer supplies its own
`airness.layer.securityOverrides`. Each approval names a parent at an exact version, its dependency, the exact
patched version, the advisory identifiers, its reason and date. The workspace declaration and lockfile must
agree, every selected dependency edge must resolve to that version, and an unused override fails. Arbitrary
replacements, aliases, patches, package extensions and pnpmfile hooks remain forbidden.

Until the harness npm packages are published, the managed `.airness/npm-registry.py` helper can serve their
installed Maven tarballs from a temporary loopback registry. Bind its startup and readiness checks at validate,
write its endpoint only to an ignored `.npmrc`, and keep its lifetime inside the Maven process. Other packages
retain their public registry metadata and release-age checks. Only the unpublished harness packages may be
excluded from the age floor.

A root node project may record `airness.vulnerabilityAllowlist` entries with `package`, `advisory`, `reason`,
`mitigation` and `addedOn`. Name one exact npm package and one literal advisory identifier or an alias OSV
supplies, explain why it cannot be exploited here, state the removal plan, and use a YYYY-MM-DD calendar date.
Prefer an available, compatible upgrade or an approved security replacement. An exception belongs only to that
node project's inventory: it cannot excuse another module or frontend. Missing fields, malformed declarations,
unused entries and overlapping entries fail verification. The report keeps each mitigated advisory and its
explanation, while any remaining open finding still fails. A scanner that cannot run cannot be waived.

### Compliance Reports

Extended verification writes one HTML and PDF compliance report for the participating reactor under the
root project's configured build directory, in `compliance/`. It embeds one SBOM: every module's Maven
artifacts and build tools, every node project's npm packages, and the Node and pnpm that ran the node gates,
joined in one dependency graph. Every npm package is looked up in the OSV database, and an advisory it lists,
or an OSV that can't be reached, fails Extended verification. The common filename is
`oss-compliance-report-<root-artifactId>-<12-character-commit>`. Both formats are required, including
for plain Java projects. Default verification produces no compliance report.

Install the Chromium revision this Airness version pins with `mvn airness:report-browser-install`.
Linux also needs Chromium's system libraries. The renderer uses this browser's separate cache, not the
project's browser-test dependencies or a workstation's interactive Chrome profile. Both installer and
renderer explicitly use `~/.cache/airness/report-browser/<Playwright version>`, independently of an inherited
`PLAYWRIGHT_BROWSERS_PATH`. Report rendering reads no remote fonts or scripts. Missing browser tooling and incomplete evidence fail generation.

Run `mvn -Pextended airness:compliance-report` to regenerate the prerequisite package-phase evidence
and the report outside full verification. This command establishes the dependency-policy result, not
an Extended verification pass. `-Dairness.compliance.output=<directory>` changes the destination only;
it must be ignored by Git or outside the repository and may not traverse a symbolic link.

The first page states the verdict as checked or failed statements, without calling the project compliant, and
lists the contents. The report then lists every vulnerability the scan read with its scope and status, open or
mitigated, and prints a mitigation plan for each mitigated one from its suppression entry. It follows with the
license classes, every weak copyleft or refused artifact with its uses, the inherited license policy, and the full
inventory: every resolved artifact and its declaring modules, native Maven scopes and direct or transitive use,
including test dependencies and resolved build tools. It ends with the textual license and notice entries from
production dependency archives. Identical texts are grouped with their artifact attribution. Provided dependencies
are identified separately from compile and runtime dependencies.

The HTML embeds an aggregate CycloneDX inventory and a CSV inventory for download. Published per-module
`bom.json` files retain their existing production dependency scope. The report records its source
commit, working-tree fingerprint, evidence hashes and scan dates. It refuses missing or stale evidence
and a tree that changes during generation. A dirty tree, selected reactor subset, policy failure or
report-only invocation produces an explicit draft. `-DskipTests` produces no new report or verdict.
Earlier report pairs are removed only after a complete replacement has been rendered.

## Layer 5: Exceptions and Repairs

- Fix a managed-file finding with `mvn airness:assets-sync`. A seed's contents belong to the project after creation,
  but its path remains mandatory. A project may take over an eligible path through `airness.assets.unmanaged` only
  when the assignment explicitly requires it and the project records the reason.
- Fix formatter findings with the format command. Modernization findings name the form to write, and no
  command writes it for you. Fix compilation, analysis, dependency, test, coverage,
  history, security, and tool failures at their source; do not disable the failing check.
- Change an exclusion or the default test timeout only for an explicit project requirement. Never use one merely to
  obtain a pass.
- The test order seed and the suppression ceiling are the harness's own and take no project setting. Answer a
  suppression-ceiling finding by removing a suppression, and never by trying to raise the ceiling.
- There is no setting that turns a check off. If a check is wrong for the project, that is an Airness bug to be
  fixed in Airness, not a local override.
- ShellCheck, Checkov, hadolint and actionlint accept no project-owned suppression or replacement configuration. Their image pins
  and policy are carried in the installed Airness artifacts, so even a command-line image override cannot replace
  them. A ShellCheck source hint must identify the actual repository import, never an empty substitute. Report a
  false finding to Airness.

### Change Requests

A project cannot repair the harness from inside itself, and that is the design rather than a gap in it: a rule a
project can edit is not a rule. What follows from it is that a rule improves only when somebody reports it, which
makes the report part of the contract rather than a courtesy.

Raise it with the Airness maintainers when:

- a check blocks work that is correct, and the only way past it is a suppression or an exclusion;
- the same finding is repaired the same way in project after project, which is a rule that belongs in the harness
  rather than in each project's habits;
- a project-owned check turns out to be true of every project of this kind, and should move into the harness;
- a message names a defect without naming the repair, or names the wrong repair.

Bring the failing output, the smallest case that reproduces it, and what the rule should have said. A suppression to
get past a check is a local answer to a general problem: take it when the work cannot wait, and report it either
way, because the next project meets the same check with no memory of this one, and a suppression left in place
spends a ceiling the next repair will need.

### Project Settings

| Setting | Use |
| --- | --- |
| `airness.package.root` | the prefix of every production and test package, and the only required key |
| `airness.assets.unmanaged` | repository paths the project takes over from the harness |
| `airness.node.projects` | the module's node project directories, comma-separated, and none unless set |
| `airness.generated.files` | further repository paths a generator owns, comma-separated, denied to agents and skipped by the typography and `.editorconfig` checks |
| `airness.typography.excludes` | repository path prefixes the typography scan skips, never all of them; generated files are skipped without it |
| `airness.coverage.excluded.classes` | qualified class patterns the coverage floors skip, never all of them |
| `airness.test.timeout` | the ceiling on one test, and `30 s` unless set |
| `airness.dependency-check.suppression.file` | a local OWASP suppression file, and the path below unless set |

Those eight are the whole of what a project file may declare. Every other name under `airness.`, and `skipTests`,
`maven.test.skip`, `test`, `it.test`, and `jacoco.dataFile`, is refused there, because each of them can decide a verdict. The refusal
reads the file as written, so a name inside a profile that is never activated is refused too. Both
`-Dairness.enforce=false` and `-DskipTests` are command-line flags and are never written into a project file.
Raw-model integrity checks read the current POM and every consumer parent up to the trusted Airness chain,
even with `-DskipTests`; skipping execution cannot hide a forbidden committed property or inherited override.

A property naming a version is refused where nothing in the same file reads it. The platform reaches a project as a
bill of materials the parent imports rather than inherits, and an imported one resolves its own properties, so a
`tomcat.version` written beside the settings above moves no version at all and leaves the file claiming a pin that is
not there. Manage the artifact in a `dependencyManagement` block of the project's own and version it from the
property, which is what makes the property the pin it reads as.

Reach for the suppression file only when no upgrade answers an advisory. Keep it at
`.airness/dependency-check-suppressions.xml`, which is the path the scan reads: the document's presence there is what
puts it in front of the scan, so one kept elsewhere is never read and the setting itself needs no declaring. Every
rule in it names the advisory it excuses and carries `notes` with three parts. The notes say why the vulnerability
can't be exploited in this project: name the path that reaches the artifact, whether it runs in production or only in
tests and the build, and why its input can't trigger the defect. After a `Mitigation:` marker they state the
mitigation plan: what limits the risk now and what will remove the artifact, such as the upstream release or the
dependent's upgrade it waits for. They carry a `YYYY-MM-DD` date. A rule that suppresses nothing fails the build, like
every other exclusion that reaches nothing.

```xml
<suppress>
    <notes><![CDATA[
        Added 2026-01-15. example-parser is reached only through example-bundler, which parses the project's own
        build configuration during the build. The defect needs attacker-supplied input, which that configuration isn't.

        Mitigation: example-bundler never runs in the deployed application. The entry is removed once
        example-bundler releases a version that drops example-parser.
    ]]></notes>
    <packageUrl regex="true">^pkg:maven/org\.example/example-parser@.*$</packageUrl>
    <cve>CVE-2026-0001</cve>
</suppress>
```

The compliance report prints the reason and the mitigation plan to a reviewer outside the project, so write both
as complete sentences that stand on their own.

Each exception names non-empty literal `cve` or `vulnerabilityName` values. Advisory-name regexes,
product (`cpe`) and weakness (`cwe`) selectors, and CVSS score thresholds are refused, even beside a
named advisory. Dependency selectors (`packageUrl`, `gav`, `filePath` and `sha1`) remain available,
including their supported regex forms, and a dependency selector is not mandatory. Entries in groups
and entries using namespace prefixes are checked too. Each entry carries its own explanation, mitigation plan and
date.
