<!-- BEGIN AIRNESS MANAGED: generic -->
## Airness harness

Follow the repository contract from general to specific:

1. Follow `README-guideline-software-project.adoc`.
2. Follow the complete Airness contract in `.airness/agent-guide.md`.
3. Follow each Airness stack layer below, where it applies.
4. Follow the project-owned instructions below the managed blocks.

Each step is binding, and a later step may strengthen but never weaken an earlier one. The inherited harness is the executable enforcement of this contract. Do not weaken, replace, duplicate, or version what Airness owns.
<!-- END AIRNESS MANAGED: generic -->

<!-- BEGIN AIRNESS MANAGED: java -->
## Airness Java layer

In every module that inherits `airness-parent-java`, also follow `.airness/agent-guide-java.md`.
<!-- END AIRNESS MANAGED: java -->

<!-- BEGIN AIRNESS MANAGED: node -->
## Airness node layer

Each directory a module lists in `airness.node.projects` is a node project. In it, also follow the Node projects domain of `.airness/agent-guide.md` and the sections that domain names.
<!-- END AIRNESS MANAGED: node -->

# Repository Guidelines

## Project Goal

Airness gives the owners of Maven-built software projects whose code AI agents write a harness they inherit from one parent: it enforces `README-guideline-software-project.adoc` for the project's technology stack, so changes can merge without human code review.

## Project Structure & Module Organization

