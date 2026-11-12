// A test whose every assertion asks only whether a value exists.
//
// `expect(users).toBeDefined()` passes for every value but `undefined`: an empty list, the wrong record and an
// error object alike. A test asserting nothing else reaches an assertion, which is all `expect-expect` asks, and
// still judges no outcome, so it passes whatever the code under test computed. One was found asserting exactly
// that of a value the code could never leave undefined, having passed every check. This rule fails such a test
// at the test, while a test that also judges an outcome keeps its existence checks as the guards they usually
// are, before a property read.
//
// An existence assertion is one of the spellings that say only "present" or "truthy": `toBeDefined()`,
// `toBeTruthy()`, `not.toBeUndefined()`, `not.toBeFalsy()`, `not.toBeNull()` (which `undefined` passes too),
// `not.toBe(undefined)` and its `toEqual`, `toStrictEqual`, `null` and `void 0` forms, `toEqual(expect.anything())`,
// and a presence test handed to a boolean matcher, as in `expect(Boolean(x)).toBe(true)` or
// `expect(x === undefined).toBe(false)`. Each counts whether it is reached through `expect`, `expect.soft` or
// `expect.poll`, and under `resolves`. Chai's and Node's truthiness asserts count as well: `assert(x)`,
// `assert.ok`, `assert.isOk`, `assert.exists`, `assert.isDefined`, `assert.isNotNull`, `assert.isNotUndefined`,
// and `assert.notEqual(x, undefined)` with its strict form.
//
// Everything else judges an outcome, an absence included: `toBeUndefined()` fails when the code starts returning
// something. So does `rejects`, because a rejection is the outcome, any other `assert.*` call, and a helper named
// `expect...` or `assert...` with a capital, whose inside is not read here. A `fc.assert` property judges by its
// own assertions where it holds some, and is an outcome of its own where it returns a boolean, which cannot be
// read either.

import type { Rule, SourceCode } from 'eslint'
import { calleeNames, isList, isSyntaxNode, type SyntaxNode } from './eslint-spec-syntax.js'

type Verdict = 'existence' | 'outcome'

const EXISTENCE: readonly Verdict[] = ['existence']
const OUTCOME: readonly Verdict[] = ['outcome']
const NONE: readonly Verdict[] = []

// What may follow `it` or `test` in a call that declares a test: Vitest's and Playwright's modifiers and table
// forms. Anything else, `test.describe`, `test.step` or `test.beforeEach`, declares something that is not a test.
const TEST_MODIFIERS: ReadonlySet<string> = new Set<string>([
  'concurrent',
  'each',
  'fail',
  'fails',
  'fixme',
  'for',
  'only',
  'runIf',
  'sequential',
  'skip',
  'skipIf',
])

const PRESENT: ReadonlySet<string> = new Set<string>(['toBeDefined', 'toBeTruthy'])
const ABSENT: ReadonlySet<string> = new Set<string>(['toBeUndefined', 'toBeFalsy', 'toBeNull'])
const EQUALITY: ReadonlySet<string> = new Set<string>(['toBe', 'toEqual', 'toStrictEqual'])
const DEEP_EQUALITY: ReadonlySet<string> = new Set<string>(['toEqual', 'toStrictEqual'])
const EXPECT_FORMS: ReadonlySet<string> = new Set<string>(['soft', 'poll'])
const EXISTENCE_ASSERTS: ReadonlySet<string> = new Set<string>([
  'exists',
  'isDefined',
  'isNotNull',
  'isNotUndefined',
  'isOk',
  'ok',
])
const INEQUALITY_ASSERTS: ReadonlySet<string> = new Set<string>(['notEqual', 'notStrictEqual'])
const HELPER: RegExp = /^(?:expect|assert)[A-Z]/u

const field = (node: unknown, key: string): unknown => (isSyntaxNode(node) ? Reflect.get(node, key) : undefined)

const isNamed = (node: unknown, name: string): boolean =>
  isSyntaxNode(node) && node.type === 'Identifier' && node.name === name

const argumentsOf = (node: unknown): readonly unknown[] => {
  const values: unknown = field(node, 'arguments')
  return isList(values) ? values : []
}

const propertyName = (member: SyntaxNode): string | undefined => {
  const property: unknown = member.property
  if (field(member, 'computed') !== true) {
    return isSyntaxNode(property) && typeof property.name === 'string' ? property.name : undefined
  }
  const value: unknown = field(property, 'value')
  return typeof value === 'string' ? value : undefined
}

const memberOf = (node: unknown, owner: string): string | undefined =>
  isSyntaxNode(node) && node.type === 'MemberExpression' && isNamed(node.object, owner) ? propertyName(node) : undefined

// `undefined`, `null` and `void 0`, the values an inequality to which says only that something is there.
const isNothing = (node: unknown): boolean =>
  isNamed(node, 'undefined') ||
  (field(node, 'type') === 'Literal' && field(node, 'raw') === 'null') ||
  (field(node, 'type') === 'UnaryExpression' && field(node, 'operator') === 'void')

const isLiteral = (node: unknown, isTrue: boolean): boolean =>
  field(node, 'type') === 'Literal' && field(node, 'value') === isTrue

