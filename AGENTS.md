<!-- BEGIN AIRNESS MANAGED INSTRUCTIONS -->
## Airness harness

Follow the repository contract from general to specific:

1. Follow `README-guideline-software-project.adoc`.
2. Follow the complete Airness contract in `.airness/agent-guide.md`.
3. Follow the project-owned instructions below this managed block.

Every layer is binding, and a later layer may strengthen but never weaken an earlier one. The inherited harness is the executable enforcement of this contract. Do not weaken, replace, duplicate, or version what Airness owns.
<!-- END AIRNESS MANAGED INSTRUCTIONS -->

# Repository Guidelines

## Project Structure & Module Organization

Airness is a Maven 3.9.16+ multi-module build harness that requires exactly Java 25. The root `pom.xml` aggregates `airness-annotations` (shared annotations), `airness-config` (Checkstyle, PMD, SpotBugs, formatter, and OpenRewrite rules), `airness-governance` (repository checks), `airness-maven-plugin` (Maven goals), `airness-parent` (the consumer-facing parent), `airness-parent-spring-boot` (the parent a Spring Boot project inherits instead), and `airness-assets` (managed files). Production and unit-test code follow Maven conventions under `src/main/java` and `src/test/java`. `airness-it` is deliberately outside the reactor and tests the installed harness as a consumer. Documentation lives in root-level AsciiDoc files; CI definitions are under `.github/workflows`.

## Build, Test, and Development Commands

- `mvn clean install` runs Default verification, reports stable updates across the complete parent chain, enforces
  the two-major freshness bound, and installs all reactor modules for consumer tests. Version checking
  requires Maven Central and Docker Hub; container-image updates have no failure threshold.
- `sh airness-it/verify.sh` exercises expected pass and failure cases from isolated consumer projects.
- `sh scripts/lint-docs.sh README.adoc README-guideline-software-project.adoc README-guideline-writing.adoc` checks project documentation when its external tools are installed.

## Coding Style & Naming Conventions

Keep packages under `eu.ciechanowiec.airness`. Prefer focused classes and explanatory Javadoc for public APIs.

## Testing Guidelines

Airness tests use JUnit Jupiter. Name test classes `*Test` and methods as descriptive behaviors, for example
`rejectsATrailingPeriod`. Add regression coverage beside the changed module. Self-coverage includes every governance
class and every non-Mojo Maven-plugin support class. Do not treat `mvn clean package` inside
`airness-it` as a normal test: its fixtures intentionally violate rules.

## Commit & Pull Request Guidelines

Pull requests should explain intent, list verification performed, link relevant issues, and call out rule, workflow,
or generated-asset changes; include screenshots only for documentation rendering changes.
