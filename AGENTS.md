# Repository Guidelines

## Project Structure & Module Organization

Airness is a Java 25 Maven multi-module build harness. The root `pom.xml` aggregates `airness-annotations` (shared annotations), `airness-config` (Checkstyle, PMD, SpotBugs, formatter, and OpenRewrite rules), `airness-governance` (repository checks), `airness-maven-plugin` (Maven goals), `airness-parent` (the consumer-facing parent), and `airness-assets` (managed files). Production and unit-test code follow Maven conventions under `src/main/java` and `src/test/java`. `airness-it` is deliberately outside the reactor and tests the installed harness as a consumer. Documentation lives in root-level AsciiDoc files; CI definitions are under `.github/workflows`.

## Build, Test, and Development Commands

- `mvn clean install` builds, tests, and installs all reactor modules for consumer tests.
- `sh airness-it/verify.sh` exercises expected pass and failure cases from isolated consumer projects.
- `mvn clean package -Pfull` runs slow history, dependency, mutation, secret, and Qodana checks; it requires full Git history, network access, and Docker.
- `mvn process-resources -Pformat` applies the configured formatter, import ordering, and OpenRewrite recipes.
- `sh scripts/lint-docs.sh README.adoc README-guideline-software-project.adoc README-guideline-writing.adoc` checks project documentation when its external tools are installed.

## Coding Style & Naming Conventions

Use four spaces, no tabs, UTF-8, end-of-line braces, and the formatter shipped in `airness-config`. Keep packages under `eu.ciechanowiec.airness`; use `UpperCamelCase` types and `lowerCamelCase` members. Static analysis includes Checkstyle, PMD, SpotBugs, Error Prone, NullAway, and OpenRewrite. Prefer focused classes and explanatory Javadoc for public APIs. Formatting checks report drift; run the format profile to repair it.

## Testing Guidelines

Tests use JUnit Jupiter. Name test classes `*Test` and methods as descriptive behaviors, for example `rejectsATrailingPeriod`. Add regression coverage beside the changed module. JaCoCo enforces at least 80% instruction and branch coverage per class; PIT mutation checks run in the full profile. Do not treat `mvn clean package` inside `airness-it` as a normal test: its fixtures intentionally violate rules.

## Commit & Pull Request Guidelines

Use Conventional Commits: `type(scope): subject`, such as `fix(plugin): reject an empty package root`. Allowed types include `feat`, `fix`, `docs`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, and `revert`. Keep subjects 15-72 characters, omit the final period, and add a body explaining non-trivial changes. Pull requests should explain intent, list verification performed, link relevant issues, and call out rule, workflow, or generated-asset changes; include screenshots only for documentation rendering changes.
