// Security-sensitive values at the two output boundaries static syntax can identify reliably.
//
// These selectors deliberately stop at an explicit function call. `redact(user.token)` and
// `publicError(error)` are reviewable sanitisation boundaries; reaching through them would turn a rule
// about raw values into a guess about a function's semantics. Direct identifiers, member reads,
// conditional branches, logical fallbacks, templates, concatenations, and object or array literals have
// no such boundary and are rejected, however they nest.
/** One `no-restricted-syntax` entry: the shape to reject, and what to say when it appears. */
export interface RestrictedSyntax {
  readonly selector: string
  readonly message: string
}

// A credential named alone, or as the last word of a camelCase compound such as `accessToken`,
// `clientSecret` or `dbPassword`. The compound needs a lowercase letter or digit before its last word, so
// `tokenCount` and `passwordPolicy`, which name something about a credential, are not credentials.
const SENSITIVE_NAME: string =
  '/^(?:password|token|secret|apiKey|privateKey|creditCard|ssn|cvv|' +
  '[a-z][a-zA-Z0-9]*[a-z0-9](?:Password|Token|Secret|ApiKey|PrivateKey))$/'

// The syntax that carries a value through to the expression holding it without a function call in
// between: a conditional's branches (never its test), either operand of a logical expression, a string
// concatenation, a template's substitutions, an optional chain, and the type-only wrappers. A comparison
// is not one of them - `token === expected` yields a boolean, not the token.
const VALUE_WRAPPER: string =
  ":matches(ConditionalExpression, LogicalExpression, BinaryExpression[operator='+'], TemplateLiteral, " +
  'ChainExpression, TSAsExpression, TSSatisfiesExpression, TSNonNullExpression)'

// An object or array literal also carries a value through, but only inside an argument: a property key
// is a name, not a value, and so is never the reported node.
const CONTAINER_WRAPPER: string = ':matches(ObjectExpression, ArrayExpression, Property)'

// How many wrappers deep a value is followed. Each depth is spelled out as direct-child steps, because
// a descendant step would reach through a call - the sanitisation boundary this module stops at.
const MAX_WRAPPERS: number = 6

const reachedThrough = (step: string, target: string): readonly string[] =>
  Array.from({ length: MAX_WRAPPERS + 1 }, (_unused: unknown, depth: number): string =>
    [...Array.from({ length: depth }, (): string => `${step}:not(.test, .key)`), `${target}:not(.test, .key)`].join(
      ' > ',
    ),
  )

// One restriction per output boundary, its alternatives joined in a single `:matches` so a value that
// two of them describe is still reported once.
const selectorUnion = (selectors: readonly string[]): string => `:matches(${selectors.join(', ')})`

// A value wrapper or, inside an argument, a container: everything a value reaches a boundary through.
const CARRYING_WRAPPER: string = selectorUnion([VALUE_WRAPPER, CONTAINER_WRAPPER])

const anyOf = (anchors: readonly string[], paths: readonly string[]): string =>
  selectorUnion(
    anchors.flatMap((anchor: string): readonly string[] => paths.map((rest: string): string => `${anchor} > ${rest}`)),
  )

const CONSOLE_CALL: string =
  "CallExpression[callee.type='MemberExpression']" +
  "[callee.object.name='console']" +
  '[callee.property.name=/^(?:log|error|warn|info|debug|trace)$/]'

// A member read names its key as an identifier, or computed as a string literal or a template without a
// substitution: `user.token`, `user['token']` and the same key quoted by backticks read one property.
const SENSITIVE_VALUE: string =
  `:matches(Identifier[name=${SENSITIVE_NAME}], MemberExpression[property.name=${SENSITIVE_NAME}], ` +
  `MemberExpression[computed=true][property.value=${SENSITIVE_NAME}], ` +
  "MemberExpression[computed=true][property.type='TemplateLiteral'][property.expressions.length=0]" +
  `[property.quasis.0.value.cooked=${SENSITIVE_NAME}])`

/** Console arguments that write a credential-bearing value without passing it through a call. */
const SENSITIVE_LOGGING: RestrictedSyntax = {
  selector: anyOf([CONSOLE_CALL], reachedThrough(CARRYING_WRAPPER, `${SENSITIVE_VALUE}:not(.callee)`)),
  message:
    '[sensitive-data-logged] Do not write a credential-bearing value directly to console output; ' +
    'remove it or pass an explicitly redacted value.',
}

const INTERNAL_ERROR: string =
  'MemberExpression[object.name=/^(?:err|error|exception)$/][property.name=/^(?:message|stack)$/]'

const ERROR_PROPERTY: string =
  ':matches(Property[key.name=/^(?:error|message)$/], Property[key.value=/^(?:error|message)$/])'

const JSON_RESPONSE: string =
  "CallExpression[callee.type='MemberExpression']" +
  '[callee.object.name=/^(?:Response|NextResponse)$/]' +
  "[callee.property.name='json']"

const RESPONSE_BODY: string = "NewExpression[callee.name='Response']"

// The places a returned object becomes a caller's response, and the object's `error` or `message`
// property, directly or one object deeper: `{ body: { message: error.message } }`.
const OBJECT_ANCHORS: readonly string[] = [
  JSON_RESPONSE,
  'ReturnStatement',
  'ArrowFunctionExpression[expression=true]',
].flatMap((anchor: string): readonly string[] => [
  `${anchor} > ObjectExpression > ${ERROR_PROPERTY}`,
  `${anchor} > ObjectExpression > Property > ObjectExpression > ${ERROR_PROPERTY}`,
])

/** Internal error text returned to a caller without passing it through a call. */
const INTERNAL_ERROR_RESPONSE: RestrictedSyntax = {
  selector: selectorUnion([
    anyOf([JSON_RESPONSE, RESPONSE_BODY], reachedThrough(VALUE_WRAPPER, INTERNAL_ERROR)),
    anyOf(OBJECT_ANCHORS, reachedThrough(CARRYING_WRAPPER, INTERNAL_ERROR)),
  ]),
  message:
    '[leaks-error-message] Do not return an internal error message or stack to a caller; log it ' +
    'server-side and return a generic or explicitly sanitised public error.',
}

/** Output-boundary syntax every composed configuration must retain. */
export const SECURITY_RESTRICTIONS: readonly RestrictedSyntax[] = [SENSITIVE_LOGGING, INTERNAL_ERROR_RESPONSE]
