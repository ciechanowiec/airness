# Airness Java Agent Guide

This guide adds the Java layer to `.airness/agent-guide.md` for every module that inherits `airness-parent-java`,
including through `airness-parent-spring-boot`. Read the generic guide first; every rule it states applies here too.

## Layer 3: Governed Java Domains

Airness governs all of the following domains in a Java module:

- **Source:** formatting, imports, modernization rules, compilation with retained formal parameter names,
  nullness, static analysis, documentation
  comments, source comments, typography, banned substitutes, banned generated members, and cycles among the
  packages of a module. A generated member is refused where what it generates is a public API the type never
  stated, or an answer this harness already gives elsewhere: a mutator, a builder, a wither, a lock held on a field
  the source never shows, and a second vocabulary for nullness beside jSpecify. The rest of Lombok stays available,
  and two of its members are required rather than merely allowed. Every production package declares its nullness
  with `@NullMarked` on its `package-info`, which the parent supplies the annotation for: a package that declares
  none leaves every type in it undecided, and a class implementing a marked framework interface then states a
  weaker contract than the one it overrides. Markup is formatted too, and stylesheets are formatted by the node
  project that checks them. Every markup resource a module ships is read by the parser the template engines use,
  so a fragment no page calls yet is still proved to parse, and a fragment is held to the argument cap a callable is held to, because a fragment is invoked by name
  with a positional list like any other callable. An element a fragment replaces carries nothing else the dialect
  reads, because the replacement discards the element and everything written on it, which no other check reaches:
  the markup parses, the page renders, and the condition beside the replacement decided nothing. Markup a page
  writes outside the parts it hands a layout reaches no reader at all, because the element carrying the replacement
  is the whole document and only what was handed over survives it. That is the same silence one step wider: the
  document parses, every expression in it resolves, the page renders, and the part nobody was handed is simply
  missing from it, so a suite of any size passes while asserting what came back rather than what ought to have. An
  element is reached by being handed over, by holding something that is, or by declaring a fragment or holding one,
  since a fragment is reached by its name from wherever that name is written. A page handing over what cannot be
  named this way, such as a class or a tag, is passed over whole rather than reported against a set of parts known
  to be incomplete. A fragment binds every name it reads, or reads none that is bound above it, because a request
  for a fragment alone is answered by processing the element it is declared on and everything under it and nothing
  over it, so an attribute binding a name above the fragment runs when the whole page is drawn and not when the
  fragment is. That silence is the same one again and the most complete of the three: an unbound name is no error
  to the engine, a condition reading one decides against and a value writing one writes nothing, so the request is
  answered with markup that parses and renders, while a suite reading the whole page reads the draw in which the
  binding did run. A name bound again at the fragment or inside it is read rather than reported, and so is one the
  fragment declares as a parameter, since each of those arrives however the fragment was reached. A link expression
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
  file and called from four others is the ordinary way it happens. Positional calls require the declared count;
  named calls require the declared names and may supply additional local variables, including to a fragment
  with no declared parameters. A computed label remains unknown rather than being mistaken for a literal name. What an expression builds rather than writes out,
  and what selects an element rather than naming a declaration, are passed over, since neither names a fragment that
  can be resolved. A call is read wherever it is written, including inside another call's argument list, because a
  page handing its own controls to a shared header calls a fragment there and hands it a positional list like any
  other call. A call is written only where the engine evaluates one, which is inside a variable expression or a
  selection expression, because everywhere else a standard expression reads a literal, a number, a token and the
  operators between them. The two arms of a conditional may therefore be written out but may not be worked out, so a
  page that turns a written-out word into a lookup moves the closing brace with it. Missing that is one character
  wide and fails on the first request that draws the value. Markup writes nothing into a page unescaped and has no
  expression read a second time as an
  expression. The first turns a stored value into markup and renders correctly for every value nobody chose to attack
  it with, and the second turns one into an expression the engine runs, whose reach is the engine's rather than the
  page's. Write the escaping form and keep markup out of what the model carries, and write the expression the
  preprocessing was building rather than composing a name out of a value. The attribute processor sets what an
  attribute holds rather than what it is called, and it reads its argument as a comma-separated list of
  assignments, so it carries neither a name holding a colon nor a value holding a comma. An attribute whose name
  holds one, as the binding attributes of a front-end library do, is written on the element directly. A selector in
  a stylesheet the project wrote selects what it names, whatever line it was wrapped onto. The space between two
  parts of one is a combinator, so a chain of refusals broken across lines refuses one thing about an element and
  another about whatever that element holds, which is valid, formats, and paints something other than what was
  written. The formatter joins the halves back onto one line and keeps the space, which leaves the mistake reading
  like a decision, so what is read is the join rather than the length of a line. A group written inside something is
  ordinary and is left alone, and a stylesheet somebody else published is not read at all.