Airness is a Maven 3.10.0+ multi-module build harness that requires exactly Java 25. The root `pom.xml` aggregates `airness-annotations` (shared annotations), `airness-config` (Checkstyle, PMD, SpotBugs, and formatter rules), `airness-error-prone` (compiler-side rules that need resolved types), `airness-governance` (repository checks), `airness-maven-plugin` (Maven goals), `airness-spring-evidence` (test-scope Spring Boot runtime evidence, and the listener that fails a Spring test on a server error it did not declare), `airness-web-evidence` (test-scope accessibility auditing of a rendered page), `airness-network-guard` (the test-scope host name resolver that keeps a consumer's tests on their machine), `airness-parent` (the generic parent with the repository rules), `airness-parent-java` (the parent a Java project inherits, adding the Java rules), `airness-parent-spring-boot` (the parent a Spring Boot project inherits instead), `airness-parent-node` (the parent a JS/TS module inherits), `airness-node` (the npm package `@airness/node`: the JS/TS gates, their analyzer configurations and the pinned analyzers, written in TypeScript under `airness-node/src`), `airness-assets` (managed files), and `airness-starters` (the starter projects `airness:new` makes an application from, each the project-owned part of a working application, with `pom.xml`, `package.json`, `compose.yaml`, `.gitignore`, `.dockerignore` and `AGENTS.md` stored under an added `.fixture` suffix). Production and unit-test code follow Maven conventions under `src/main/java` and `src/test/java`. `airness-it` is deliberately outside the reactor and tests the installed harness as a consumer. Documentation lives in root-level AsciiDoc files; CI definitions are under `.github/workflows`.

The root `pom.xml` also aggregates the Payload stack: `airness-payload-assets` (the Payload managed files and generated-file declarations), `airness-parent-payload` (the parent a Payload CMS module inherits) and `airness-payload-node` (the npm package `@airness/payload`: the Payload gates, under `airness-payload-node/src`). The Payload stack is named in the core only at the registration points `StackConfinementTest` lists; keep any other core change generic. To remove the stack, only delete, so that no text is added, moved or reworded: delete every path the stack-path list of `StackConfinementTest` names; at every registration point that test lists, delete what names the stack, its lines, sentences and list elements, each element with the separator that sets it apart; delete by hand the deny rules of `airness-payload-node` in `.claude/settings.json`, because `mvn airness:assets-sync` adds missing rules and never removes one; finally delete every entry of the stack-path and registration lists of `StackConfinementTest`, with the line breaks around them, and the names only those entries read, which leaves both lists as `List.of()`. The generic uses that test lists stay.

A stack layer above the node layer joins the node gates through the `airness.layer` block of its own npm package, read from the copy a member resolves and only for the layer packages `@airness/node` names, never from the member itself. Besides defaults for the node project settings, the block names: `imageProbes`, the paths the `image` gate asks a booted image for beside the origin; `e2eSpecs`, a package directory whose compiled `*.e2e.spec.js` files `e2e` runs as a Playwright project of the layer's own; `eslint`, an export whose configuration starts with the blocks of `@airness/node/eslint` and adds the layer's own, composed in `@airness/node/eslint-layered`, which refuses a block that turns a node rule off, lowers it, replaces its options with ones that don't extend them, ignores files for every block, or sets anything but files, ignores, plugins and rules, except the relaxations `@airness/node` lists for that layer; `productionEntries`, the files its framework runs in production of its own accord, which both `knip` runs count as entry points; `knipConfigPlugins`, the knip plugins the production run turns off because they read one of those entries as a tool's configuration; `analysisEnvironment`, the inert placeholders under `defaults`, extended by the member's `package.json` map that `projectSettings` names, which let a framework configuration load under knip; `browser`, an export that supplies `scanRootSelectors`, the roots of a custom view the browser scanner judges; and `securityOverrides`, the layer's approved security replacements. A seam value of the wrong shape, an export the layer doesn't have, or a specification directory holding no specification fails the gate.

## Build, Test, and Development Commands

- `sh airness-it/report-browser.sh` installs the pinned Chromium used by report tests. Add `--with-deps` on Linux to install its system libraries. Run this as toolchain setup before the first build.
- `mvn clean install` runs Default verification, reports stable updates across the complete parent chain, and installs all reactor modules for consumer tests into this clone's own local Maven repository, which Local Harness Builds below describes. The integration suite installs its node fixtures from `registry.npmjs.org`.
- Create each parallel worktree beside the clone, for example `git worktree add ../airness-<name>`, never inside it: the harness reads every project file beneath the repository root, so a worktree under `.claude/worktrees` fails the main clone's build.
- `sh airness-it/promote.sh` copies this clone's installed harness into `~/.m2/repository`, which governed projects build against. Run it only while no governed project builds.
- `sh airness-it/verify.sh` exercises expected pass and failure cases from isolated consumer projects.
- `sh airness-it/verify.sh <domain>...` diagnoses only the named domains against installed artifacts. It is a partial run; the complete integration verdict still requires the command without a domain. The domains are `maven`, `analysis`, `templates`, `stylesheets`, `repository`, `spring`, `containers`, `scanners`, `node`, `browser`, `payload` (the Payload stack against a real Payload application and an isolated PostgreSQL; it needs Docker), `starters` (an application `airness:new` makes from each starter passes Extended verification; it needs Docker) and `browser-evidence` (motion, ancestor clipping, missing-page layouts and completed project scans, which also run in the browser domain).

## Local Harness Builds

A governed project that names a SNAPSHOT Airness parent resolves it from `~/.m2/repository`. Clones of this repository installing there would replace each other's harness under one version, and a build starting during another clone's install would read a harness mixed from both. Each clone therefore installs into a local repository of its own: in this repository alone, the pinned `.mvn/maven.config` ends with two lines no governed project receives, which `mvn airness:assets-sync` writes here and the asset check requires here and refuses elsewhere:

```text
-Dmaven.repo.local=${user.home}/.m2/clones${session.rootDirectory}
-Dmaven.repo.local.tail=${user.home}/.m2/repository
```

A clone at `/work/airness` installs into `~/.m2/clones/work/airness` and reads the shared repository behind it as a read-only tail, so it downloads nothing the shared one holds; its pinned Node toolchain is cached in its own repository, and Extended verification mounts the tail for Qodana beside it. `airness-it/verify.sh`, `airness-it/node.sh`, `airness-it/report-browser.sh` and `airness-it/promote.sh` ask Maven for the clone's repository, and `verify.sh` hands it, with the tail, to every consumer fixture, which builds outside the clone and reads no `.mvn/maven.config`. `-Dmaven.repo.local` in `MAVEN_ARGS` moves the repository for Maven and the scripts alike, and `MAVEN_REPO_LOCAL` names one to the scripts alone. `promote.sh` copies every `eu.ciechanowiec` artifact whose name starts with `airness` into the shared repository and refuses when the clone installed nothing. One promotion runs at a time; a second waits, and a lock a killed promotion left is reported for removal, never taken over.

## Coding Style & Naming Conventions

Prefer focused classes and explanatory Javadoc for public APIs.

`README.adoc` explains the mechanisms and concepts of Airness to people, juniors included, and leaves exact rules to the agent guides, which hold every detail an agent needs. It follows `README-guideline-writing.adoc`, passes `adocent lint README.adoc`, links each technology it names to that technology's official site at its first mention in every paragraph, list item and table cell, and changes in the same change as a mechanism it describes. Its diagrams are draw.io PNG files under `.adocent/resources/visuals` that carry their editable source: edit them in draw.io and export them with the diagram embedded again.

## Testing Guidelines

Airness tests use JUnit Jupiter. Name test classes `*Test` and methods as descriptive behaviors, for example `rejectsATrailingPeriod`. Each npm package module, `airness-node` and `airness-payload-node`, tests with Vitest under its own tests directory, importing sources through the `@/` alias. Add regression coverage beside the changed module. The tests of a rule, gate or check that recognizes a construct cover each equivalent spelling the language or library allows for it, for example JUnit and AssertJ assertions, `req.payload` and a destructured `{ payload }`, `String.format` and `formatted`, `&&` and `||` around an `instanceof`. Self-coverage includes every governance class and every non-Mojo Maven-plugin support class. Do not treat `mvn clean package` inside `airness-it` as a normal test: its fixtures intentionally violate rules.

## Commit & Pull Request Guidelines

Pull requests should explain intent, list verification performed, link relevant issues, and call out rule, workflow, or generated-asset changes; include screenshots only for documentation rendering changes.
