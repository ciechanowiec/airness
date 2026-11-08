// One call out of a body, into the member's own source.
//
// A rule about what a body does reads the body, and a project rarely writes the defect where the rule
// looks: a boot hook hands its work to an imported seeder, and a route handler to a helper in `src/lib`.
// Both rules follow the call the same way - the name has to be imported, the module has to be one of the
// member's own files, and the function it names has to do the thing itself - so the reading lives here
// once. Each further hop multiplies both the resolution machinery and the false-positive surface with no
// evidence that it is needed, so the depth stops at one.

import type { SpecSource } from '@airness/node/axe-coverage'
import {
  balancedArguments,
  type Folded,
  NOT_FOUND,
  type ScanStep,
  scanDelimited,
  stripComments,
} from '@airness/node/source-text'
import { escapeForRegex } from '@airness/node/text-escapes'

// The names a body calls. The keywords are removed because `if (` and `while (` are indistinguishable
// from a call by this pattern, and a keyword can never name an import.
const CALLED_IDENTIFIER: RegExp = /\b([a-z_$][\w$]*)\s*\(/gi
const STATEMENT_KEYWORDS: ReadonlySet<string> = new Set([
  'await',
  'catch',
  'for',
  'function',
  'if',
  'return',
  'switch',
  'typeof',
  'while',
])

/**
 * The distinct names a body calls, in reading order.
 * @param body the text to read, with comments already stripped.
 * @returns each called name once, statement keywords excluded.
 */
export const calledNamesIn = (body: string): readonly string[] => [
  ...new Set(
    [...body.matchAll(CALLED_IDENTIFIER)]
      .map((match: RegExpExecArray): string => match[1] ?? '')
      .filter((name: string): boolean => !STATEMENT_KEYWORDS.has(name)),
  ),
]

// Every quantifier here is bounded by a literal that cannot itself match, so the scan is linear: a
// binding clause runs to the one brace that closes it, and a specifier to the one quote that ends it.
const IMPORT_BINDING: RegExp = /\bimport\s+\{([^}]+)\}\s+from\s*['"]([^'"]+)['"]/g

const boundNames = (clause: string): readonly string[] =>
  clause
    .split(',')
    .map((binding: string): string => binding.trim().split(/\s+/).at(-1) ?? '')
    .filter((name: string): boolean => name.length > 0)

/**
 * The module specifier a name is imported from.
 * @param code the importing file's text.
 * @param name the imported binding to find.
 * @returns the specifier, or undefined when the file imports no such name.
 */
export const moduleOfImport = (code: string, name: string): string | undefined =>
  [...code.matchAll(IMPORT_BINDING)]
    .filter((match: RegExpExecArray): boolean => boundNames(match[1] ?? '').includes(name))
    .map((match: RegExpExecArray): string | undefined => match[2])
    .at(0)

const SOURCE_EXTENSIONS: readonly string[] = ['.ts', '.tsx']
const ALIAS: string = '@/'
const ALIAS_ROOT: string = 'src/'

const directoryOf = (filePath: string): string => {
  const slash: number = filePath.lastIndexOf('/')
  return slash === NOT_FOUND ? '' : filePath.slice(0, slash)
}

// `.` and `..` resolved without node:path, which keeps this layer free of every runtime module.
const flattened = (segments: readonly string[]): readonly string[] =>
  segments.reduce<readonly string[]>((kept: readonly string[], segment: string): readonly string[] => {
    if (segment === '' || segment === '.') {
      return kept
    }
    return segment === '..' ? kept.slice(0, -1) : [...kept, segment]
  }, [])

// The path a specifier names before an extension is tried. A package specifier resolves to nothing,
// which is what keeps the rules inside the member's own source.
const resolvedBase = (specifier: string, fromPath: string): string | undefined => {
  if (specifier.startsWith(ALIAS)) {
    return `${ALIAS_ROOT}${specifier.slice(ALIAS.length)}`
  }
  return specifier.startsWith('.')
    ? flattened([...directoryOf(fromPath).split('/'), ...specifier.split('/')]).join('/')
    : undefined
}

