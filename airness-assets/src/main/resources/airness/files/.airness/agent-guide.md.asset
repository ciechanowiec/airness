# Airness Agent Guide

This guide maps the governing standard to the Airness harness. After it, continue through the lower `AGENTS.md`
layers in the order the standard declares. Exact analyzer rules remain in the executable harness.

## Layer 1: Governing Standard

- Read `README-guideline-software-project.adoc` before using this Airness-specific mapping.

## Layer 2: Harness Invariants

- The project inherits `airness-parent`, or `airness-parent-spring-boot` when it is a Spring Boot project.
  That choice is the only place a project states which kind it is, and it is what decides which rules apply.
  Do not weaken, replace, duplicate, or version the plugins, dependencies, analyzers, rules, thresholds, or
  managed files that the parent owns.
- Use exactly Java 25, Maven 3.9.16 or later, and a Git working tree. Default verification reads Maven Central and
  Docker Hub and fails when either cannot be read, and Extended verification also needs a reachable Docker daemon.
  Keep every production and test package under the `airness.package.root` declared in the root `pom.xml`.
- Treat every finding and every tool, setup, or compilation failure as a failed verification. Reporting findings with
  `-Dairness.enforce=false` is not a pass. A build using `-DskipTests` produces no Airness verdict.

## Layer 3: Governed Domains

Airness governs all of the following domains:

- **Toolchain and Maven model:** runtime versions, project and parent coordinates, package ownership, inherited plugin
  ownership, raw-model anti-bypass checks, effective dependency convergence, the ordering of declared properties, and
  valid Airness parameters.
- **Repository files and instructions:** managed, seeded, and forbidden files; the root license file; agent
  instruction files; editor and Git configuration; and an unchanged committable tree during verification. The editor
  configuration is held against the files a commit would carry, so what git is configured to ignore is passed over
  rather than reported: build output and a tool's scratch are not content this style governs.
- **Source:** formatting, imports, modernization recipes, compilation with retained formal parameter names,
  nullness, static analysis, documentation
  comments, source comments, typography, banned substitutes, banned generated members, and cycles among the
  packages of a module. A generated member is refused where what it generates is a public API the type never
  stated, or an answer this harness already gives elsewhere: a mutator, a builder, a wither, a lock held on a field
  the source never shows, and a second vocabulary for nullness beside jSpecify. The rest of Lombok stays available,
  and two of its members are required rather than merely allowed. Every production package declares its nullness
  with `@NullMarked` on its `package-info`, which the parent supplies the annotation for: a package that declares
  none leaves every type in it undecided, and a class implementing a marked framework interface then states a
  weaker contract than the one it overrides. Markup and stylesheets are formatted too, every markup resource a
  module ships is read by the parser the template engines use, so a fragment no page calls yet is still proved to
  parse, and a fragment is held to the argument cap a callable is held to, because a fragment is invoked by name
  with a positional list like any other callable. An element a fragment replaces carries nothing else the dialect
  reads, because the replacement discards the element and everything written on it, which no other check reaches:
  the markup parses, the page renders, and the condition beside the replacement decided nothing. A link expression
  reaches for nothing the engine refuses to read inside one, because what a link carries ends up in an address a
  browser follows and is evaluated under a rule that turns away a bean, a static class and an instantiation. That
  expression compiles and fails on the first request that draws it, so what the link needs is asked for beside it
  and the link is handed the variable that answered. A constructor expression written inside a repository query is
  read the same way and for the same reason: it is a call nothing compiles, so a record given four components and
  constructed with three is correct everywhere a tool looks and wrong the first time the query runs. The count is
  read against the records the module itself declares, so a type declared beside it in another module is passed
  over rather than reported. A fragment call is read against the fragments the module declares, so a name nothing
  declares and an argument list of the wrong length are both reported where they are written. That is the other half
  of the cap above and it fails the same way a constructor expression does: the document parses, the page renders for
  as long as nothing draws that branch, and the first request that draws it fails on a name. A fragment renamed in one
  file and called from four others is the ordinary way it happens. What an expression builds rather than writes out,
  and what selects an element rather than naming a declaration, are passed over, since neither names a fragment that
  can be resolved. Markup writes nothing into a page unescaped and has no expression read a second time as an
  expression. The first turns a stored value into markup and renders correctly for every value nobody chose to attack
  it with, and the second turns one into an expression the engine runs, whose reach is the engine's rather than the
  page's. Write the escaping form and keep markup out of what the model carries, and write the expression the
  preprocessing was building rather than composing a name out of a value.
