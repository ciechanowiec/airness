// Security defects visible from source syntax alone, independent of any framework configuration.
//
// The rules here reject the shape of a fail-open credential guard rather than guessing whether a route
// is called cron, webhook, or callback. The same defect has two spellings, and each is invisible to the
// other's rule. A test of the credential's presence conjoined with a rejection makes the rejection
// disappear precisely when configuration is absent. Its mirror tests for absence and then ADMITS the
// caller, so the endpoint opens exactly when the credential is unset. Safe guards reject absence before comparing,
// or return success for a configured, matching credential and deny every other case.

import {
  balancedArguments,
  type Folded,
  lineOf,
  maskLiterals,
  type ScanStep,
  SOURCE_LINE_TERMINATOR,
  scanDelimited,
} from './source-text.js'
import type { SourceViolation } from './source-violation.js'

const IF_STATEMENT: RegExp = /\bif\s*\(/g

const PENULTIMATE: number = -2

const credentialSegments = (name: string): readonly string[] =>
  name
    .split('.')
    .at(-1)
    ?.replaceAll(/([a-z\d])([A-Z])/g, '$1 $2')
    .split(/[^a-z\d]+/i)
    .filter((segment: string): boolean => segment.length > 0)
    .map((segment: string): string => segment.toLowerCase()) ?? []

const isCredential = (name: string): boolean => {
  const segments: readonly string[] = credentialSegments(name)
  const last: string | undefined = segments.at(-1)
  const previous: string | undefined = segments.at(PENULTIMATE)
  return last === 'secret' || last === 'token' || (previous === 'api' && last === 'key')
}

const CONTINUATION_CHARACTERS: ReadonlySet<string> = new Set('!%&(*+,-./:<=>?[^|')

const canContinueExpression = (masked: string, start: number, newline: number): boolean => {
  const before: string = masked.slice(start, newline).trimEnd()
  if (/\b(?:return|throw)$/.test(before)) {
    return false
  }
  const after: string = masked.slice(newline + 1).trimStart()
  return (
    CONTINUATION_CHARACTERS.has(before.at(-1) ?? '') ||
    CONTINUATION_CHARACTERS.has(after[0] ?? '') ||
    /^(?:in|instanceof|as|satisfies)\b/.test(after)
  )
}

const endsStatement = (masked: string, start: number, step: ScanStep): boolean => {
  if (step.depth < 0) {
    return true
  }
  if (step.depth > 0) {
    return false
  }
  return (
    step.character === ';' ||
    (SOURCE_LINE_TERMINATOR.test(step.character) && !canContinueExpression(masked, start, step.index))
  )
}

const statementAt = (masked: string, start: number): string => {
  if (masked[start] === '{') {
    return balancedArguments(masked, start) ?? ''
  }
  if (!/^return\b/.test(masked.slice(start))) {
    const newline: number = masked.indexOf('\n', start)
    return masked.slice(start, newline === -1 ? masked.length : newline)
  }
  const end: number = scanDelimited(
    masked,
    start,
    (found: number, step: ScanStep): Folded<number> =>
      endsStatement(masked, start, step) ? { state: step.index, stop: true } : { state: found, stop: false },
    masked.length,
  )
  return masked.slice(start, end)
}

// A line terminator immediately after return ends the statement through automatic semicolon insertion.
const isSuccessfulReturn = (value: string): boolean => {
  const expression: string = value.trimStart()
  const leading: string = value.slice(0, value.length - expression.length)
  return !SOURCE_LINE_TERMINATOR.test(leading) && /^true\s*(?:[;}]|$)/.test(expression)
}

const containsRejection = (statement: string): boolean => {
  const parts: readonly string[] = statement.split(/\b(return|throw)\b/)
  return parts.some(
    (part: string, index: number): boolean =>
      part === 'throw' || (part === 'return' && !isSuccessfulReturn(parts[index + 1] ?? '')),
  )
}

const hasRejectionAfter = (masked: string, close: number): boolean => {
  const statementStart: number = masked.slice(close + 1).search(/\S/)
  if (statementStart < 0) {
    return false
  }
  const start: number = close + 1 + statementStart
  const statement: string = statementAt(masked, start)
  if (masked[start] !== '{' && !/^return\b/.test(statement)) {
    return /\b(?:return|throw)\b/.test(statement)
  }
  return containsRejection(statement)
}

// An operator is top level when no bracket is open at it, which is what separates `a || (b && c)` -
// two disjuncts - from `(a || b) && c`, a conjunction. Both characters of the operator are found at
// the first, because the scan asks whether the operator STARTS here.
const OPERATOR_WIDTH: number = 2