- **Artifacts:** the finished JAR contains no unsafe or duplicate paths, development or source files, test-only output,
  machine-local repository paths, or recognizable secret material, and its manifest declares the versioned classes the
  archive ships and the restricted native access the classes of the module reach for. Production and test output
  are recognized both at the archive root and under Spring Boot's `BOOT-INF/classes/`; diagnostics retain the
  packaged path. An executable JAR declaring `Enable-Native-Access` must give it exactly `ALL-UNNAMED`, even
  when no restricted call is detected, because the launcher refuses every other value before application startup.
- **Tests and evidence:** test execution, test integrity and determinism, a default timeout on every test, a
  shuffled execution order under a declared seed, an assertion in every test, assertions that literals alone cannot
  settle, production-to-test boundaries, per-class line and branch coverage, current-build coverage evidence, and
  current-build evidence that a framework-assembled production application reached ready.
  Consumer lifecycle verification, including incremental `mvn verify`, starts each module's JaCoCo execution
  data and Spring context evidence afresh at `initialize`, once per Maven session. Surefire, Failsafe
  and lifecycle forks in that session then append to the same evidence. Old startup records, security
  findings and covered probes cannot carry over from a previous invocation. Include `initialize`
  before standalone test goals when preparing their evidence; standalone goals are not a full
  verification. `-DskipTests` still produces no Airness verdict.
  Failsafe, Build Helper and Assembly are optional managed plugins: declare a plugin without a version
  in the module that uses it. Failsafe supplies `integration-test` and `verify` executions for the
  standard `IT*`, `*IT` and `*ITCase` names, shares Surefire's timeout and ordering, and fails when the
  opted-in module has no integration tests. Run through `verify`: coverage combines both test phases,
  Spring runtime evidence reads both, and the final tree check includes integration-test writes.
  Build Helper's source and resource roots and Assembly's distribution descriptors belong to the project.
- **Inline code in markup:** every markup resource is held to the code it carries. An executable `<script>`
  without a source (no type, `module` or a JavaScript type), a `<style>` element, an `on*`, `th:on*` or
  `data-th-on*` handler attribute, a `javascript:` address and `th:inline` set to `javascript` or `css` are
  refused: move the code into a checked file of the frontend tooling project and reference it. Data blocks such as
  `application/json`, `application/ld+json`, `importmap` and `speculationrules` stay. Thymeleaf, htmx and Alpine
  expression attributes are template syntax rather than standalone JavaScript; the static checks above read the
  Thymeleaf ones, and the Extended browser sweep assesses what any of them does when the page runs.
- **Static and runtime template assessment:** the template checks resolve fragments, scopes, literal view names
  and literal message keys from the markup and sources alone. A view name a handler chooses at run time is
  logged as statically unassessed; its page is assessed by the browser sweep through the route its handler maps,
  which the route inventory requires to be swept, scanned by a project specification or skipped with a reason.
  An application that starts proves only that it starts: rendered states, roles and validation failures need
  project specifications driving them to an `expectSweptPage` scan.
- **Thymeleaf message keys in Spring Boot applications:** literal `#{key}` references in production HTML
  `th:*` and `data-th-*` processing attributes must resolve through the message source used by the application's
  template engine during its existing startup tests. Bare or quoted keys may contain ASCII letters, digits, dots,
  underscores and hyphens, with optional arguments. The lookup checks the base messages with `Locale.ROOT`;
  message parity continues to own translation completeness. Computed keys, quoted examples, fragment declarations,
  inline text and JavaScript, and application-code lookups are outside this check. Assessment covers a single standard
  Spring template engine with a standard classpath HTML resolver and a parentless `ResourceBundleMessageSource` whose
  base bundles are available, including dependency bundles, or an empty parentless `DelegatingMessageSource`.
  Custom engines, resolvers, message sources and external templates are explicitly reported as unassessed.
  Current-build evidence binds the production application and reference inventory to the startup test run;
  missing or stale evidence and missing keys fail verification, and a passing context cannot erase another
  supported context's failure. Evidence records keys and source locations, never translated values.
