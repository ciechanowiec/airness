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

Airness is a Maven 3.10.0+ multi-module build harness that requires exactly Java 25. The root `pom.xml` aggregates `airness-annotations` (shared annotations), `airness-config` (Checkstyle, PMD, SpotBugs, and formatter rules), `airness-error-prone` (compiler-side rules that need resolved types), `airness-governance` (repository checks), `airness-maven-plugin` (Maven goals), `airness-spring-evidence` (test-scope Spring Boot runtime evidence), `airness-web-evidence` (test-scope accessibility auditing of a rendered page), `airness-parent` (the generic parent with the repository rules), `airness-parent-java` (the parent a Java project inherits, adding the Java rules), `airness-parent-spring-boot` (the parent a Spring Boot project inherits instead), `airness-parent-node` (the parent a JS/TS module inherits), `airness-node` (the npm package `@airness/node`: the JS/TS gates, their analyzer configurations and the pinned analyzers, written in TypeScript under `airness-node/src`), and `airness-assets` (managed files). Production and unit-test code follow Maven conventions under `src/main/java` and `src/test/java`. `airness-it` is deliberately outside the reactor and tests the installed harness as a consumer. Documentation lives in root-level AsciiDoc files; CI definitions are under `.github/workflows`.

The root `pom.xml` also aggregates the Payload stack: `airness-payload-assets` (the Payload managed files and generated-file declarations), `airness-parent-payload` (the parent a Payload CMS module inherits) and `airness-payload-node` (the npm package `@airness/payload`: the Payload gates, under `airness-payload-node/src`). The Payload stack is named in the core only at the registration points `StackConfinementTest` lists; keep any other core change generic.

## Build, Test, and Development Commands

- `sh airness-it/report-browser.sh` installs the pinned Chromium used by report tests. Add `--with-deps` on Linux
  to install its system libraries. Run this as toolchain setup before the first build.
- `mvn clean install` runs Default verification, reports stable updates across the complete parent chain, and
  installs all reactor modules for consumer tests into this clone's own local Maven repository, which the README
  section Local Harness Builds names. The integration suite installs its node fixtures from `registry.npmjs.org`.
- `sh airness-it/promote.sh` copies this clone's installed harness into `~/.m2/repository`, which governed
  projects build against. Run it only while no governed project builds.
- `sh airness-it/verify.sh` exercises expected pass and failure cases from isolated consumer projects.
- `sh airness-it/verify.sh <domain>...` diagnoses only the named domains against installed artifacts. It is a
  partial run; the complete integration verdict still requires the command without a domain. The domains are
  `maven`, `analysis`, `templates`, `stylesheets`, `repository`, `spring`, `containers`, `scanners`, `node`,
  `browser`, `payload` (the Payload stack against a real Payload application and an isolated PostgreSQL; it needs
  Docker) and `browser-evidence` (motion, ancestor clipping, missing-page layouts and completed project scans,
  which also run in the browser domain).

## Coding Style & Naming Conventions

Prefer focused classes and explanatory Javadoc for public APIs.

## Testing Guidelines

Airness tests use JUnit Jupiter. Name test classes `*Test` and methods as descriptive behaviors, for example
`rejectsATrailingPeriod`. Each npm package module, `airness-node` and `airness-payload-node`, tests with Vitest under its own tests directory, importing sources through the `@/` alias. Add regression coverage beside the changed module.
The tests of a rule, gate or check that recognizes a construct cover each equivalent spelling the language or
library allows for it, for example JUnit and AssertJ assertions, `req.payload` and a destructured `{ payload }`,
`String.format` and `formatted`, `&&` and `||` around an `instanceof`. Self-coverage includes every governance
class and every non-Mojo Maven-plugin support class. Do not treat `mvn clean package` inside
`airness-it` as a normal test: its fixtures intentionally violate rules.

## Commit & Pull Request Guidelines

Pull requests should explain intent, list verification performed, link relevant issues, and call out rule, workflow,
or generated-asset changes; include screenshots only for documentation rendering changes.