const topLevelOperators = (condition: string, operator: string): readonly number[] =>
  scanDelimited<readonly number[]>(
    condition,
    0,
    (found: readonly number[], step: ScanStep): Folded<readonly number[]> => ({
      state: step.depth === 0 && condition.startsWith(operator, step.index) ? [...found, step.index] : found,
      stop: false,
    }),
    [],
  )

// A credential named as a property chain, optional links included, read in the mask. A segment may be a
// string key in brackets, `process.env['CRON_SECRET']`, which the shipped `noPropertyAccessFromIndexSignature`
// makes the only spelling of an environment read; the mask has filled that key, so the shapes below locate
// the name there and the source at the same offsets says what the key is.
const IDENTIFIER: string = String.raw`[a-z_$][\w$]*`

const chainOf = (key: string): string =>
  String.raw`${IDENTIFIER}(?:\s*\??\.\s*${IDENTIFIER}|\s*(?:\?\.\s*)?\[\s*${key}\s*\])*`

const NAME: string = chainOf('x+')

// The same chain in the source, where a bracketed segment must hold a string literal rather than an expression.
const STRING_KEY: string = String.raw`(?:'[^'\n]*'|"[^"\n]*"|\`[^\`$\n]*\`)`

const SOURCE_NAME: RegExp = new RegExp(`^${chainOf(STRING_KEY)}$`, 'i')

// The flags of a shape: case-insensitive, and with the offsets of what it captured, which the source is read at.
const SHAPE_FLAGS: string = 'di'

// The name a shape captured in the mask, read from the source at the same offsets. Whitespace is dropped and
// optional links are folded to plain ones for the report: `?.` to `.`, and `?.[` to `[`.
const capturedName = (shape: RegExp, masked: string, source: string): string | undefined => {
  const span: readonly [number, number] | undefined = shape.exec(masked)?.indices?.[1]
  const name: string | undefined = span === undefined ? undefined : source.slice(span[0], span[1])
  return name !== undefined && SOURCE_NAME.test(name)
    ? name.replaceAll(/\s/g, '').replaceAll('?.[', '[').replaceAll('?.', '.')
    : undefined
}

const BARE_PRESENCE_SHAPES: readonly RegExp[] = [
  new RegExp(`^(${NAME})$`, SHAPE_FLAGS),
  new RegExp(String.raw`^!\s*!\s*(${NAME})$`, SHAPE_FLAGS),
  new RegExp(String.raw`^Boolean\s*\(\s*(${NAME})\s*\)$`, SHAPE_FLAGS),
]

// The first comparison operator, its longer spellings tried before the shorter ones they begin with.
const COMPARISON_OPERATOR: RegExp = /!==|===|!=|==|>=|<=|>|</

const MIRRORED: Readonly<Record<string, string>> = { '>': '<', '<': '>', '>=': '<=', '<=': '>=' }

interface Comparison {
  readonly operand: string
  /** The operand in the source, at the offsets it has in the mask. */
  readonly operandSource: string
  readonly operator: string
  readonly constant: string
}

// `maskLiterals` fills a string literal with filler of the same width, so the masked text locates the
// operands and the source text at the same offsets says which type a `typeof` comparison names.
const isConstant = (masked: string): boolean => /^(?:undefined|null|\d+|x+)$/.test(masked)

const comparisonIn = (masked: string, source: string): Comparison | undefined => {
  const found: RegExpExecArray | null = COMPARISON_OPERATOR.exec(masked)
  if (found === null) {
    return undefined
  }
  const operator: string = found[0]
  const left: string = masked.slice(0, found.index).trimEnd()
  const right: string = masked.slice(found.index + operator.length).trimStart()
  if (isConstant(right)) {
    return {
      operand: left,
      operandSource: source.slice(0, left.length),
      operator,
      constant: source.slice(source.length - right.length),
    }
  }
  return isConstant(left)
    ? {
        operand: right,
        operandSource: source.slice(source.length - right.length),
        operator: MIRRORED[operator] ?? operator,
        constant: source.slice(0, left.length),
      }
    : undefined
}

