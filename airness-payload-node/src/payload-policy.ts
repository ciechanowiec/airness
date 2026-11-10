// The Payload half of the source rules: explicit read bounds, request threading, and access decisions.
// The language half, which holds in any package, is the node layer's source-rules gate.

import type { SpecSource } from '@airness/node/axe-coverage'
import { balancedArguments, lineOf, occurrences, stripComments, topLevelSlice } from '@airness/node/source-text'
import { escapeForRegex } from '@airness/node/text-escapes'
import {
  findAnonymousDraftReads,
  findUndecidedSvgHeaders,
  findUndeclaredAccess,
  findUndeclaredVersionReads,
  findUnhardenedAuth,
  findUnlockableAuth,
  findUnrestrictedUploads,
} from './payload-access.js'
import { findUnreviewedSchemaPush } from './payload-database.js'
import { findUnprotectedPrivilegedFields } from './payload-field-access.js'
import type { PayloadViolation } from './payload-source.js'
import type { LocatedViolation } from './relationship-cleanup.js'
import { type ReachedFunction, reachedFunctions } from './source-reach.js'

interface BoundedCallRule {
  readonly call: string
  readonly required: readonly string[]
  readonly rule: string
  readonly reason: string
}

// The Local API reads that must declare their bound. `find` returns a page, so either `depth` or `limit`
// bounds it; the single-document and global reads return one document, so only `depth` applies.
const BOUNDED_CALLS: readonly BoundedCallRule[] = [
  {
    call: '.find(',
    required: ['depth', 'limit'],
    rule: 'no-unbounded-find',
    reason: 'bound payload.find() with an explicit depth and/or limit, or it pulls an unbounded relationship graph',
  },
  {
    call: '.findByID(',
    required: ['depth'],
    rule: 'no-unbounded-findbyid',
    reason: 'bound payload.findByID() with an explicit depth to cap relationship population',
  },
  {
    call: '.findGlobal(',
    required: ['depth'],
    rule: 'no-unbounded-findglobal',
    reason: 'bound payload.findGlobal() with an explicit depth to cap relationship population',
  },
]

// The names a Payload request goes by: `req`, as Payload's own examples spell it, and `request`, as a handler
// that spells its parameter out does. The ESLint configuration bans destructuring `payload` out of either.
const REQUEST: string = '(?:req|request)'

// The receivers every file shares: `payload`, the request-scoped instance and an instance a class holds.
const SHARED_RECEIVERS: string = String.raw`payload|(?:[\w$]+\.)*${REQUEST}\.payload|this\.payload`

const receiverOf = (alternatives: string): RegExp => new RegExp(String.raw`(?:^|[^\w$.])(?:${alternatives})$`)

// Recognized Payload receivers, which exclude unrelated collection methods such as array.find(). Every rule
// derives its receiver through payloadReceiverIn, so no two rules disagree about what Payload is.
const PAYLOAD_RECEIVER: RegExp = receiverOf(SHARED_RECEIVERS)

