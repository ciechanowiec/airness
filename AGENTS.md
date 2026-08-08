<!-- BEGIN AIRNESS MANAGED INSTRUCTIONS -->
## Airness harness

Follow the repository contract from general to specific:

1. Follow `README-guideline-software-project.adoc`.
2. Follow the complete Airness contract in `.airness/agent-guide.md`.
3. Follow the project-owned instructions below this managed block.

Every layer is binding, and a later layer may strengthen but never weaken an earlier one. The inherited harness is
the executable enforcement of this contract. Do not weaken, replace, duplicate, or version what Airness owns. Use
only the write commands declared by the guide, review their edits, and complete its required verification before
finishing.
<!-- END AIRNESS MANAGED INSTRUCTIONS -->

# Repository Guidelines

## Project Structure & Module Organization

Airness is a Maven 3.9.16+ multi-module build harness that requires exactly Java 25. The root `pom.xml` aggregates `airness-annotations` (shared annotations), `airness-config` (Checkstyle, PMD, SpotBugs, formatter, and OpenRewrite rules), `airness-governance` (repository checks), `airness-maven-plugin` (Maven goals), `airness-parent` (the consumer-facing parent), and `airness-assets` (managed files). Production and unit-test code follow Maven conventions under `src/main/java` and `src/test/java`. `airness-it` is deliberately outside the reactor and tests the installed harness as a consumer. Documentation lives in root-level AsciiDoc files; CI definitions are under `.github/workflows`.

## Build, Test, and Development Commands

- `mvn clean package` runs Default verification.
- `mvn clean install` runs Default verification, reports stable updates across the complete parent chain, enforces
  the two-major freshness bound, and installs all reactor modules for consumer tests. Version checking
  requires Maven Central.
- `sh airness-it/verify.sh` exercises expected pass and failure cases from isolated consumer projects.
- `mvn clean package -Pextended` runs Extended verification. It adds history, mutation, secret, Qodana, and
  Airness README checks to Default verification. It requires full Git history, network access, Docker, Python 3,
  Asciidoctor, Vale, `pdftotext`, and Tesseract.
- `mvn process-resources -Pformat` applies the configured formatter, import ordering, and OpenRewrite recipes.
- `sh scripts/lint-docs.sh README.adoc README-guideline-software-project.adoc README-guideline-writing.adoc` checks project documentation when its external tools are installed.

## Coding Style & Naming Conventions

Use four spaces, no tabs, UTF-8, end-of-line braces, and the formatter shipped in `airness-config`. Keep packages under `eu.ciechanowiec.airness`; use `UpperCamelCase` types and `lowerCamelCase` members. Static analysis includes Checkstyle, PMD, SpotBugs, Error Prone, NullAway, and OpenRewrite. Prefer focused classes and explanatory Javadoc for public APIs. Formatting checks report drift; run the format profile to repair it.

## Testing Guidelines

Airness tests use JUnit Jupiter and real components rather than mocks, stubs, or spies. Consumer projects may choose
their own test-double policy. Name test classes `*Test` and methods as descriptive behaviors, for example
`rejectsATrailingPeriod`. Add regression coverage beside the changed module. JaCoCo enforces at least 80% instruction
and branch coverage per consumer class, across the governance bundle, and per directly tested Maven-plugin utility
class. PIT enforces an 80% mutation score for the self-tested implementation. Consumer mutation analysis uses the
accepted-survivor baseline documented in `.airness/agent-guide.md`. Do not treat `mvn clean package` inside
`airness-it` as a normal test: its fixtures intentionally violate rules.

## Commit & Pull Request Guidelines

Use Conventional Commits: `type(scope): subject`, such as `fix(plugin): reject an empty package root`. Allowed types include `feat`, `fix`, `docs`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, and `revert`. Keep subjects 15-72 characters, omit the final period, and add a body explaining non-trivial changes. Pull requests should explain intent, list verification performed, link relevant issues, and call out rule, workflow, or generated-asset changes; include screenshots only for documentation rendering changes.
