# Airness Agent Guide

This guide maps the governing standard, `README-guideline-software-project.adoc`, to the Airness harness for every
repository, whatever stacks it holds. The guide of each stack layer present adds to it, and the managed blocks of
`AGENTS.md` name them. After them, follow the remaining steps of `AGENTS.md` in the order it declares. Exact
analyzer rules remain in the executable harness.

## Harness Invariants

- Each module inherits the Airness parent for what it contains: `airness-parent` for a monorepo root or a module
  with no code, `airness-parent-java` for Java, `airness-parent-spring-boot` for a Spring Boot project, or
  `airness-parent-node` for a module whose main content is JavaScript or TypeScript. That
  choice is the only place a module states which kind it is, and it is what decides which rules apply.
- Use exactly Java 25, Maven 3.10.0 or later, and a Git working tree. Default verification reads Maven Central and
  Docker Hub and fails when either cannot be read, and Extended verification also needs a reachable Docker daemon
  that can read the repository through a bind mount. Extended verification refuses to start under `-o` or offline
  mode, because Maven skips a goal whose descriptor requires online mode, the vulnerability scan is one of those,
  and an offline run would otherwise report clean without having read a single advisory. Default verification
  also needs online Maven mode from `prepare-package` onward, because Maven otherwise skips SBOM generation,
  including with `-DskipTests`; earlier phases keep their offline behavior. On macOS with Colima, a
  repository under Downloads, Desktop or Documents is unreadable inside a container until the terminal that starts
  Colima is granted access to that folder under System Settings, Privacy and Security, Files and Folders, so keep
  the repository elsewhere or grant it.
- Treat every finding and every tool, setup, or compilation failure as a failed verification. Reporting findings with
  `-Dairness.enforce=false` is not a pass. A build using `-DskipTests` produces no Airness verdict.

## Governed Domains

Airness governs all of the following domains:

- **Toolchain and Maven model:** runtime versions, project and parent coordinates, package ownership, inherited plugin
  ownership, raw-model anti-bypass checks, effective dependency convergence, the ordering and local use of declared
  properties, valid Airness parameters, and current build practices: no Java version below 25 stated in the project
  file, no superseded coordinate, a version on every managed dependency, a groupId on every plugin, `project.`-prefixed
  expressions, the documented order of pom elements, and Java 21 or later in every workflow's `setup-java`.
  Every hosted Ubuntu runner a workflow, a composite action or an Azure Pipelines definition names runs the
  current Ubuntu long-term-support release, named rather than aliased: `ubuntu-26.04`, or `ubuntu-26.04-arm` for
  Arm. `ubuntu-latest` and every earlier release are refused wherever the label is written, a matrix value
  included, and a larger runner whose label carries a release is held to it too. The required release moves to the
  next long-term-support one only once GitHub-hosted runners for it are generally available.
  Every module ends with `airness:module-complete`, the last execution of `verify`: a project binds nothing in
  `verify` and names no goal after it, and either is refused at `validate`. Bind a project step to an earlier
  phase, such as `post-integration-test`, and name a command-line goal before the lifecycle
  (`mvn sonar:sonar verify`) or run it in an invocation of its own. Once every module of the build has
  completed, one closing step compares the committable tree with the one the build started from, holds the
  Java sources and the node projects' own code to their suppression ceilings, and under Extended verification
  writes the compliance report and looks every npm package up in the OSV database. Build Helper and Assembly
  are optional managed plugins: declare one without a version in the module that uses it. Build Helper's source
  and resource roots and Assembly's distribution descriptors belong to the project.
