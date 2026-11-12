// The syntax the spec rules of this package read: the parts of a node each needs, and the names a call is made
// through, such as `test.describe.serial` or `it.each(rows)`, which decide whether a callback is a test, a group
// of tests or neither.

/** The parts of a syntax node a spec rule reads, each as it arrives: unknown until a guard narrows it. */
export interface SyntaxNode {
  readonly type: string
  readonly name?: unknown
  readonly object?: unknown
  readonly property?: unknown
  readonly callee?: unknown
  readonly arguments?: unknown
}

/**
 * A guard rather than an assertion: the callee's parts arrive typed by ESTree without the parent link a rule's
 * own node carries, and the type coverage this package holds counts an assertion as uncovered.
 * @param value anything a node holds.
 * @returns whether it is a syntax node.
 */
export const isSyntaxNode = (value: unknown): value is SyntaxNode =>
  typeof value === 'object' && value !== null && 'type' in value && typeof value.type === 'string'

/** Whether a value is a list, such as the arguments of a call. */
export const isList = (value: unknown): value is readonly unknown[] => Array.isArray(value)

/** The name an identifier carries, as a list of none or one. */
const identifierName = (value: unknown): readonly string[] =>
  isSyntaxNode(value) && value.type === 'Identifier' && typeof value.name === 'string' ? [value.name] : []

/**
 * The names a call's callee is built from: its identifier, each property it reads and each call a table form
 * makes first, so `test.describe.serial` gives `test`, `describe` and `serial`.
 * @param callee the callee of a call, or any node it is built from.
 * @returns the names, outermost last.
 */
export const calleeNames = (callee: unknown): readonly string[] => {
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