- **Spring Boot, for a project inheriting `airness-parent-spring-boot` alone:** the constructs the container accepts and
  then does not honour, covering proxy semantics, bean wiring, transactions, persistence mapping, the web layer,
  security configuration, asynchrony and scheduling, and test context handling; the runtime settings an
  `application.yml`, `application.yaml` or `application.properties` declares, and the ones whose absence decides
  behaviour; a persistence entity carried by a web request or response; more than one application class in the build;
  and evidence that the production application reached ready during the current tests. That evidence proves
  unconditional wiring and the profiles active in the test, not a profile the test never activated. A view name a
  handler hands back is read against the markup the module ships, because nothing compiles that string and the template
  behind it is found only when a request is answered, so a template renamed or moved leaves every handler that named it
  compiling and failing on the first request that reaches it. String view names are read from mapped controller
  methods and from exception handlers in controllers or controller advice. Response-body annotations apply to the
  class or method they mark, so a body-returning sibling cannot exempt an HTML handler. Model-attribute methods and
  ordinary helpers do not declare string view names. An explicit `ModelAndView` names a view independently of those
  string-return rules. An explicit argument list on a view name selecting a
  declared fragment is read against the list that fragment declares; a bare selector carries no list and is left alone.
  The name is read where it is written plainly, which is a
  constant as readily as a literal. A name a handler builds is passed over, and so is a string returned by anything
  answering with a body rather than with a page, since neither states a template. What a redirect or a forward names is
  an address rather than a template and is left alone here. A parameter an authorization expression reads is named for
  the runtime by its own annotation even though Airness retains Java parameter names in the class file. The annotation
  makes the security binding a declaration that survives a Java parameter rename instead of letting that refactor
  silently change what the expression reads. A reference with no such annotation behind it is therefore refused before
  it can resolve to an unintended value and make the guard decide something other than what it says. Retained Java
  parameter names are fallback metadata rather than an external contract. A bean an authorization expression calls is
  read against the beans the ready application holds, since which beans exist is settled by the classes of the module
  together with every auto-configuration on the classpath and no source states it. A name nothing is declared under, and
  a method the bean it names does not answer to, both parse and both compile and neither is resolved until a caller
  arrives, so the guard raises instead of deciding and the method behind it answers nobody rather than refusing them. A
  handler therefore annotates the source, name and requiredness of each individual request value.
  Spring's whole-request `@RequestParam Map` and `MultiValueMap` forms deliberately omit the name,
  and their resolver ignores individual-value requiredness and defaults. Airness recognizes the
  standard library interfaces through qualified names or unshadowed explicit imports, with an
  omitted name or an empty string literal. An ambiguous inherited or local name, a wrapper, an
  array, a custom subtype or a name supplied by a constant retains the ordinary checks. This
  exception does not validate the map's contents: the application still validates the fields it
  needs. A string repository query binds only named `@Param` values, and a bound model or
  application request part states validation. Feature annotations are
  paired with the production annotation that enables them, and an asynchronous or scheduled method names the
  infrastructure that runs it. Cache destinations, configuration-property namespaces, transaction-bound event phases,
  pagination defaults and test SQL are written rather than inherited from framework defaults. A listener that runs after
  its publisher has committed and writes under `@Transactional` opens a transaction of its own with `REQUIRES_NEW`,
  because the one it would otherwise join has already ended and a write made under it is flushed by nothing. A startup
  runner states its order with `@Order` or `Ordered`, because the container runs a runner that states none wherever
  registration happened to put it, and a runner that admits users before the bootstrap which writes the first
  administrator only into an empty user table inverts that bootstrap in silence. Persistence mapping follows the same
  boundary: entity, table, column, join, sequence, access and inheritance names stay stable when a Java declaration is
  renamed. A mapped value that may hold nothing is a deliberate exception rather than an ordinary one, and is priced the
  way every deliberate exception here is: a field of a persistent class or a component of a persistent record annotated
  `@Nullable` is reported until a suppression with its reason sits beside it. Nullness at a boundary and nullness at
  rest are different things: a bound form holds nothing because the framework read nothing into it, while a column holds
  nothing because somebody decided it may, which is either a fact that is genuinely absent, such as the day an unissued
  document was issued, or two shapes flattened into one table because the type system was never asked to tell them
  apart. Writing the reason is what separates the two, since a reason that has to name which kind of row carries the
  value has written the missing type out by hand. A constructor parameter is passed over, because a persistent record
  standing a default in for what a binder could not build says nothing there about the column. A role a security
  annotation names is read against the enum implementing `GrantedAuthority` that the production sources declare, with or
  without the `ROLE_` prefix, because a role nobody is granted decides against every caller without a line in any log. A
  project naming a role while declaring no such enum is refused rather than passed over, since a security rule that
  quietly checks nothing is worse than one asking for the declaration it needs, and an authority spelled out as a
  literal such as `"ROLE_ADMIN"` is refused in favour of the enum's own derivation. A path variable a handler names is
  read against the placeholders of its mapping, class-level and method-level paths joined, since a name the mapping
  never declares answers the route and fails on the first request; a path or a name the source does not state is passed
  over. What the running application leaves open is read from the ready context rather than from any file: every mapping
  the module declares is put to the security chain as an anonymous caller, and one the chain admits is reported unless a
  `permitAll` matcher of the module names that exact pattern. Which endpoints answer an unauthenticated caller is
  decided by a mapping in one file, a matcher in another and a path the container composes from both, so no source
  states it and no rule reading source can find it. Naming the pattern is the declaration that it is public, whether the
  matcher writes it out or names a string constant of the module, bare from the same source or qualified by the type
  declaring it; a matcher naming a prefix is not, because it admits whatever is mapped under that prefix afterwards and
  widens without anybody rereading it. A matcher names a method as readily as a pattern, and one that names none opens
  every method mapped under that path, so it declares the mapping it opens only while that pattern opens one and is
  asked for the method as soon as it opens two. A collection posted to by a guest and read back by its owner is one path
  and two mappings, and the line meant to open the write opens the read beside it, which no rule reading a pattern alone
  can see. Write `requestMatchers(HttpMethod.POST, PATH).permitAll()` for the method that is public and leave the one
  that is not to the rule below it, which is a form drawn by a get and submitted by a post spelled as the two matchers
  it is. An argument that resolves to no written string, a call among them, states no pattern, so the mapping stays
  reported. An argument is read as a method only after it has failed to resolve to a string, so a constant of the module
  named for a verb and holding a path is still a path. A handler carrying an authorization annotation is passed over,
  since its decision is taken after the chain. Collection-filter annotations alone do not establish authorization.
  Mappings the framework contributes, the error controller and actuator endpoints among them, belong to their own rules. Nothing but the authorization decision
  is exercised: no filter of the project runs, no session is created and no handler is called, so recording this cannot
  change what the test around it observes. A class of the application layer that guards some of its public methods
  guards all of them, because an authorization annotation is the whole of what decides who may call one, and a method
  added beside guarded siblings and left unannotated is reached by every caller the container admits while looking
  exactly like the methods around it. A class guarding none of them is passed over, having taken on no obligation, and
  so is a controller, whose callers are settled by the filter chain and read from the running application by the rule
  above. A method the container invokes itself, a schedule, a lifecycle callback, an event listener or an override, is
  passed over too: no principal is in scope on the thread one runs on, so a guard there denies every invocation, and an
  override may carry its guard on the method it overrides. Where reaching a method without a role is what was meant,
  `@PreAuthorize("permitAll()")` says so, that being the annotation the prePostEnabled configuration every project has
  already reads, where the JSR-250 `@PermitAll` is honoured only by a project that enabled that family beside it. A
  redirect or a forward whose whole target is a value the request carried is refused, because the link that carried it
  chose where the reader goes and the sign-in they passed on the way makes the destination read as the next step of the
  flow. A prefix carrying a path of its own puts the value in a segment beneath this application and is left alone, a
  path variable is not read as a value the caller sent since the mapping declared the path it is a segment of, and a
  value handed to a call of the project's own is read as one the handler worked out, which is also the repair. A
  fragment the module's markup declares is read against everything in the module that could reach it, which is a
  fragment call in that markup and, because a view name is as often assembled from constants as returned as a literal,
  the plain occurrence of the name inside a string the module's Java writes. A fragment neither of those mentions is
  dead markup that parses, is held to the argument cap, is passed over by the rule about calls because it is the callee
  rather than a caller, and is rendered by nothing, so it is reported where it was declared; a fragment merely mentioned
  anywhere, tests included, is left alone, which trades a dead fragment somebody happened to name for never accusing one
  a handler reaches by a name it builds.