- **Repository files and instructions:** managed, seeded, and forbidden files; the root license file; agent
  instruction files; editor and Git configuration; and an unchanged committable tree during verification. Managed
  files follow what the repository contains rather than which parent a module names: the root carries the files
  of every stack present in any module, `AGENTS.md`, `.gitignore` and `.dockerignore` lead with one marked block
  per stack, the lines below the blocks belong to the project, and each declared node project carries the node
  files in its own directory. No ignore file repeats a pattern an earlier declaration already applies: a line
  equal to an earlier line of its own file, or an un-anchored `.gitignore` line (no slash but a trailing one) equal
  to a line of a `.gitignore` in a directory above, with no negation between them that could reverse it. A
  managed line counts as declared, so remove the project's copy; a Docker ignore file is compared with itself
  alone. The editor
  configuration is held against the files a commit would carry, so what git is configured to ignore is passed over
  rather than reported: build output and a tool's scratch are not content this style governs. Neither is a
  generated file, which is a file a stack layer, `airness.generated.files` or a node project's
  `airness.generatedArtefacts` declares, and which the editor configuration, the indentation check and the
  typography check leave unread. The indentation check holds the rest to the editor configuration too: it reports
  the first line of each file whose leading whitespace is not the kind `indent_style` names, and in a YAML file
  the first nesting step that is not `indent_size` columns wide, and passes over the binaries and tab-separated
  data the file linter exempts.
  Ignored files selected as Java or resource inputs are different: Maven can consume them locally
  while a checkout omits them. The per-module `build-inputs` preflight rejects that mismatch at
  `validate`, `process-resources` and `process-test-resources`, using Maven's roots and file filters,
  and so it does for a script or stylesheet under a checked source root of a declared node project.
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
  denies `Edit(...)` of every generated file and of every pinned managed file, with no local settings allowing
  one back. `mvn airness:assets-sync` writes those rules beside the project's own.
- **Frontend resources:** at `validate`, every module inventories the scripts (`.js`, `.mjs`, `.cjs`, `.jsx`,
  `.ts`, `.mts`, `.cts`, `.tsx`) and stylesheets (`.css`, `.scss`) a commit would carry, whether or not it has a
  `package.json`. Each must lie in a declared node project and under one of its checked source roots (`src`,
  `tests`, `scripts` and `airness.sourceRoots`); a script directly in a project's top directory is a tooling file
  the linters and the type check read. `.sass` and `.less` files are refused because no gate reads them. Only a
  generated file or a file the harness places is passed over; a `vendor` directory or a minified name is not evidence of third-party ownership, so take
  a distribution from a dependency (a WebJar, or an npm package a `node-run` build copies under `target/`). An
  ignored input is the concern of `build-inputs` under Repository files and instructions.
  A tooling project with stylesheets and no script of its own needs no TypeScript, no tests and no server: the
  script gates report not applicable and the `css` gate judges the stylesheets.
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
  no `biome.json` or `.biome.json` below the project root, passing over Thymeleaf templates, which the Java template
  checks and the browser sweep judge), `oxlint` (native rules, with no project Oxlint configuration),
  `eslint` (type-aware lint under the shipped configuration, whatever the project keeps, with no warning
  allowed and at least one file to lint), `source-rules` (no parent-relative import under a source root, no credential guard that opens when
  the credential is unset), `config-refs` (every carve-out and declared exclusion reaches a file) and
  `javascript-allowlist` (no hand-written JavaScript outside the allowlist), `arch` (no cycle, unresolvable
  import, or production import of a spec, test code or development-only dependency), `type-coverage` (no `any`
  and no type assertion in hand-written logic, and no `typeCoverage` key), `knip` (no unused file, export or dependency,
  each judged with every test and tool as an entry point, and also no export, outside the declared public
  interface, and no file under a production source root that only tests and tooling reach; test such a helper
  through the production code that uses it, or remove it; a release tag knip honours, `@public`, `@beta`, `@alias`
  or `@internal`, counts as a suppression),
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
  to the freshness bound, read from the registry pnpm installs it from with its configured token, where a
  release above the one the `latest` distribution tag names is not yet the stable line, and a node
  project without that evidence fails. The pinned Node answers to the bound in long-term-support lines, and
  pnpm to the ordinary bound. Extended verification adds `npm-sbom` (a CycloneDX
  document beside `dependencies.json`), and for a member that declares `next` `build` and `bundle` (gzipped
  client JavaScript within 1100 KiB), and for a browser application `e2e`: the harness's accessibility, layout and
  security-header specs and the member's own end-to-end specs under the shipped Playwright configuration. A
  browser application that is not a Next.js application declares the script that serves it as `serve`. A
  Next.js application gets the React and Next.js lint rules and Biome domains, `arch` keeps `src/app` on top and
  refuses a routes directory (`app` or `pages`) at the member root rather than under `src`, the pages the crawl
  renders and `src/proxy.ts` are left to `e2e` rather than to the coverage floor, and the assets goal seeds
  `src/proxy.ts`. A node project declares only what the harness cannot know, under the `airness` key
  of its `package.json`, through the keys the table Node Project Settings lists. Diagnose one finding with
  `pnpm exec airness-node gate <id> --verbose`, which gives no verdict. The sections Node Application
  Preparation and Security Corrections, Browser Verification and Node Project Settings complete this domain.
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
  stage names and numeric stage indexes identify local stages rather than registry images. The known-vulnerability
  scan reads the effective plugin dependencies Maven resolves from the clean and deploy lifecycles, requested goals,
  and project build extensions, including plugin dependency overrides. Unused lifecycle defaults enter the scan when
  requested. A full Airness reactor build collects its own plugin inputs in the parent module.
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
  retention period, set of log sources, commercial security service, or signing product, and product decisions
  such as an audience restricted by country. A CLI container needs no health endpoint. A release workflow may
  declare manual inputs. Extended verification also lints every Dockerfile with hadolint at its default
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