const isAnything = (node: unknown): boolean =>
  memberOf(field(node, 'callee'), 'expect') === 'anything' && argumentsOf(node).length === 0

// `typeof x` compared with the string 'undefined'.
const isUndefinedType = (typeOf: unknown, name: unknown): boolean =>
  field(typeOf, 'type') === 'UnaryExpression' &&
  field(typeOf, 'operator') === 'typeof' &&
  field(name, 'value') === 'undefined'

const isComparisonWithNothing = (node: unknown): boolean => {
  const left: unknown = field(node, 'left')
  const right: unknown = field(node, 'right')
  return isNothing(left) || isNothing(right) || isUndefinedType(left, right) || isUndefinedType(right, left)
}

const isNegation = (node: unknown): boolean =>
  field(node, 'type') === 'UnaryExpression' && field(node, 'operator') === '!'

// A presence test, whose truth says only that its operand is there: `Boolean(x)`, `!!x`, `x !== undefined`,
// `x != null`, `typeof x !== 'undefined'` and their mirrored forms.
const isPresenceTest = (node: unknown): boolean =>
  (isNamed(field(node, 'callee'), 'Boolean') && argumentsOf(node).length === 1) ||
  (isNegation(node) && isNegation(field(node, 'argument'))) ||
  (['!==', '!='].includes(String(field(node, 'operator'))) && isComparisonWithNothing(node))

// An absence test, whose falsity says only that its operand is there: `!x`, `x === undefined`, `x == null`.
const isAbsenceTest = (node: unknown): boolean =>
  (isNegation(node) && !isNegation(field(node, 'argument'))) ||
  (['===', '=='].includes(String(field(node, 'operator'))) && isComparisonWithNothing(node))

/** A matcher call as the chain reads it: the matcher, whether `not` turned it, and what it was handed. */
interface Matcher {
  readonly name: string
  readonly negated: boolean
  readonly arguments: readonly unknown[]
}

// Whether a matcher asserts that its subject is true, or that it is false, as a boolean matcher can.
const isTruthAsserted = (matcher: Matcher, isTrue: boolean): boolean => {
  const isAsserted: boolean = matcher.negated ? !isTrue : isTrue
  return (
    (EQUALITY.has(matcher.name) && matcher.arguments.length === 1 && isLiteral(matcher.arguments[0], isAsserted)) ||
    (matcher.arguments.length === 0 && matcher.name === (isAsserted ? 'toBeTruthy' : 'toBeFalsy'))
  )
}

// The single argument a matcher was handed, if it was handed exactly one.
const onlyArgument = (matcher: Matcher): unknown => (matcher.arguments.length === 1 ? matcher.arguments[0] : undefined)

// `not.toBeUndefined()`, `not.toBeFalsy()`, `not.toBeNull()` and `not.toBe(undefined)` with its other forms.
const isNegatedExistence = (matcher: Matcher): boolean =>
  (ABSENT.has(matcher.name) && matcher.arguments.length === 0) ||
  (EQUALITY.has(matcher.name) && isNothing(onlyArgument(matcher)))

// `toBeDefined()`, `toBeTruthy()` and `toEqual(expect.anything())` with its strict form.
const isPlainExistence = (matcher: Matcher): boolean =>
  (PRESENT.has(matcher.name) && matcher.arguments.length === 0) ||
  (DEEP_EQUALITY.has(matcher.name) && isAnything(onlyArgument(matcher)))

const isExistenceMatcher = (matcher: Matcher): boolean =>
  matcher.negated ? isNegatedExistence(matcher) : isPlainExistence(matcher)

// Whether a matcher asks about the truth of its subject at all, as `toBeTruthy()` or `toBe(false)` do.
const isTruthMatcher = (matcher: Matcher): boolean => isTruthAsserted(matcher, true) || isTruthAsserted(matcher, false)

// A boolean matcher over a presence or an absence test asks whatever that test asks, so `expect(!x).toBeTruthy()`
// judges an absence. Any other matcher asks what it asks of any subject.
const isExistence = (subject: unknown, matcher: Matcher): boolean => {
  if (isTruthMatcher(matcher) && isPresenceTest(subject)) {
    return isTruthAsserted(matcher, true)
  }
  return isTruthMatcher(matcher) && isAbsenceTest(subject)
    ? isTruthAsserted(matcher, false)
    : isExistenceMatcher(matcher)
}

const judgeMatcher = (subject: unknown, matcher: Matcher): readonly Verdict[] =>
  isExistence(subject, matcher) ? EXISTENCE : OUTCOME

// `expect(x)`, `expect.soft(x)` or `expect.poll(fn)`: the call a matcher chain starts from.
const isExpectation = (node: unknown): boolean => {
  const callee: unknown = field(node, 'callee')
  return (
    field(node, 'type') === 'CallExpression' &&
    (isNamed(callee, 'expect') || EXPECT_FORMS.has(String(memberOf(callee, 'expect'))))
  )
}

/** A matcher chain, from the expectation it starts at to the names read off it, the matcher's last. */
interface Chain {
  readonly expectation: unknown
  readonly names: readonly string[]
}