- **Spring MVC streaming timeouts:** registered production handlers returning `StreamingResponseBody`
  directly or inside `ResponseEntity` require a production timeout declaration and an effective MVC
  timeout choice in the existing ready-context tests. Declare `spring.mvc.async.request-timeout` in
  loaded production configuration, optionally through a deployment placeholder, or call
  `setDefaultTimeout` on the supplied argument of a straight-line `configureAsyncSupport` callback
  in an active named production `WebMvcConfigurer`. Ordinary fluent calls are supported. Airness
  sets no duration: finite deadlines and explicit no-deadline values are application decisions.
  Test-only settings, inactive beans and calls on a different object do not establish production
  policy. Conditional, delegated, anonymous or otherwise unverifiable callbacks and custom MVC
  dispatch that cannot be assessed fail the check. The supported runtime is standard Spring Boot
  MVC or `@EnableWebMvc`, with the standard request adapter and streaming return-value handling.
  The listener inspects this setup without invoking application handlers or replaying callbacks.
  Input fingerprints and the Maven invocation bind the assessment to production sources and copied
  configuration. Missing, stale, malformed and unsupported evidence fail, and a passing context
  cannot erase another context's failure. Assessment covers tested ready contexts and registered
  handlers, not inactive deployment profiles. Plain Java, non-MVC applications and contexts without
  production streaming handlers require no timeout policy. The Spring parent prepares inputs at
  `process-classes` and enforces `spring-streaming-timeouts` at `verify`; neither goal adds
  a runtime dependency to the application archive.