- **Dependencies:** explicit scopes, exactly named versions, no project-declared repositories or system paths,
  released dependencies for a released project, one version and one owning artifact per class, unused dependencies,
  declared mocking libraries, licenses, known vulnerabilities, available stable package and container-image updates,
  container tag-digest drift, and the maximum permitted coordinate freshness gap.
- **Artifacts:** the finished JAR contains no unsafe or duplicate paths, development or source files, test-only output,
  machine-local repository paths, or recognizable secret material.
- **Tests and evidence:** test execution, test integrity and determinism, a default timeout on every test, a
  shuffled execution order under a declared seed, an assertion in every test, assertions that literals alone cannot
  settle, production-to-test boundaries, per-class line and branch coverage, and current-build coverage evidence.
- **Spring Boot, for a project inheriting `airness-parent-spring-boot` alone:** the constructs the container accepts
  and then does not honour, covering proxy semantics, bean wiring, transactions, persistence mapping, the web layer,
  security configuration, asynchrony and scheduling, and test context handling; the runtime settings an
  `application.yml`, `application.yaml` or `application.properties` declares, and the ones whose absence decides
  behaviour; a persistence entity carried by a web request or response; and more than one application class in the
  build. A view name a handler hands back is read against the markup the module ships, because nothing compiles that
  string and the template behind it is found only when a request is answered, so a template renamed or moved leaves
  every handler that named it compiling and failing on the first request that reaches it. The name is read where it is
  written plainly, which is a constant as readily as a literal. A name a handler builds is passed over, and so is a
  string returned by anything answering with a body rather than with a page, since neither states a template. What a
  redirect or a forward names is an address rather than a template and is left alone here. A parameter an
  authorization expression reads is named for the runtime by its own annotation even though Airness retains Java
  parameter names in the class file. The annotation makes the security binding a declaration that survives a Java
  parameter rename instead of letting that refactor silently change what the expression reads. A reference with no
  such annotation behind it is therefore refused before it can resolve to an unintended value and make the guard
  decide something other than what it says. A mapped value that may hold nothing is a
  deliberate exception rather than an ordinary one, and is priced the way every deliberate exception here is: a
  field of a persistent class or a component of a persistent record annotated `@Nullable` is reported until a
  suppression with its reason sits beside it. Nullness at a boundary and nullness at rest are different things: a
  bound form holds nothing because the framework read nothing into it, while a column holds nothing because
  somebody decided it may, which is either a fact that is genuinely absent, such as the day an unissued document
  was issued, or two shapes flattened into one table because the type system was never asked to tell them apart.
  Writing the reason is what separates the two, since a reason that has to name which kind of row carries the value
  has written the missing type out by hand. A constructor parameter is passed over, because a persistent record
  standing a default in for what a binder could not build says nothing there about the column.
- **Spring Boot configuration keys:** every key a settings file declares is read against the
  `spring-configuration-metadata.json` that the dependencies on the compile classpath publish about themselves. A key
  those suppliers no longer bind, a key they still bind and have deprecated, and a key that no declared group accounts
  for are each refused, because none of them fails at startup and none appears in a log. Write the key the metadata
  names: a replacement is reported with the offence. A key under a namespace no dependency declares is your own and is
  left alone, so a project binds its own settings freely. Declaring one key twice in one document is refused as well,
  since only the last of them is bound.
- **Repository assurance:** secret scanning, Qodana analysis, the ceiling on how many suppressions the repository
  holds, complete Git history, commit-message policy, commit typography, linear history, and history-wide
  compliance.

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

The search for an assertion stops at the file boundary, so a test that reaches one only through a helper in another
type reports as unproven. Move the assertion into the test's own source rather than suppressing the finding.

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
3. Run `mvn process-resources -Pformat` to apply formatting and rewrite recipes, then review the resulting source.
4. Run `mvn clean verify` for Default verification. Verify rather than package, because a project that repackages
   its archive produces the archive that ships during package, and the artifact-content check reads it afterwards.
5. Run `mvn clean verify -Pextended` before finishing. Extended verification includes Default verification and adds
   the known-vulnerability scan and the history, secret, and Qodana checks. Default verification alone never reads the
   vulnerability database.

## Layer 5: Exceptions and Repairs

- Fix a managed-file finding with `mvn airness:assets-sync`. A seed's contents belong to the project after creation,
  but its path remains mandatory. A project may take over an eligible path through `airness.assets.unmanaged` only
  when the assignment explicitly requires it and the project records the reason.