// A name a file binds to the instance `getPayload()` resolves to, as `const cms = await getPayload(...)` or
// with its type written, `const cms: Payload = await getPayload(...)`. Named, never any `<name>.payload`: a
// wildcard would read every object that happens to hold a `payload` property as the Local API.
const BOUND_INSTANCE: RegExp = /\b(?:const|let|var)\s+([a-z_$][\w$]*)\s*(?::[^=;]*)?=\s*await\s+getPayload\s*\(/gi

/**
 * The Payload receivers of one text: the shared ones, and every name it binds to `getPayload()`.
 * @param code the text whose calls are judged, with comments already stripped.
 * @returns the receiver pattern, which matches the text before a call's `.`.
 */
export const payloadReceiverIn = (code: string): RegExp => {
  const bound: readonly string[] = [...code.matchAll(BOUND_INSTANCE)].map(
    (match: RegExpExecArray): string => match[1] ?? '',
  )
  return bound.length === 0
    ? PAYLOAD_RECEIVER
    : receiverOf([SHARED_RECEIVERS, ...new Set(bound.map((name: string): string => escapeForRegex(name)))].join('|'))
}

// A property boundary keeps a value such as request: req from being read as a req property.
const declaresProperty = (topLevel: string, key: string): boolean =>
  new RegExp(String.raw`(?:^|,)\s*${key}\s*(?::|,|$)`).test(topLevel)

// The depth-one text of a call's options literal, or undefined when the call is not one to judge.
const topLevelOptionsAt = (source: string, call: string, found: number, receiver: RegExp): string | undefined => {
  if (!receiver.test(source.slice(0, found))) {
    return undefined
  }
  const argumentText: string | undefined = balancedArguments(source, found + call.length - 1)
  return argumentText?.trim().startsWith('{') === true ? topLevelSlice(argumentText) : undefined
}

// A protected property must follow every spread that could replace it.
const hasEffectiveProperty = (topLevel: string, key: string): boolean => {
  const properties: readonly RegExpExecArray[] = [
    ...topLevel.matchAll(new RegExp(String.raw`(?:^|,)\s*${key}\s*(?::|(?=,|$))`, 'gu')),
  ]
  const last: RegExpExecArray | undefined = properties.at(-1)
  return last !== undefined && last.index > topLevel.lastIndexOf('...')
}

const unboundedCallAt = (
  source: string,
  rule: BoundedCallRule,
  found: number,
  receiver: RegExp,
): PayloadViolation | undefined => {
  const topLevel: string | undefined = topLevelOptionsAt(source, rule.call, found, receiver)
  return topLevel === undefined || rule.required.some((key: string): boolean => hasEffectiveProperty(topLevel, key))
    ? undefined
    : { line: lineOf(source, found), rule: rule.rule, reason: rule.reason }
}

const findUnboundedCalls = (source: string, receiver: RegExp): readonly PayloadViolation[] =>
  BOUNDED_CALLS.flatMap((rule: BoundedCallRule): readonly PayloadViolation[] =>
    occurrences(source, rule.call)
      .map((found: number): PayloadViolation | undefined => unboundedCallAt(source, rule, found, receiver))
      .filter((violation: PayloadViolation | undefined): violation is PayloadViolation => violation !== undefined),
  )

// The collection, global, and version operations whose options carry both the caller and the request.
// tests/local-api-catalogue.spec.ts holds it against the BasePayload of the pinned Payload, so an operation a
// release adds is catalogued here or excluded there with its reason. One catalogue feeds both rules below:
// adding a Payload operation to one contract and not the other would leave a call either outside its
// transaction or running as an administrator with a decorative user value.
export const LOCAL_API_CALLS: readonly string[] = [
  '.count(',
  '.countGlobalVersions(',
  '.countVersions(',
  '.create(',
  '.delete(',
  '.duplicate(',
  '.find(',
  '.findByID(',
  '.findDistinct(',
  '.findGlobal(',
  '.findGlobalVersionByID(',
  '.findGlobalVersions(',
  '.findVersionByID(',
  '.findVersions(',
  '.restoreGlobalVersion(',
  '.restoreVersion(',
  '.update(',
  '.updateGlobal(',
]

// Only the request-scoped instance is judged, which is the whole reason the rule can be trusted. A bare
// `payload` from `getPayload()` - a script, a seed, a Server Component that opened its own instance -
// has no request to thread, and demanding one would be asking for a value that does not exist. Reaching
// the instance THROUGH `req` is the proof that one does, so the omission is never a decision.
const REQUEST_RECEIVER: RegExp = receiverOf(String.raw`(?:[\w$]+\.)*${REQUEST}\.payload`)

const findOpaqueOptions = (source: string, receiver: RegExp): readonly PayloadViolation[] =>
  LOCAL_API_CALLS.flatMap((call: string): readonly PayloadViolation[] =>
    occurrences(source, call).flatMap((found: number): readonly PayloadViolation[] =>
      receiver.test(source.slice(0, found)) && topLevelOptionsAt(source, call, found, receiver) === undefined
        ? [
            {
              line: lineOf(source, found),
              rule: 'require-explicit-payload-options',
              reason:
                `cannot check ${call.slice(1, -1)}() options; use an object literal and state ` +
                'the required depth/limit, req, and access settings after any spread',
            },
          ]
        : [],
    ),
  )

const unthreadedCallAt = (source: string, call: string, found: number): PayloadViolation | undefined => {
  const topLevel: string | undefined = topLevelOptionsAt(source, call, found, REQUEST_RECEIVER)
  if (topLevel === undefined || hasEffectiveProperty(topLevel, 'req')) {
    return undefined
  }
  return {
    line: lineOf(source, found),
    rule: 'no-unthreaded-req',
    reason:
      `pass req to ${call.slice(1, -1)}() so it joins the caller's transaction; reached through ` +
      'the request without it, the operation opens its own - it cannot see the in-flight write, and ' +
      'it is not rolled back with it',
  }
}

// The defect this catches is invisible in a passing test suite and in a code review. A hook that reads
// or writes through `req.payload` without `req` looks identical to one that threads it, runs correctly
// whenever no transaction is open, and corrupts a document only when one is - which is precisely when a
// hook runs. Payload's own documentation calls threading it critical, and no type checker can require it
// because the parameter is optional for the callers that genuinely have no request.
const findUnthreadedRequests = (source: string): readonly PayloadViolation[] =>
  LOCAL_API_CALLS.flatMap((call: string): readonly PayloadViolation[] =>
    occurrences(source, call)
      .map((found: number): PayloadViolation | undefined => unthreadedCallAt(source, call, found))
      .filter((violation: PayloadViolation | undefined): violation is PayloadViolation => violation !== undefined),
  )

interface OverrideProperty {
  readonly index: number
  readonly value: string
}

const OVERRIDE_PROPERTY: RegExp = /(?:^|,)\s*overrideAccess\s*:\s*([^,]*)/g

const overrideProperties = (topLevel: string): readonly OverrideProperty[] =>
  [...topLevel.matchAll(OVERRIDE_PROPERTY)].map(
    (match: RegExpExecArray): OverrideProperty => ({
      index: match.index,
      value: (match[1] ?? '').trim(),
    }),
  )

// A later spread can replace an earlier false. A false written after the last spread is the only form
// whose effective value this text reader can prove without resolving another object.
const hasEffectiveAccessControl = (topLevel: string): boolean => {
  const last: OverrideProperty | undefined = overrideProperties(topLevel).at(-1)
  return last?.value === 'false' && last.index > topLevel.lastIndexOf('...')
}

const userAccessViolationAt = (
  source: string,
  call: string,
  found: number,
  receiver: RegExp,
): PayloadViolation | undefined => {
  const topLevel: string | undefined = topLevelOptionsAt(source, call, found, receiver)
  if (
    topLevel === undefined ||
    !(declaresProperty(topLevel, 'user') || topLevel.includes('...')) ||
    hasEffectiveAccessControl(topLevel) ||
    overrideProperties(topLevel).some((property: OverrideProperty): boolean => property.value === 'true')
  ) {
    return undefined
  }
  return {
    line: lineOf(source, found),
    rule: 'require-user-access-control',
    reason:
      `set overrideAccess: false on ${call.slice(1, -1)}() after any spread; ` +
      'a supplied or inherited user otherwise does not enable access control. Payload ' +
      'runs the operation as an administrator and ignores that user for access control',
  }
}

const findIgnoredUsers = (source: string, receiver: RegExp): readonly PayloadViolation[] =>
  LOCAL_API_CALLS.flatMap((call: string): readonly PayloadViolation[] =>
    occurrences(source, call)
      .map((found: number): PayloadViolation | undefined => userAccessViolationAt(source, call, found, receiver))
      .filter((violation: PayloadViolation | undefined): violation is PayloadViolation => violation !== undefined),
  )

const OVERRIDE_ACCESS: RegExp = /overrideAccess\s*:\s*true/g

const findOverrideAccess = (source: string): readonly PayloadViolation[] =>
  [...source.matchAll(OVERRIDE_ACCESS)].map(
    (match: RegExpExecArray): PayloadViolation => ({
      line: lineOf(source, match.index),
      rule: 'no-override-access',
      reason:
        'overrideAccess: true bypasses Payload access control; set overrideAccess: false so the ' +
        'access rules run against the calling user',
    }),
  )

// The layers whose caller is a stranger. Everywhere else a Local API call runs in a context the project
// already trusts - a hook inside the write that authored it, a scheduled job, a seed - and privilege there is
// legitimate rather than a mistake. A route handler serves whoever reached the URL, a server action whoever
// posted to the page that holds it, and a page whoever requested it, so what each is allowed to read is the
// one thing it must never inherit by saying nothing.
const ENDPOINT_PATH: RegExp = /^src\/(?:endpoints\/|app\/(?:.*\/)?route\.[jt]s$)/u

// The App Router modules that render for the visitor who requested the route: a page, a layout, a template, a
// parallel route's default and a not-found page. Their `generateMetadata` and `generateStaticParams` run in the
// same module, for the same visitor or for every visitor of a prerendered page, so the whole module is judged.
const RENDER_MODULE_PATH: RegExp = /^src\/app\/(?:.*\/)?(?:page|layout|template|default|not-found)\.[jt]sx?$/u

// Payload's own admin and API glue, which it generates and regenerates: its server functions hand the
// request to Payload's handlers, which decide access themselves.
const GENERATED_APP_PATH: string = 'src/app/(payload)/'

// One statement of a directive prologue, the run of string statements that opens a module.
const PROLOGUE_STATEMENT: RegExp = /^(?<quote>['"])(?<directive>[^'"\n]*)\k<quote>;?/u

const prologueOf = (code: string): readonly string[] => {
  const text: string = code.trimStart()
  const statement: RegExpExecArray | null = PROLOGUE_STATEMENT.exec(text)
  return statement === null
    ? []
    : [statement.groups?.['directive'] ?? '', ...prologueOf(text.slice(statement[0].length))]
}

// A module whose directive prologue says `'use server'`: every function it exports is a server action.
// Another directive may precede it in the prologue.
const opensWithServerDirective = (code: string): boolean => prologueOf(code).includes('use server')

// A function whose own body opens with `'use server'`, which makes that function alone a server action,
// written inline in a Server Component.
const FUNCTION_SERVER_DIRECTIVE: RegExp = /\{\s*(['"])use server\1/gu

/** A run of a file's text, by offset, whose Local API calls answer to a stranger. */
interface EndpointScope {
  readonly start: number
  readonly end: number
}

const serverActionScopes = (code: string): readonly EndpointScope[] =>
  [...code.matchAll(FUNCTION_SERVER_DIRECTIVE)].flatMap((match: RegExpExecArray): readonly EndpointScope[] => {
    const body: string | undefined = balancedArguments(code, match.index)
    return body === undefined ? [] : [{ start: match.index + 1, end: match.index + 1 + body.length }]
  })

// The whole file for an endpoint module, a route handler, a rendered App Router module or a server-action
// module; each inline server action's body otherwise.
const isEndpointModule = (filePath: string, code: string): boolean =>
  ENDPOINT_PATH.test(filePath) || RENDER_MODULE_PATH.test(filePath) || opensWithServerDirective(code)

const endpointScopesIn = (filePath: string, code: string): readonly EndpointScope[] => {
  if (filePath.startsWith(GENERATED_APP_PATH)) {
    return []
  }
  return isEndpointModule(filePath, code) ? [{ start: 0, end: code.length }] : serverActionScopes(code)
}

// Whether a call leaves the access decision to Payload's privileged default.
const isAccessLeftToDefault = (source: string, call: string, found: number, receiver: RegExp): boolean => {
  if (!receiver.test(source.slice(0, found))) {
    return false
  }
  const topLevel: string | undefined = topLevelOptionsAt(source, call, found, receiver)
  return topLevel === undefined || !hasEffectiveAccessControl(topLevel)
}

const ENDPOINT_RULE: string = 'require-endpoint-access'
const ENDPOINT_CONSEQUENCE: string =
  'Payload defaults it to true, so an omission runs as an administrator and serves the document to ' +
  'whoever called the route or action or requested the page - set overrideAccess: false'

// The Local API calls of a text that leave the decision to the default, as offsets.
const unstatedAccessIn = (code: string, receiver: RegExp): readonly (readonly [string, number])[] =>
  LOCAL_API_CALLS.flatMap((call: string): readonly (readonly [string, number])[] =>
    occurrences(code, call)
      .filter((found: number): boolean => isAccessLeftToDefault(code, call, found, receiver))
      .map((found: number): readonly [string, number] => [call, found]),
  )

const isInScope = (found: number, scopes: readonly EndpointScope[]): boolean =>
  scopes.some((scope: EndpointScope): boolean => found >= scope.start && found < scope.end)

/**
 * The rules that are about Payload itself, which only a Payload package can break.
 * @param source the file's text.
 * @returns one violation per offending line.
 */
export const findPayloadViolations = (source: string): readonly PayloadViolation[] => {
  // Every rule reads the code, never the prose around it. A comment that names a banned construct in
  // order to explain why the code avoids it must not be reported as that construct.
  const code: string = stripComments(source)
  const receiver: RegExp = payloadReceiverIn(code)
  return [
    ...findOpaqueOptions(code, receiver),
    ...findUnboundedCalls(code, receiver),
    ...findUnthreadedRequests(code),
    ...findIgnoredUsers(code, receiver),
    ...findOverrideAccess(code),
    ...findUndeclaredAccess(code),
    ...findUnhardenedAuth(code),
    ...findUnlockableAuth(code),
    ...findAnonymousDraftReads(code),
    ...findUndeclaredVersionReads(code),
    ...findUnrestrictedUploads(code),
    ...findUndecidedSvgHeaders(code),
    ...findUnprotectedPrivilegedFields(code),
    ...findUnreviewedSchemaPush(code),
  ]
}

/**
 * The access decision a route handler, server action or rendered page has to state rather than inherit.
 *
 * The two rules that already read `overrideAccess` judge a value that is written down: one bans `true`,
 * the other requires `false` beside a `user`. Neither can see an absent property, and absent is the
 * dangerous spelling - Payload defaults `overrideAccess` to `true`, so the call that says nothing is
 * the call that runs as an administrator. Under `src/endpoints`, in a route handler, in a server action and
 * in a page, layout, template, default or not-found module of the App Router the omission is therefore the
 * violation, which is why this rule reads the path and the directive and the others do not.
 * @param filePath the repository-relative path of the file, which decides whether the rule applies.
 * @param source the file's text.
 * @returns one violation per Local API call that leaves the decision to the default.
 */
export const findEndpointViolations = (filePath: string, source: string): readonly PayloadViolation[] => {
  // Runs outside `findPayloadViolations`, so it strips its own comments: prose that names a call must
  // not be read as one.
  const code: string = stripComments(source)
  const scopes: readonly EndpointScope[] = endpointScopesIn(filePath, code)
  return unstatedAccessIn(code, payloadReceiverIn(code))
    .filter(([, found]: readonly [string, number]): boolean => isInScope(found, scopes))
    .map(
      ([call, found]: readonly [string, number]): PayloadViolation => ({
        line: lineOf(code, found),
        rule: ENDPOINT_RULE,
        reason:
          `state overrideAccess: false on ${call.slice(1, -1)}() in a route handler, server action or page; ` +
          ENDPOINT_CONSEQUENCE,
      }),
    )
}

// Where a scope first calls a name, so the finding points at the line that reaches the helper.
const callSiteOf = (code: string, scope: EndpointScope, name: string): number => {
  const call: RegExp = new RegExp(String.raw`(?<![\w$.])${escapeForRegex(name)}\s*\(`, 'u')
  const found: number = code.slice(scope.start, scope.end).search(call)
  return scope.start + Math.max(found, 0)
}

const reachedAccessViolations = (
  code: string,
  scope: EndpointScope,
  reached: ReachedFunction,
): readonly PayloadViolation[] =>
  [
    ...new Set(
      unstatedAccessIn(reached.body, payloadReceiverIn(reached.body)).map(([call]: readonly [string, number]): string =>
        call.slice(1, -1),
      ),
    ),
  ].map(
    (operation: string): PayloadViolation => ({
      line: lineOf(code, callSiteOf(code, scope, reached.name)),
      rule: ENDPOINT_RULE,
      reason:
        `state overrideAccess: false on ${operation}() in ${reached.name}(), which this route handler, ` +
        `server action or page calls from "${reached.specifier}"; ${ENDPOINT_CONSEQUENCE}`,
    }),
  )

/**
 * The access decision a route handler, server action or rendered page leaves to the default in a function
 * it calls.
 *
 * A handler that hands its read to a helper in `src/lib` runs that read on behalf of the same stranger, so
 * the helper's call answers to the rule exactly as one written in the handler would. The call is followed
 * once, into the member's own source, as `source-reach.ts` explains; a helper in a module the rule already
 * judges whole is left to that module's own finding.
 * @param files every source file of the member, as read.
 * @returns one violation per operation a reached function leaves to the default, located on the line of
 *   the handler that calls it.
 */
export const findReachedEndpointViolations = (files: readonly SpecSource[]): readonly LocatedViolation[] =>
  files.flatMap((file: SpecSource): readonly LocatedViolation[] => {
    const code: string = stripComments(file.source)
    return endpointScopesIn(file.path, code)
      .flatMap((scope: EndpointScope): readonly PayloadViolation[] =>
        reachedFunctions(code, code.slice(scope.start, scope.end), file.path, files)
          .filter((reached: ReachedFunction): boolean => !isEndpointModule(reached.path, reached.module))
          .flatMap((reached: ReachedFunction): readonly PayloadViolation[] =>
            reachedAccessViolations(code, scope, reached),
          ),
      )
      .map((violation: PayloadViolation): LocatedViolation => ({ path: file.path, violation }))
  })