const STRING_LITERAL: RegExp = /^(['"`])(.*)\1$/s

const isInequality = (operator: string): boolean => operator === '!==' || operator === '!='

const isEquality = (operator: string): boolean => operator === '===' || operator === '=='

const isNonEmpty = ({ operator, constant }: Comparison): boolean =>
  (constant === '0' && (operator === '>' || isInequality(operator))) || (constant === '1' && operator === '>=')

const isDefined = ({ operator, constant }: Comparison): boolean =>
  isInequality(operator) && (constant === 'undefined' || constant === 'null')

const isDefinedByType = ({ operator, constant }: Comparison): boolean => {
  const type: string | undefined = STRING_LITERAL.exec(constant)?.[2]
  return (
    type !== undefined &&
    ((isEquality(operator) && type !== 'undefined') || (isInequality(operator) && type === 'undefined'))
  )
}

// The comparison forms of a presence test, each an operand shape and what it must be compared with: a length
// above zero, a `typeof` that names a type other than `'undefined'` or denies that one, and a value compared
// unequal to `undefined` or `null`. The length shape comes first, as a chain ending in `.length` is a value too.
const PRESENCE_COMPARISONS: readonly (readonly [RegExp, (comparison: Comparison) => boolean])[] = [
  [new RegExp(String.raw`^(${NAME})\s*\??\.\s*length$`, SHAPE_FLAGS), isNonEmpty],
  [new RegExp(String.raw`^typeof\s+(${NAME})$`, SHAPE_FLAGS), isDefinedByType],
  [new RegExp(`^(${NAME})$`, SHAPE_FLAGS), isDefined],
]

const comparedPresence = (comparison: Comparison): string | undefined => {
  const shape: readonly [RegExp, (comparison: Comparison) => boolean] | undefined = PRESENCE_COMPARISONS.find(
    ([operand]: readonly [RegExp, (comparison: Comparison) => boolean]): boolean => operand.test(comparison.operand),
  )
  return shape?.[1](comparison) === true
    ? capturedName(shape[0], comparison.operand, comparison.operandSource)
    : undefined
}

// The two characters a parenthesised conjunct adds around what it holds.
const PARENTHESES_WIDTH: number = 2

const unwrapped = (masked: string, source: string): readonly [string, string] => {
  const inner: string | undefined = masked.startsWith('(') ? balancedArguments(masked, 0) : undefined
  if (inner === undefined || inner.length + PARENTHESES_WIDTH !== masked.length) {
    return [masked, source]
  }
  // Trimmed by the mask's offsets, so a comment the mask blanked trims from the source text too.
  const start: number = inner.search(/\S|$/)
  const end: number = inner.trimEnd().length
  return unwrapped(inner.slice(start, end), source.slice(1, -1).slice(start, end))
}

// The credential a conjunct tests the presence of, in every spelling strict boolean expressions still
// allow: bare truthiness, `!!`, `Boolean()`, and the comparison forms above with either operand first.
const presentNameIn = (maskedConjunct: string, sourceConjunct: string): string | undefined => {
  const [masked, source] = unwrapped(maskedConjunct, sourceConjunct)
  const bare: string | undefined = BARE_PRESENCE_SHAPES.map((shape: RegExp): string | undefined =>
    capturedName(shape, masked, source),
  ).find((name: string | undefined): name is string => name !== undefined)
  const comparison: Comparison | undefined = bare === undefined ? comparisonIn(masked, source) : undefined
  return bare ?? (comparison === undefined ? undefined : comparedPresence(comparison))
}

// The operands of a condition's top-level `&&` or `||`, each read from the mask for structure and from the
// source at the same offsets for the literals the mask hides.
const operandsOf = (masked: string, source: string, operator: string): readonly (readonly [string, string])[] => {
  const cuts: readonly number[] = topLevelOperators(masked, operator)
  const ends: readonly number[] = [...cuts, masked.length]
  return [0, ...cuts.map((cut: number): number => cut + OPERATOR_WIDTH)].map(
    (start: number, position: number): readonly [string, string] => {
      const end: number = ends[position] ?? masked.length
      const leading: number = masked.slice(start, end).search(/\S|$/)
      const trailing: number = masked.slice(start, end).trimEnd().length
      return [masked.slice(start + leading, start + trailing), source.slice(start + leading, start + trailing)]
    },
  )
}

// A presence test guards something only beside the rejection it is conjoined with.
const MINIMUM_CONJUNCTS: number = 2

const guardedCredential = (masked: string, source: string): string | undefined => {
  const conjuncts: readonly (readonly [string, string])[] = operandsOf(masked, source, '&&')
  return conjuncts.length < MINIMUM_CONJUNCTS
    ? undefined
    : conjuncts
        .map(([maskedConjunct, sourceConjunct]: readonly [string, string]): string | undefined =>
          presentNameIn(maskedConjunct, sourceConjunct),
        )
        .find((name: string | undefined): name is string => name !== undefined && isCredential(name))
}

/** Report credential guards whose rejection disappears when the credential is absent. */
export const findFailOpenSecretGuards = (source: string): readonly SourceViolation[] => {
  const masked: string = maskLiterals(source)
  return [...masked.matchAll(IF_STATEMENT)].flatMap((match: RegExpExecArray): readonly SourceViolation[] => {
    const open: number = masked.indexOf('(', match.index)
    const condition: string | undefined = balancedArguments(masked, open)
    if (condition === undefined) {
      return []
    }
    const close: number = open + condition.length + 1
    const credential: string | undefined = guardedCredential(condition, source.slice(open + 1, close))
    return credential !== undefined && hasRejectionAfter(masked, close)
      ? [
          {
            line: lineOf(source, match.index),
            rule: 'no-fail-open-secret-guard',
            reason:
              `reject a missing ${credential} before comparing it; guarded by a test of its presence, ` +
              'the rejection is skipped when the credential is absent',
          },
        ]
      : []
  })
}

// The ways a value is tested for absence. `=== ''` is deliberately absent: `maskLiterals` fills a
// string literal INCLUDING its quotes, so an empty string is indistinguishable from an identifier by
// the time this reads the text. `.length === 0` expresses the same test and survives masking.
const ABSENCE_SHAPES: readonly RegExp[] = [
  new RegExp(String.raw`^!\s*(${NAME})$`, SHAPE_FLAGS),
  new RegExp(String.raw`^(${NAME})\s*===?\s*(?:undefined|null)$`, SHAPE_FLAGS),
  new RegExp(String.raw`^(${NAME})\s*\.\s*length\s*===?\s*0$`, SHAPE_FLAGS),
]

const absentNameIn = (masked: string, source: string): string | undefined =>
  ABSENCE_SHAPES.map((shape: RegExp): string | undefined => capturedName(shape, masked, source)).find(
    (name: string | undefined): name is string => name !== undefined,
  )

// The credential a condition admits on absence, or nothing when the condition is not that shape.
//
// A conjunction is refused outright: `if (secret === undefined && enabled)` narrows the branch to a
// case the project chose, and it is the fail-closed shape the sibling rule's tests already pin. Every
// disjunct must be an absence test, which is the conservative reading - `if (!secret || isDevelopment)`
// is fail-open too, but it is not PROVABLY this defect from text, and a false positive here costs more
// than the finding is worth.
const acceptedAbsence = (condition: string, source: string): string | undefined => {
  if (topLevelOperators(condition, '&&').length > 0) {
    return undefined
  }
  const names: readonly (string | undefined)[] = operandsOf(condition, source, '||').map(
    ([masked, disjunct]: readonly [string, string]): string | undefined => absentNameIn(masked, disjunct),
  )
  return names.includes(undefined)
    ? undefined
    : names.find((name: string | undefined): name is string => name !== undefined && isCredential(name))
}

// The returned VALUE decides, not the presence of a return: `if (!secret) { return unauthorized() }`
// refuses the caller and is correct, while `return true` admits them. `true` is not a string literal,
// so it survives masking intact and can be recognised here.
const ACCEPTANCE: RegExp = /\breturn\s+true\b/

const hasAcceptanceAfter = (masked: string, close: number): boolean => {
  const statementStart: number = masked.slice(close + 1).search(/\S/)
  return statementStart >= 0 && ACCEPTANCE.test(statementAt(masked, close + 1 + statementStart))
}

/**
 * Report guards that admit the caller when the credential is absent.
 *
 * The mirror of {@link findFailOpenSecretGuards}: that rule reads a rejection guarded by the
 * credential's own truthiness, this one an acceptance guarded by its absence. The two never report the
 * same line, because one requires a top-level `&&` in the condition and the other refuses it.
 * @param source the file's text.
 * @returns one violation per guard that opens on a missing credential.
 */
export const findAbsentSecretAcceptances = (source: string): readonly SourceViolation[] => {
  const masked: string = maskLiterals(source)
  return [...masked.matchAll(IF_STATEMENT)].flatMap((match: RegExpExecArray): readonly SourceViolation[] => {
    const open: number = masked.indexOf('(', match.index)
    const condition: string | undefined = balancedArguments(masked, open)
    if (condition === undefined) {
      return []
    }
    const close: number = open + condition.length + 1
    const credential: string | undefined = acceptedAbsence(condition, source.slice(open + 1, close))
    return credential !== undefined && hasAcceptanceAfter(masked, close)
      ? [
          {
            line: lineOf(source, match.index),
            rule: 'no-absent-secret-acceptance',
            reason:
              `refuse the caller when ${credential} is absent; this admits them instead, so the ` +
              'endpoint is open exactly when the credential is unset and no request is ever ' +
              'refused - reject the absence and report the misconfiguration',
          },
        ]
      : []
  })
}