- Fix formatter and rewrite findings with the format command. Fix compilation, analysis, dependency, test, coverage,
  history, security, and tool failures at their source; do not disable the failing check.
- Read a resource the JAR carries by resolving `Class.getResource` through `Optional.ofNullable(...)` and
  `orElseThrow(...)` to a `URL`, and only then opening the stream in a plain try-with-resources. A stream opened
  before the missing-resource check, or checked inside the resource specifier, is unclosed on the throwing edge,
  which is the `OBL_UNSATISFIED_OBLIGATION_EXCEPTION_EDGE` finding. A `URL` carries no close obligation, so
  resolving it first puts the check where nothing is open yet.
- Weaken a parameter when `TypeMayBeWeakened` asks, and answer the two findings it raises that no weakening can
  satisfy by changing the declaration rather than by suppressing the rule. Give a record its defensive copy through
  a compact constructor, which declares no parameter list at all, rather than through a canonical constructor whose
  parameters are pinned to the component types. Name the first navigation off a parameter that the body only ever
  reads as the qualifier of a further call, rather than chaining it. Such a parameter has no declared expected type
  at its single use, which is the one place this inspection cannot rule a weakening out, so a Jackson `JsonNode`
  reads as weakenable to `TreeNode` even though `TreeNode` returns `TreeNode` and declares no value reader and the
  weakening does not compile. Naming the first hop supplies the expected type and the inspection then rules it out
  itself. A local already carries its initializer's type, so only parameters need this. Neither repair costs any
  coverage, and neither needs an entry in the profile: prefer both to `stopClasses`, which would exempt a type
  everywhere and so give up the genuine weakenings the same run still reports.
- Say that a bound value is required in a method rather than in an annotation beside a nullable one. A value read
  out of a request may genuinely arrive as nothing, which the type says with `@Nullable`, and a constraint
  annotation that says it must not be absent is a second nullness annotation on the same type use, which the
  compiler refuses. The two are both right and cannot be written together. What answers both is a method on the
  bound record that names every value the request left out, keyed by the same property path a constraint violation
  would carry, which whatever gathers the refusals then merges with what the binder could not read and what the
  remaining rules refuse. The constraints that bound a value which is present stay where they are.
- Write a multi-line annotation with its arguments on their own lines and its closing bracket alone, which is what
  the paired-brackets rule asks of every construct whose brackets span lines. An annotation whose argument is a
  text block always spans lines, so the shape is not optional there: open the bracket, start the text block on the
  next line, and close the bracket on a line of its own. A single-member annotation names no member, because
  `value =` written out is a redundancy the inspections refuse; an annotation carrying two or more members names
  each one. The formatter cannot produce this shape and leaves a one-line opening untouched, so it is written by
  hand.
- Change an exclusion or the default test timeout only for an explicit project requirement. Never use one merely to
  obtain a pass.
- The test order seed and the suppression ceiling are the harness's own and take no project setting. Answer a
  suppression-ceiling finding by removing a suppression, and never by trying to raise the ceiling.
- There is no setting that turns a check off. If a check is wrong for the project, that is an Airness bug to be
  fixed in Airness, not a local override.
- For a source suppression, put `@SuppressWarnings` and a non-empty `@Justification` on the same declaration and use
  the analyzer's exact rule ID. For a tool without source suppressions, use its Airness-owned configuration or
  baseline mechanism and keep the reason beside the entry.

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
| `airness.typography.excludes` | repository path prefixes the typography scan skips |
| `airness.coverage.excluded.classes` | qualified class patterns the coverage floors skip |
| `airness.test.timeout` | the ceiling on one test, and `30 s` unless set |
| `airness.dependency-check.suppression.file` | a local OWASP suppression file, described below |

Those six are the whole of what a project file may declare. Every other name under `airness.`, and `skipTests`,
`maven.test.skip`, and `jacoco.dataFile`, is refused there, because each of them can decide a verdict. The refusal
reads the file as written, so a name inside a profile that is never activated is refused too. Both
`-Dairness.enforce=false` and `-DskipTests` are command-line flags and are never written into a project file.

Reach for the suppression file only when no upgrade answers an advisory. Keep it at
`.airness/dependency-check-suppressions.xml`, which is the path the scan reads: the document's presence there is what
puts it in front of the scan, so one kept elsewhere is never read. Every rule in it names the advisory it excuses,
says in its `notes` why this project cannot reach the vulnerability, and carries a `YYYY-MM-DD` date. A rule that
suppresses nothing fails the build, like every other exclusion that reaches nothing.