The section Commit Messages of the governing standard states the policy. A published commit message cannot be
rewritten, so an agent satisfies the policy only while writing the message. Airness adds these details:

| Rule | Value |
| --- | --- |
| Header | nothing after the type but the scope |
| Scope | optional, and when present drawn from `a-z`, `0-9`, `.`, and `-` |
| Subject | its length is counted after the `: ` that closes the header prefix |
| Junk words | matched as whole words anywhere in the header, the scope included |
| Body | everything after the first line, stripped, and a blank line after it is the Git convention |
| Typography | plain ASCII only: no em dash, en dash, one-character ellipsis, or curly quotation mark |

No commit shape is exempt from the policy, including the headers Git writes by default for a merge
and for a revert. Every tool that makes one of those offers to edit the message, so a conforming message
is always reachable: a revert says what it undid and why, like any other change. A breaking change is
described in the body rather than marked in the header, because a marker a reader has to notice says less
than a sentence saying what breaks. No commit message attributes the change to an AI agent or to an AI agent
session, and that ban binds in prose as well as by pattern: a marker that no pattern yet knows is still a
violation.

Four traps account for most failures:

- The junk-word scan reads the whole header, so `chore(temp): rotate the build cache keys` fails on its scope.
- The 15-character floor rejects a short subject such as `fix typo`.
- The body threshold counts added and deleted lines together, so a routine change crosses it quickly.
- The attribution and typography scans read the body as well as the header, so a signature or an em dash in an
  explanation fails exactly as it would in a subject.

Check a commit as soon as it is recorded, with
`mvn airness:commit-history airness:commit-typography airness:linear-history`. All three goals read the repository
rather than the module, so none of them needs a build, and an unpublished commit can still be amended.

### History Shape

The linear-history check counts the parents Git recorded rather than reading the header, so a subject that
mentions merging is an ordinary commit, and rewording a merge does not hide it. Avoid a merge rather than plan to
repair one: once it is published, only a fresh history satisfies the rule.

### Assertions

The harness checks that every test reaches an assertion and that no assertion compares one written-out value with
another. Neither check decides the question those two stand in for, which is whether the assertion would notice if
the behaviour went missing. That half binds in prose: a test fails when the behaviour its name states is removed
from the code under test. Write each test so that it would, and read it back that way before finishing.

### Node Application Preparation and Security Corrections

A node project may declare `airness.pretest` and `airness.testWrapper` as arrays of literal command
arguments in its root `package.json`. The preparation runs before tests, production builds and browser
verification. The wrapper receives the pinned Node command and its arguments, so it can provide a disposable
database or another isolated environment. Its failure and the actual child command's failure both fail the
gate; a wrapper that never completes the requested command fails too. Malformed arrays are refused.