- **Spring Boot configuration keys:** every key a settings file declares is read against the
  `spring-configuration-metadata.json` that the dependencies on the compile classpath publish about themselves. A key
  those suppliers no longer bind, a key they still bind and have deprecated, and a key that no declared group accounts
  for are each refused, because none of them fails at startup and none appears in a log. Write the key the metadata
  names: a replacement is reported with the offence. A key under a namespace no dependency declares is your own and is
  left alone, so a project binds its own settings freely. Declaring one key twice in one document is refused as well,
  since only the last of them is bound. A placeholder production code reads without a default is read against the base
  configuration file, because the test profile satisfies it in every test while an environment starting
  from the base file refuses to start. Published metadata recognizes names but supplies no environment
  value; a required placeholder needs a base declaration or its own default. A module shipping no base
  file is passed over. A lazy
  repository bootstrap is refused, because every repository query is parsed against the entity model when the
  repository is built, and a lazy one is built on its first call. Three settings are refused for what they
  hand out: an actuator publishing an endpoint that carries a credential or stops the application, which the
  wildcard rule beside it does not reach when the endpoint is named outright; an environment endpoint
  returning its values in full, including whatever a placeholder resolved a credential to; and a session
  cookie the page can read, which turns anything injected into one page from something acting inside the
  session into something holding it.

## Layer 5: Java Exceptions and Repairs

- Connect a Java web interface to the browser sweep without rewriting it: list a frontend tooling directory in
  `airness.node.projects` (the module itself works, with `sourceRoots` naming `src/main/resources/static`), move
  each authored script and stylesheet under its checked roots, replace copied distributions with a WebJar or a
  `node-run` copy from npm, name a `serve` script launching `$AIRNESS_APPLICATION_ARTIFACT` on `$PORT`, start
  the database and other services through `airness.testWrapper` with fixture data of its own, and add
  `tests/e2e/**/*.e2e.spec.ts` specifications that sign in as each representative role and call
  `expectSweptPage(page)` on the pages the anonymous crawl cannot reach. Keep the existing Selenium tests; they
  remain project tests beside the automatic sweep.
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
- Repair markup a layout discards by moving it inside a part the layout is handed, rather than by widening what the
  layout takes. A shell is held to the argument cap a callable is held to, so a page wanting a fifth thing from one
  is a page asking the shell to know something about it. Markup that genuinely belongs to no part of the page is
  markup to delete.