/**
 * The repository-relative paths a specifier could name, in the order a resolver would try them.
 * @param specifier the module specifier as written.
 * @param fromPath the member-relative path of the importing file.
 * @returns the candidate paths, empty when the specifier names a package rather than project source.
 */
export const candidatePathsFor = (specifier: string, fromPath: string): readonly string[] => {
  const base: string | undefined = resolvedBase(specifier, fromPath)
  return base === undefined
    ? []
    : [...SOURCE_EXTENSIONS.map((extension: string): string => `${base}${extension}`), `${base}/index.ts`]
}

const ARROW: string = '=>'
// The two brackets around a parameter list, which balancedArguments leaves out of the list it returns.
const PARENTHESES: number = 2
const FUNCTION_EXPRESSION: RegExp = /^(?:async\s+)?function\b/
const ASYNC_PREFIX: RegExp = /^async\s*(?=[(a-z_$])/i
const IDENTIFIER: RegExp = /^[a-z_$][\w$]*/i
// A line that opens with a member access continues the expression above it rather than ending it.
const CONTINUATION: RegExp = /^\s*\??\./

const endsExpression = (text: string, step: ScanStep, endsAtLineBreak: boolean): boolean => {
  if (step.depth < 0) {
    return true
  }
  if (step.depth > 0) {
    return false
  }
  return (
    step.character === ',' ||
    step.character === ';' ||
    (endsAtLineBreak && step.character === '\n' && !CONTINUATION.test(text.slice(step.index + 1)))
  )
}

/**
 * Where a value written at the start of a text ends: the first comma or semicolon outside every bracket,
 * or the bracket that closes the construct holding it.
 * @param text the text the value opens.
 * @param endsAtLineBreak true when a statement may end at a line break, as a declaration's value does.
 * @returns the offset just past the value.
 */
export const valueEnd = (text: string, endsAtLineBreak: boolean = false): number =>
  scanDelimited<number>(
    text,
    0,
    (end: number, step: ScanStep): Folded<number> =>
      endsExpression(text, step, endsAtLineBreak) ? { state: step.index, stop: true } : { state: end, stop: false },
    text.length,
  )

/**
 * The body a function opens after its parameters: skipping them first is what keeps a destructured
 * parameter's brace from being read as the body.
 * @param source the text holding the function, with comments already stripped.
 * @param at an offset before the function's parameter list, such as its name's.
 * @returns the body, or undefined when no body follows.
 */
export const bodyAfterParameters = (source: string, at: number): string | undefined => {
  const open: number = source.indexOf('(', at)
  const parameters: string | undefined = open === NOT_FOUND ? undefined : balancedArguments(source, open)
  const from: number = parameters === undefined ? at : open + parameters.length + 1
  const brace: number = source.indexOf('{', from)
  return brace === NOT_FOUND ? undefined : balancedArguments(source, brace)
}

// Where an arrow's parameters end: after the balanced list, or after the one bare parameter.
const parametersEnd = (text: string): number | undefined => {
  if (text.startsWith('(')) {
    const parameters: string | undefined = balancedArguments(text, 0)
    return parameters === undefined ? undefined : parameters.length + PARENTHESES
  }
  return IDENTIFIER.exec(text)?.[0].length
}

const arrowBody = (text: string): string | undefined => {
  const unprefixed: string = text.replace(ASYNC_PREFIX, '')
  const from: number | undefined = parametersEnd(unprefixed)
  const arrow: number = from === undefined ? NOT_FOUND : unprefixed.indexOf(ARROW, from)
  if (arrow === NOT_FOUND) {
    return undefined
  }
  const rest: string = unprefixed.slice(arrow + ARROW.length).trimStart()
  return rest.startsWith('{') ? balancedArguments(rest, 0) : rest.slice(0, valueEnd(rest, true))
}

/**
 * The body of a function written as a value: a function expression, an arrow that opens a block, or an
 * arrow whose body is the one expression it returns, which is read as that expression.
 * @param value the text the value opens, with comments already stripped.
 * @returns the body, or undefined when the value is no function written in place.
 */
export const functionValueBody = (value: string): string | undefined => {
  const text: string = value.trimStart()
  return FUNCTION_EXPRESSION.test(text) ? bodyAfterParameters(text, 0) : arrowBody(text)
}

// A declaration of the name, exported or not: a module may declare the function and export it in a
// clause of its own, and a binding a file imports by this name is exported whichever way it was spelled.
const DECLARATION_PREFIX: string =
  String.raw`(?:^|[^\w$.])(?:export\s+)?(?:async\s+)?` + String.raw`(function\s*\*?\s*|(?:const|let|var)\s+)`

const declarationOf = (name: string): RegExp =>
  new RegExp(String.raw`${DECLARATION_PREFIX}${escapeForRegex(name)}(?![\w$])`)

// A declared binding's value starts after its `=`; the `=` of an arrow in its type annotation is not it.
const ASSIGNMENT: RegExp = /=(?!>)/

/**
 * The body of a function a module declares under a name.
 * @param source the module's text, with comments already stripped.
 * @param name the binding to read.
 * @returns the body, or undefined when the module declares no such function.
 */
export const functionBodyIn = (source: string, name: string): string | undefined => {
  const found: RegExpExecArray | null = declarationOf(name).exec(source)
  if (found === null) {
    return undefined
  }
  const end: number = found.index + found[0].length
  if (found[1]?.startsWith('function') === true) {
    return bodyAfterParameters(source, end)
  }
  const assignment: RegExpExecArray | null = ASSIGNMENT.exec(source.slice(end))
  return assignment === null ? undefined : functionValueBody(source.slice(end + assignment.index + 1))
}

/** A function one call reaches in another of the member's own modules. */
export interface ReachedFunction {
  /** The name the calling file imports it by. */
  readonly name: string
  /** The specifier the calling file imports it from, as written. */
  readonly specifier: string
  /** The member-relative path of the module that declares it. */
  readonly path: string
  /** That module's text, with comments stripped. */
  readonly module: string
  /** The function's own body. */
  readonly body: string
}

/**
 * The function an imported name reaches in the member's own source.
 * @param code the importing file's text, with comments already stripped.
 * @param name the imported binding.
 * @param fromPath the member-relative path of the importing file.
 * @param files every source file of the member, as read.
 * @returns the function, or undefined when the name is not imported from one of the member's own modules
 *   that declares it with a body.
 */
export const reachedFunction = (
  code: string,
  name: string,
  fromPath: string,
  files: readonly SpecSource[],
): ReachedFunction | undefined => {
  const specifier: string | undefined = moduleOfImport(code, name)
  const paths: readonly string[] = specifier === undefined ? [] : candidatePathsFor(specifier, fromPath)
  const target: SpecSource | undefined = files.find((file: SpecSource): boolean => paths.includes(file.path))
  if (specifier === undefined || target === undefined) {
    return undefined
  }
  const module: string = stripComments(target.source)
  const body: string | undefined = functionBodyIn(module, name)
  return body === undefined ? undefined : { name, specifier, path: target.path, module, body }
}

/**
 * Every function a body reaches in one call into the member's own source.
 * @param code the file's text, with comments already stripped, whose imports resolve the names.
 * @param body the part of that file whose calls are followed.
 * @param fromPath the member-relative path of the file.
 * @param files every source file of the member, as read.
 * @returns one entry per distinct name the body calls that resolves to such a function.
 */
export const reachedFunctions = (
  code: string,
  body: string,
  fromPath: string,
  files: readonly SpecSource[],
): readonly ReachedFunction[] =>
  calledNamesIn(body).flatMap((name: string): readonly ReachedFunction[] => {
    const reached: ReachedFunction | undefined = reachedFunction(code, name, fromPath, files)
    return reached === undefined ? [] : [reached]
  })
