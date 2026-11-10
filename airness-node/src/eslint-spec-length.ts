// The function-length cap, as it binds in a spec.
//
// The core `max-lines-per-function` rule cannot tell one function from another, and a spec has one function
// the cap was never aimed at: the body of a `describe`, which is a list of independent declarations rather than
// a unit of logic. Switching the core rule off for specs to spare that body also spared every test, hook and
// helper beside it, so a 150-line `it` passed in TypeScript where the same test failed in Java. This rule
// measures every function of a spec as the core rule does, skipping blank and comment-only lines, and passes
// over a group body alone.

import type { AST, Rule, SourceCode } from 'eslint'

// The calls whose callback is a group of tests rather than a test: Vitest's `describe` and its `suite` alias,
// with every modifier and table form, such as `describe.each(rows)(title, body)`, and Playwright's
// `test.describe` with its own modifiers, such as `test.describe.serial(title, body)`.
const GROUPS: ReadonlySet<string> = new Set<string>(['describe', 'suite'])

// The parts of a syntax node this rule reads, each as it arrives: unknown until a guard narrows it.
interface SyntaxNode {
  readonly type: string
  readonly name?: unknown
  readonly object?: unknown
  readonly property?: unknown
  readonly callee?: unknown
  readonly arguments?: unknown
}

// A guard rather than an assertion: the callee's parts arrive typed by ESTree without the parent link a rule's
// own node carries, and the type coverage this package holds counts an assertion as uncovered.
const isSyntaxNode = (value: unknown): value is SyntaxNode =>
  typeof value === 'object' && value !== null && 'type' in value && typeof value.type === 'string'

const isList = (value: unknown): value is readonly unknown[] => Array.isArray(value)

const identifierName = (value: unknown): readonly string[] =>
  isSyntaxNode(value) && value.type === 'Identifier' && typeof value.name === 'string' ? [value.name] : []

/**
 * The names a call's callee is built from: its identifier, each property it reads and each call a table form
 * makes first, so `test.describe.serial` gives `test`, `describe` and `serial`.
 * @param callee the callee of a call, or any node it is built from.
 * @returns the names, outermost last.
 */
const calleeNames = (callee: unknown): readonly string[] => {
  if (!isSyntaxNode(callee)) {
    return []
  }
  switch (callee.type) {
    case 'MemberExpression': {
      return [...calleeNames(callee.object), ...identifierName(callee.property)]
    }
    case 'CallExpression': {
      return calleeNames(callee.callee)
    }
    default: {
      return identifierName(callee)
    }
  }
}

const isGroupBody = (node: Rule.Node): boolean => {
  const parent: unknown = node.parent
  return (
    isSyntaxNode(parent) &&
    parent.type === 'CallExpression' &&
    isList(parent.arguments) &&
    parent.arguments.includes(node) &&
    calleeNames(parent.callee).some((name: string): boolean => GROUPS.has(name))
  )
}

const span = (first: number, last: number): readonly number[] =>
  Array.from({ length: last - first + 1 }, (_unused: unknown, offset: number): number => first + offset)

// The lines a code token stands on. A comment is no token, and a blank line holds none, so both are skipped.
const codeLines = (sourceCode: SourceCode, node: Rule.Node): number =>
  new Set<number>(
    sourceCode
      .getTokens(node)
      .flatMap((token: AST.Token): readonly number[] => span(token.loc.start.line, token.loc.end.line)),
  ).size

/**
 * Caps every function of a spec at the given number of code lines, but the body of a group of tests.
 * @param max the most code lines a function may span.
 * @returns the rule.
 */
export const specFunctionLength = (max: number): Rule.RuleModule => ({
  meta: {
    type: 'suggestion',
    schema: [],
    messages: {
      tooLong:
        'This function spans {{lines}} code lines; a spec function is capped at {{max}}, as every function is. ' +
        'Split the test by the behaviors it checks, or move its arrangement into a named helper. Only the ' +
        'body of a describe or suite is exempt.',
    },
  },
  create(context: Rule.RuleContext): Rule.RuleListener {
    const check = (node: Rule.Node): void => {
      const lines: number = codeLines(context.sourceCode, node)
      if (lines > max && !isGroupBody(node)) {
        context.report({
          node,
          messageId: 'tooLong',
          data: { lines: String(lines), max: String(max) },
        })
      }
    }
    return {
      ArrowFunctionExpression: check,
      FunctionExpression: check,
      FunctionDeclaration: check,
    }
  },
})