- Repair a fragment reading a name bound above it by binding the name on the fragment as well, which is one line and
  is never wrong, since a binding written there runs whenever the fragment does and whenever the whole page does.
  Moving the binding down rather than repeating it is the same repair where nothing above the fragment reads the
  name. Taking the name as a parameter is the repair where the callers already differ about what it should be.
- Write a multi-line annotation with its arguments on their own lines and its closing bracket alone, which is what
  the paired-brackets rule asks of every construct whose brackets span lines. An annotation whose argument is a
  text block always spans lines, so the shape is not optional there: open the bracket, start the text block on the
  next line, and close the bracket on a line of its own. A single-member annotation names no member, because
  `value =` written out is a redundancy the inspections refuse; an annotation carrying two or more members names
  each one. The formatter cannot produce this shape and leaves a one-line opening untouched, so it is written by
  hand.
- Let the formatter wrap a condition too long for one line: it opens the bracket, writes the condition on the next
  line one level deeper, and closes the bracket on a line of its own, which is the shape the paired-brackets rule
  asks of an `if` or `while` whose condition spans lines. A condition an author wraps by hand keeps the shape it was
  written in, because the formatter joins no line it finds broken, so it is written paired from the start: nothing
  follows `if (` on its line, and `) {` stands alone.
- Start a block lambda handed to a call on the line after the bracket, or hand the call a method reference to a
  private method holding the body. The formatter wraps a call only when the call itself is too long for its line,
  so `run(x -> {` is left where it stands, and the paired-brackets rule refuses it because the call's brackets span
  lines while the content starts on the opening bracket's line. Neither repair is made by the format command.
- For a source suppression, put `@SuppressWarnings` and a non-empty `@Justification` on the same declaration and use
  the identifier the analyzer accepts in a suppression, which is not always the identifier it reported. For a tool
  without source suppressions, use its Airness-owned configuration or baseline mechanism and keep the reason beside
  the entry.
- The Qodana inspections that measure the size or the coupling of a type report one name and accept another, so the
  reported name suppresses nothing and is then itself reported as a suppression of nothing. A wrong identifier
  therefore costs two full runs rather than one, because the second failure reads like a new finding. `ClassCoupling`
  is suppressed as `OverlyCoupledClass`, `MethodCoupling` as `OverlyCoupledMethod`, and `FieldCount` as
  `ClassWithTooManyFields`. Where a reported name suppresses nothing and no other name is known, report it to
  Airness rather than leaving a suppression that holds nothing back.
- A static field whose initializer creates mutable state is reported as `AirnessStaticStateIsBounded` even when it
  is `private static final`, because `final` fixes the reference and not the contents. That covers a `new` of a
  mutable container, an `Atomic*`, a `ThreadLocal`, a `StringBuilder` or an array anywhere in the initializer, and a
  factory that returns mutable state: `Collections.synchronized*`, `Collections.newSetFromMap`,
  `ConcurrentHashMap.newKeySet`, `ThreadLocal.withInitial`, `Executors.new*`, the Guava `Lists.new*`, `Sets.new*`,
  `Maps.new*`, `Queues.new*` and `CacheBuilder`, and `Caffeine.newBuilder()`. A `new` inside a lambda, a method
  reference or an anonymous class body, one wrapped by `Collections.unmodifiable*` or a `copyOf`, and one that is
  only the receiver of a method call, whose result is what the field stores, are not reported. A receiver whose call
  returns a live view of it, such as `keySet`, `values`, `entrySet` or `subList`, is still reported. Bound the
  state or scope it to a unit of work. A deliberate cache is not banned: suppress the rule on that field with a
  `@Justification` that states its bound, for example "keyed by an enum, so bounded by its constants".