Write the wrapper with `withServices` from `@airness/node/services`, so verifications of several projects, or of
two worktrees of one, can run on one machine at once:

```ts
import { randomSecret, withServices } from '@airness/node/services'

const credential: string = randomSecret()
process.exitCode = await withServices({
  project: 'shop',
  containers: [
    {
      key: 'db',
      image: 'postgres:18-alpine',
      environment: { POSTGRES_DB: 'shop', POSTGRES_USER: 'shop', POSTGRES_PASSWORD: credential },
      ports: ['5432/tcp'],
      ready: ['pg_isready', '--host=127.0.0.1', '--username=shop', '--dbname=shop'],
    },
  ],
  environment: (services) => ({
    DATABASE_URL: `postgres://shop:${credential}@127.0.0.1:${String(services.port('db', '5432/tcp'))}/shop`,
  }),
})
```

Each container is named for the run, published on a loopback port Docker chooses and removed afterwards, also
when the run is stopped by a signal or killed outright. `network: true` gives the containers a private network,
on which `prepare` can run a one-off container with `services.once`; `prepare` also runs steps such as
migrations against the services before the command. Never start fixed-name containers on fixed ports, or reuse a
developer's database, for verification.

Vitest collects TypeScript and TSX specifications. Server integration specs run in Node, component specs in
Happy DOM with native dialog and fetch support, and browser-application unit specs retain their jsdom environment. Application suites and wrapped suites
run files serially. The harness network guard loads before an optional project `vitest.setup.ts`, whose imports
therefore register matchers and property-test settings against the project's own dependencies.
An optional `vitest.global-setup.ts` runs in Vitest's Node host, after the same network guard, and may
start real local services for component tests. Use Vitest's provided context to share their addresses and
return a teardown that closes them; server code stays out of the jsdom realm.

Only exact security replacements approved by the installed harness are allowed under `overrides` in
`pnpm-workspace.yaml`. The generic catalogue belongs to Airness; a trusted stack layer supplies its own
`airness.layer.securityOverrides`. Each approval names a parent at an exact version, its dependency, the exact
patched version, the advisory identifiers, its reason and date. The workspace declaration and lockfile must
agree, every selected dependency edge must resolve to that version, and an unused override fails. Arbitrary
replacements, aliases, patches, package extensions and pnpmfile hooks remain forbidden.

A released harness installs its npm packages from the public registry. A SNAPSHOT harness's packages are never
published, so the managed `.airness/npm-registry.py` helper can serve their installed Maven tarballs from a
temporary loopback registry. Bind its startup and readiness checks at validate,
write its endpoint only to an ignored `.npmrc`, and keep its lifetime inside the Maven process. Given the
project's lockfile with `--lockfile`, it refuses to start when a served tarball's integrity differs from the
one the lockfile records, and names the lockfile-only `pnpm install` that refreshes the lock against it. With
`--refresh-with <pnpm>` and the lockfile, bound with the pinned pnpm at initialize before the frozen install, it
serves nothing and runs that refresh itself on a stale lock or one with merge conflict markers, then fails the
build, which has rewritten a tracked file, so the next build runs on the refreshed lock. With the `CI` variable
set it refreshes nothing and fails. Other packages retain their public registry metadata and release-age
checks. Only the unpublished harness packages may be excluded from the age floor.

## Command Workflow

1. Read `airness.package.root` from the root `pom.xml` before adding or moving Java packages.
2. Run `mvn airness:assets-sync` only to create or restore managed repository files, then review and commit its edits.
   `mvn airness:assets-init` also synchronizes managed files and creates absent optional scaffolds, including a
   nightly and release Extended verification workflow in a repository with node projects. Scaffolds belong to the
   project: ordinary `assets-sync` and verification neither require nor recreate them. Customize the workflow's
   services and environment, or delete it when verification runs elsewhere.
3. Apply formatting, then review the resulting source. In a node project, `pnpm exec airness-node format` applies
   the same member boundaries, generated-file exclusions and configurations as the Biome, ESLint and stylesheet
   gates. The Java layer's guide names the format command of a Java module.
4. Run `mvn clean verify` for Default verification. Verify rather than package, because a project that repackages
   its archive produces the archive that ships during package, and the artifact-content check reads it afterwards.
5. Run `mvn clean verify -Pextended` before finishing. Extended verification is the verification command of the
   governing standard; Default and delta verification run declared subsets of its checks. It includes Default
   verification and adds the known-vulnerability scan, the HTML/PDF compliance report, and the history, secret, and Qodana checks. Default verification alone never reads the
   vulnerability database.

### Browser Verification

Default verification runs no automatic browser sweep; browser tests a project writes itself may run there as
ordinary tests. Extended verification runs the complete browser sweep, including
Chromium, Firefox and WebKit, alongside the security-header, stack-layer and project specifications. Prepare
the Playwright image matching the node harness's pinned `@playwright/test` before the run. A missing image
fails with its pull command. The host Chromium used for compliance reports is a separate toolchain.

`mvn clean verify -Pextended,delta` runs the Extended checks with the automatic sweep restricted to affected
routes and stories in Chromium. The delta profile requires Extended and cannot be used for a release. It
never narrows the project's or a layer's own specifications. Its result and report explicitly state the scope,
and its report is a draft rather than a complete Extended verdict. Use Extended verification without `delta`
for a release and a full assessment. `pnpm exec airness-node scope --json` explains the selected targets without
running verification. The baseline is the remote default branch's merge-base; committed, staged, unstaged and
untracked changes all count. Unknown baselines and global inputs broaden the sweep, while unreadable graphs
fail. No pass or scope is cached.

The sweep applies to every module that serves user-facing HTML, whatever its backend language. The Java layer's
guide states when a Java module is such a web interface, how it is served and where its route inventory comes
from. An inventory route the crawl does not reach needs a completed `expectSweptPage(page)` scan from
`@airness/node/a11y` in a project specification of the current run, which is how authenticated roles and prepared
states are covered, or an `accessibilitySkipRoutes` entry with its reason. The helper records evidence only after
every required viewport passes and the URL remains unchanged. Discovery clears earlier evidence before dependent
specifications start; teardown checks it after they finish. A source-level call that never executes fails as
`route-unexecuted`. Project and layer specifications depend on discovery. Playwright's `--no-deps` is available
for a focused diagnostic, which establishes no complete sweep verdict. A temporary redirect (302, 303, 307)
credits only the page it lands on, so a protected page answered by the sign-in page stays uncovered; a permanent
redirect still credits its source. Same-origin `hx-get`
fragments found on swept and scanned pages are requested with the page's session, must answer HTML, and are
reported as fragment coverage apart from swept pages.

The node setting `clock` selects an ISO instant or `real`; by default the build, application, test worker and
browser start at `2100-01-01T12:00:00.000Z`. Application clocks advance normally; browser Date stays fixed
while timers and animation clocks continue. `auxiliaryServers` names the command and URL
of each server the application needs before it starts, with an optional member-relative `cwd`. The sweep
finds Storybook stories on those servers. In verification, `e2e` keeps the scheme, host and path of
`serverUrl` and of each auxiliary URL but serves on a loopback port chosen for the run, handed to the server as
`$PORT`, so a `serve` script and every auxiliary command listen on `$PORT`, such as
`storybook dev --port $PORT`; the declared port matters only outside verification. Specifications reach the application through
Playwright's `baseURL` or relative URLs, never a written-out origin. An application that builds absolute links
or a list of accepted origins from its own variable names it in `serverUrlVariables`, which the run sets to the
served origin, and reads it at runtime. The node harness owns the general browser rules; an installed
stack layer supplies the content selectors of its framework's views through `airness.layer.browser`.

Resting and reflow measurements keep motion reduced and animations still. Keyboard and disclosure checks
also run with normal motion, preserving transitions and animations. They verify that activation opens the
disclosure and that focus becomes perceivable, including ancestor opacity, clipping and inert state. Pointer
geometry and pixel-contrast comparisons remain in the still passes. Text clipped by a non-scrollable ancestor
is a finding; inactive subtrees and text deliberately outside a scrollable region are distinguished from it.
An image whose source carries more than three pixels per CSS pixel of its box, decoding at least a mebipixel
no screen shows, is `image-oversized`; serve a smaller source, or a `srcset` whose `sizes` match the box.

The scrolling pass crosses one route per template and one story per component on an emulated touch phone with
normal motion, by wheel gestures down to the end and back. The document must come to rest where each gesture
left it (`scroll-hijack`): a page that snaps to sections or runs on past a gesture fails, while sticky pinning
and scroll-driven animation, which leave the scroll position alone, pass. A heading, paragraph, quotation or
caption not yet seen visible must not stay fully transparent 1.5 seconds after the document rests with it in
the upper half of the view (`unrevealed-content`). It reports wheel gestures, not swipes; a project's own specifications cover its touch
gestures and any reveal timing tighter than that bound.

Authored Next.js `not-found` boundaries are scanned through their real application layouts, with concrete
locale and dynamic prefixes taken from discovered routes. The expected top-level document 404 is allowed;
failed subresources remain findings. A boundary with no resolvable public prefix fails discovery.
An application with several root layouts answers an address none of them claims with `global-not-found`,
which is scanned at the root; without it Next.js serves its bare built-in page, and that page is judged.

`expectPerceivable(control)` from `@airness/node/a11y` checks painted visibility within 1.5 seconds, without
moving focus or scrolling on the test's behalf. Use it for time-sensitive keyboard regressions.

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
and the report outside Extended verification. This command establishes the dependency-policy result, not
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

## Exceptions and Repairs

- Fix a managed-file finding with `mvn airness:assets-sync`. A seed's contents belong to the project after creation,
  but its path remains mandatory. A project may take over an eligible path through `airness.assets.unmanaged` only
  when the assignment explicitly requires it and the project records the reason.
- Fix formatter findings with the format command of step 3 of the Command Workflow. Modernization findings name the form to write, and no
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
- A configuration file a pinned tool would read from the tree on its own is refused on every build, tracked or not:
  the scanners' native files, `.gitleaksignore`, which gitleaks reads whatever its flags say, and the
  `junit.jupiter.execution.timeout.*` keys of a `junit-platform.properties`, which the parents pin as launcher
  parameters. A fixture exception belongs in `.gitleaks.toml`, and the rest of `junit-platform.properties` stays the
  project's. That exception names its `targetRules` and writes the whole fixture as `'''^value$'''`, a literal
  string matched against the secret, because gitleaks excuses every finding that contains an unanchored value and
  decodes the escapes of a double-quoted one before compiling it.

### Vulnerability Exceptions

Reach for the OWASP suppression file, which `airness.dependency-check.suppression.file` locates, only when no
upgrade answers an advisory. Keep it at
`.airness/dependency-check-suppressions.xml` in the directory of the module whose dependency it excuses, which is the
path the scan of that module reads: the document's presence there is what puts it in front of the scan, so one kept
elsewhere is never read and the setting itself needs no declaring. In a multi-module build a document at the
repository root excuses nothing in a child module. Every rule in it names the advisory it excuses and carries `notes`
with three parts. The notes say why the vulnerability
can't be exploited in this project: name the path that reaches the artifact, whether it runs in production or only in
tests and the build, and why its input can't trigger the defect. After a `Mitigation:` marker they state the
mitigation plan: what limits the risk now and what will remove the artifact, such as the upstream release or the
dependent's upgrade it waits for. They carry a `YYYY-MM-DD` date. A rule that suppresses nothing fails the build, like
every other exclusion that reaches nothing. The harness carries its own narrowly scoped, dated exceptions for
confirmed incorrect product or version matches, and the project's rules add to them.

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

Each `airness.vulnerabilityAllowlist` entry carries `package`, `advisory`, `reason`, `mitigation` and `addedOn`.
Name one exact npm package and one literal advisory identifier or an alias OSV supplies, explain why it cannot be
exploited here, state the removal plan, and use a YYYY-MM-DD calendar date. Prefer an available, compatible
upgrade or an approved security replacement. An exception belongs only to that node project's inventory: it
cannot excuse another module or frontend. Missing fields, malformed declarations, unused entries and overlapping
entries fail verification. The report keeps each mitigated advisory and its explanation, while any remaining open
finding still fails. A scanner that cannot run cannot be waived.

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

## Settings

### Project Settings

| Setting | Use |
| --- | --- |
| `airness.package.root` | the prefix of every production and test package, and the only required key |
| `airness.assets.unmanaged` | repository paths the project takes over from the harness |
| `airness.node.projects` | the module's node project directories, comma-separated, and none unless set |
| `airness.generated.files` | further generated files, comma-separated |
| `airness.typography.excludes` | repository path prefixes the typography scan skips, never all of them; generated files are skipped without it |
| `airness.coverage.excluded.classes` | `pattern -- reason` entries, separated by semicolons, for the classes the coverage floors skip, never all of them; each counts against the suppression ceiling |
| `airness.test.timeout` | the ceiling on one test, and `30 s` unless set |
| `airness.dependency-check.suppression.file` | a local OWASP suppression file, and the path Vulnerability Exceptions names unless set |

Those eight are the whole of what a project file may declare. Every other name under `airness.` is refused there, and
so is every property a goal the parents bind reads in place of a parameter that decides a verdict, such as `skipTests`,
`maven.test.failure.ignore`, `checkstyle.maxAllowedViolations` or `license.excludedGroups`, because Maven fills an
unset parameter from that property without the plugin being declared. A property that only tunes how a plugin the
project may configure runs, such as `argLine`, stays the project's. The refusal
reads the file as written, so a name inside a profile that is never activated is refused too. Both
`-Dairness.enforce=false` and `-DskipTests` are command-line flags and are never written into a project file.
Raw-model integrity checks read the current POM and every consumer parent up to the trusted Airness chain,
even with `-DskipTests`; skipping execution cannot hide a forbidden committed property or inherited override.

A property naming a version is refused where nothing in the same file reads it. The platform reaches a project as a
bill of materials the parent imports rather than inherits, and an imported one resolves its own properties, so a
`tomcat.version` written beside the settings above moves no version at all and leaves the file claiming a pin that is
not there. Manage the artifact in a `dependencyManagement` block of the project's own and version it from the
property, which is what makes the property the pin it reads as.

### Node Project Settings

A node project declares what the harness cannot know through these keys under `airness` in its `package.json`:

| Setting | Use |
| --- | --- |
| `sourceRoots` | first-party source directories beyond `src`, `tests` and `scripts` |
| `generatedArtefacts` | files a generator writes, which no gate judges |
| `frameworkGlue` | files a framework writes to a fixed shape, which the lint relaxes |
| `javascriptAllowlist` | JavaScript files a tool forces on the project, each with the `runtime` of `node` or `cloudfront-js-2.0` that runs it |
| `coverageExclude` | source files the coverage floor doesn't measure; each entry counts against the node suppression ceiling |
| `pureLogicRoots` | directories of pure logic, which `arch` forbids to import anything else under a source root |
| `productionEntries` | files that run in production though no entry point knip reads reaches them, such as a script `start` runs or a site build that renders a story; never a test, and no suppression |
| `accessibilitySkipRoutes` | route prefixes the sweep doesn't follow |
| `serve`, `serverUrl`, `serverUrlVariables`, `auxiliaryServers`, `clock` | how a browser application is served and the clock it starts at, as Browser Verification describes |
| `bundleBudgetBytes`, `accessibilityRouteBudget` | lower ceilings for the client bundle and for the routes the sweep discovers |
| `layoutMinimumGap`, `layoutViewports` | a larger gap between boxes, and further viewports the layout scan measures |
| `pretest`, `testWrapper` | the preparation and the wrapper that Node Application Preparation and Security Corrections describes |
| `vulnerabilityAllowlist` | npm advisories that can't be exploited here, in the root node project only |

Each `generatedArtefacts`, `frameworkGlue`, `javascriptAllowlist`, `coverageExclude`, `pureLogicRoots`,
`productionEntries` and `accessibilitySkipRoutes` entry names a `pattern` and a `reason`.