const chainOf = (callee: unknown, names: readonly string[] = []): Chain | undefined => {
  if (isExpectation(callee)) {
    return names.length === 0 ? undefined : { expectation: callee, names }
  }
  if (!isSyntaxNode(callee) || callee.type !== 'MemberExpression') {
    return undefined
  }
  const name: string | undefined = propertyName(callee)
  return name === undefined ? undefined : chainOf(callee.object, [name, ...names])
}

// `resolves` hands the matcher what the promise settled to, so it changes nothing about what the matcher asks.
// `rejects`, or any modifier but one `not`, makes the assertion an outcome.
const judgeChain = (chain: Chain, call: SyntaxNode): readonly Verdict[] => {
  const names: readonly string[] = chain.names.filter((name: string): boolean => name !== 'resolves')
  const modifiers: readonly string[] = names.slice(0, -1)
  const name: string | undefined = names.at(-1)
  if (name === undefined || modifiers.some((modifier: string): boolean => modifier !== 'not') || modifiers.length > 1) {
    return OUTCOME
  }
  const matcher: Matcher = { name, negated: modifiers.length === 1, arguments: argumentsOf(call) }
  return judgeMatcher(argumentsOf(chain.expectation)[0], matcher)
}

const judgeAssert = (call: SyntaxNode): readonly Verdict[] => {
  if (isNamed(call.callee, 'assert')) {
    return EXISTENCE
  }
  const name: string | undefined = memberOf(call.callee, 'assert')
  if (name === undefined) {
    return NONE
  }
  const values: readonly unknown[] = argumentsOf(call)
  return EXISTENCE_ASSERTS.has(name) || (INEQUALITY_ASSERTS.has(name) && isNothing(values[1])) ? EXISTENCE : OUTCOME
}

const isHelper = (callee: unknown): boolean =>
  calleeNames(callee)
    .slice(-1)
    .some((name: string): boolean => HELPER.test(name))

const judgeCall = (call: SyntaxNode): readonly Verdict[] => {
  const chain: Chain | undefined = chainOf(call.callee)
  if (chain !== undefined) {
    return judgeChain(chain, call)
  }
  const asserted: readonly Verdict[] = judgeAssert(call)
  return asserted.length > 0 || !isHelper(call.callee) ? asserted : OUTCOME
}

const childrenOf = (node: SyntaxNode, keys: SourceCode.VisitorKeys): readonly SyntaxNode[] =>
  (keys[node.type] ?? []).flatMap((key: string): readonly SyntaxNode[] => {
    const value: unknown = Reflect.get(node, key)
    if (isList(value)) {
      return value.filter((child: unknown): child is SyntaxNode => isSyntaxNode(child))
    }
    return isSyntaxNode(value) ? [value] : []
  })

/**
 * The verdict of every assertion a node holds, itself included, read top-down so no state outlives the call.
 * @param node the node to read.
 * @param keys the parser's visitor keys, which name the children of every node type it produces.
 * @returns the verdicts, in source order.
 */
const verdictsIn = (node: SyntaxNode, keys: SourceCode.VisitorKeys): readonly Verdict[] => {
  const inner: readonly Verdict[] = childrenOf(node, keys).flatMap((child: SyntaxNode): readonly Verdict[] =>
    verdictsIn(child, keys),
  )
  if (node.type !== 'CallExpression') {
    return inner
  }
  if (memberOf(node.callee, 'fc') === 'assert') {
    return inner.length === 0 ? OUTCOME : inner
  }
  return [...judgeCall(node), ...inner]
}

const isTestCall = (node: SyntaxNode): boolean => {
  const [first, ...rest]: readonly string[] = calleeNames(node.callee)
  return (first === 'it' || first === 'test') && rest.every((name: string): boolean => TEST_MODIFIERS.has(name))
}

const isFunction = (node: unknown): node is SyntaxNode =>
  field(node, 'type') === 'ArrowFunctionExpression' || field(node, 'type') === 'FunctionExpression'

/** Fails a test whose every assertion asks only whether a value exists or is truthy. */
export const existenceOnlyTest: Rule.RuleModule = {
  meta: {
    type: 'problem',
    schema: [],
    messages: {
      existenceOnly:
        'Every assertion of this test asks only whether a value exists or is truthy, which passes whatever ' +
        'value the code under test produced. Assert the outcome itself: the value, a property it must have, ' +
        'its length or its content. A boolean outcome is asserted with toBe(true), and a rendered element ' +
        'with toBeVisible() or its accessible name.',
    },
  },
  create(context: Rule.RuleContext): Rule.RuleListener {
    const check = (node: Rule.Node): void => {
      const body: SyntaxNode | undefined = argumentsOf(node).findLast(isFunction)
      if (body === undefined || !isTestCall(node)) {
        return
      }
      const verdicts: readonly Verdict[] = verdictsIn(body, context.sourceCode.visitorKeys)
      if (verdicts.length > 0 && verdicts.every((verdict: Verdict): boolean => verdict === 'existence')) {
        context.report({ node, messageId: 'existenceOnly' })
      }
    }
    return { CallExpression: check }
  },
}
