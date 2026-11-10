// Keep every handler a Payload configuration serves under `src/endpoints`, where the endpoint access rule reads it.
//
// `require-endpoint-access` decides by path, because a route handler is a layer whose caller is a stranger.
// A handler written in `src/lib` or `src/hooks` and wired through an `endpoints:` entry serves the same stranger,
// but its Local API calls were never judged, so an omitted `overrideAccess: false` ran as an administrator and
// served every document to an anonymous caller. This rule closes that gap from the wiring side: whatever an
// `endpoints:` entry serves must come from a module under `src/endpoints`, so the path the access rule reads is
// the path the handler really has.

import path from 'node:path'
import {
  balancedArguments,
  type Folded,
  lineOf,
  type ScanStep,
  scanDelimited,
  stripComments,
  topLevelSlice,
} from '@airness/node/source-text'
import { type FoundPayloadConfig, payloadConfigsIn } from './payload-configs.js'
import { BUILD_CONFIG_CALL, depthOneValue, type PayloadViolation } from './payload-source.js'

/** The directory every served handler lives under. */
const ENDPOINT_DIRECTORY: string = 'src/endpoints/'

const RULE: string = 'require-endpoint-module'

// The project alias Payload's templates configure, `@/*` for `./src/*`. Another alias cannot be resolved from the
// file alone, so it is not taken on trust: a handler imported through it is reported, and the relative or `@/`
// spelling repairs it.
const SOURCE_ALIAS: string = '@/'

// An entry the rule can follow: an identifier, optionally spread or read through a member chain. The root name is
// what an import binds.
const REFERENCE: RegExp = /^(?:\.\.\.)?\s*([A-Za-z_$][\w$]*)(?:\s*\.\s*[\w$]+)*\s*$/u

// The value of a `handler:` key, up to the next depth-one delimiter.
const HANDLER_REFERENCE: RegExp = /^\s*([A-Za-z_$][\w$]*)(?:\s*\.\s*[\w$]+)*\s*(?=[,}]|$)/u

const SHORTHAND_HANDLER: RegExp = /(?:^|,)\s*handler\s*(?:,|$)/u

const IMPORT_KEYWORD: RegExp = /\bimport\b/gu

const QUOTE: RegExp = /['"]/u

const WHITESPACE_RUN: RegExp = /\s+/gu

const FROM_SUFFIX: string = ' from'

const NAMESPACE_BINDING: RegExp = /\* ?as ([A-Za-z_$][\w$]*)/u

const DEFAULT_BINDING: RegExp = /^([A-Za-z_$][\w$]*)(?: ?,|$)/u

/** One element of an `endpoints:` array, with the offset it starts at in the stripped file. */
interface Entry {
  readonly text: string
  readonly start: number
}

// The local names one import clause binds, its whitespace already collapsed: a default, a namespace, and each
// named binding under its local name.
const bindingsOf = (clause: string): readonly string[] => {
  const open: number = clause.indexOf('{')
  const named: readonly string[] = (open === -1 ? '' : clause.slice(open + 1, clause.indexOf('}', open)))
    .split(',')
    .map(
      (binding: string): string =>
        binding
          .trim()
          .replace(/^type /u, '')
          .split(' as ')
          .at(-1)
          ?.trim() ?? '',
    )
  return [DEFAULT_BINDING.exec(clause)?.[1], NAMESPACE_BINDING.exec(clause)?.[1], ...named].filter(
    (binding: string | undefined): binding is string => binding !== undefined && binding !== '',
  )
}

// One `import <clause> from '<specifier>'` statement starting at `at`, read up to its first quote. A clause holds
// no quote, so a side-effect import (`import 'x'`) yields nothing and cannot be read into the next statement; a
// type-only import binds nothing a handler can be.
const importAt = (code: string, at: number): readonly [string, string][] => {
  const rest: string = code.slice(at + 'import'.length)
  const quoteAt: number = rest.search(QUOTE)
  const clauseText: string = rest.slice(0, Math.max(quoteAt, 0)).replaceAll(WHITESPACE_RUN, ' ').trim()
  if (quoteAt === -1 || !clauseText.endsWith(FROM_SUFFIX) || clauseText.startsWith('type ')) {
    return []
  }
  const quote: string = rest.charAt(quoteAt)
  const specifier: string = rest.slice(quoteAt + 1, rest.indexOf(quote, quoteAt + 1))
  return bindingsOf(clauseText.slice(0, -FROM_SUFFIX.length)).map((binding: string): [string, string] => [
    binding,
    specifier,
  ])
}

// Every local name the file imports, mapped to the specifier it comes from.
const importsOf = (code: string): ReadonlyMap<string, string> =>
  new Map(
    [...code.matchAll(IMPORT_KEYWORD)].flatMap((match: RegExpExecArray): readonly [string, string][] =>
      importAt(code, match.index),
    ),
  )

// The member-relative path a specifier names, or undefined when it is neither relative nor the source alias.
const resolvedSpecifier = (filePath: string, specifier: string): string | undefined => {
  if (specifier.startsWith(SOURCE_ALIAS)) {
    return `src/${specifier.slice(SOURCE_ALIAS.length)}`
  }
  return specifier.startsWith('.') ? path.posix.join(path.posix.dirname(filePath), specifier) : undefined
}

const isServedFromEndpoints = (filePath: string, imports: ReadonlyMap<string, string>, name: string): boolean => {
  const specifier: string | undefined = imports.get(name)
  const resolved: string | undefined = specifier === undefined ? undefined : resolvedSpecifier(filePath, specifier)
  return resolved !== undefined && `${resolved}/`.startsWith(ENDPOINT_DIRECTORY)
}

// The depth-one commas of an array, which separate its entries. Starting on the opener puts them at depth one.
const entriesOf = (code: string, open: number): readonly Entry[] => {
  const inner: string | undefined = balancedArguments(code, open)
  if (inner === undefined) {
    return []
  }
  const commas: readonly number[] = scanDelimited<readonly number[]>(
    code,
    open,
    (found: readonly number[], step: ScanStep): Folded<readonly number[]> =>
      step.character === ',' && step.depth === 1
        ? { state: [...found, step.index], stop: false }
        : { state: found, stop: step.depth === 0 },
    [],
  )
  const close: number = open + inner.length + 1
  return [open, ...commas]
    .map(
      (from: number, index: number): Entry => ({
        text: code.slice(from + 1, commas[index] ?? close),
        start: from + 1,
      }),
    )
    .filter((entry: Entry): boolean => entry.text.trim() !== '')
}

// The name an entry is served through, or undefined when the entry is written in place.
const servedName = (entry: string): string | undefined => {
  const trimmed: string = entry.trim()
  if (!trimmed.startsWith('{')) {
    return REFERENCE.exec(trimmed)?.[1]
  }
  if (SHORTHAND_HANDLER.test(topLevelSlice(trimmed))) {
    return 'handler'
  }
  const handler: string | undefined = depthOneValue(trimmed, 'handler')
  return handler === undefined ? undefined : HANDLER_REFERENCE.exec(handler)?.[1]
}

// Where an `endpoints:` value opens in the stripped file, from a configuration body that starts at `bodyStart`.
const endpointsValueAt = (body: string, bodyStart: number): Entry | undefined => {
  const value: string | undefined = depthOneValue(body, 'endpoints')
  return value === undefined ? undefined : { text: value, start: bodyStart + body.length - value.length }
}

// Every `buildConfig({ ... })` root, read like a collection or a global: it declares its own `endpoints:`.
const buildConfigBodies = (code: string): readonly Pick<FoundPayloadConfig, 'body' | 'bodyStart'>[] =>
  [...code.matchAll(/\bbuildConfig\(/gu)].flatMap(
    (match: RegExpExecArray): readonly Pick<FoundPayloadConfig, 'body' | 'bodyStart'>[] => {
      const argumentText: string | undefined = balancedArguments(code, match.index + BUILD_CONFIG_CALL.length - 1)
      if (argumentText?.trimStart().startsWith('{') !== true) {
        return []
      }
      const bodyStart: number = match.index + BUILD_CONFIG_CALL.length + argumentText.indexOf('{')
      const inner: string | undefined = balancedArguments(code, bodyStart)
      return inner === undefined ? [] : [{ body: `{${inner}}`, bodyStart }]
    },
  )

const violationAt = (code: string, start: number): PayloadViolation => ({
  line: lineOf(code, start),
  rule: RULE,
  reason:
    `serve this endpoint from a module under ${ENDPOINT_DIRECTORY} and import it by a relative or ` +
    `${SOURCE_ALIAS} path; require-endpoint-access judges only that directory, so a handler written anywhere ` +
    'else runs its Local API calls unjudged for whoever calls the route',
})

// The entries of one `endpoints:` value that are not served from `src/endpoints`. A bare reference stands for the
// whole array; `false`, which turns a collection's default endpoints off, serves nothing.
const misplacedIn = (
  code: string,
  filePath: string,
  imports: ReadonlyMap<string, string>,
  value: Entry,
): readonly PayloadViolation[] => {
  const leading: number = value.text.length - value.text.trimStart().length
  const valueOpen: number = value.start + leading
  const trimmed: string = value.text.trimStart()
  if (trimmed.startsWith('false')) {
    return []
  }
  const entries: readonly Entry[] = trimmed.startsWith('[')
    ? entriesOf(code, valueOpen)
    : [{ text: trimmed.split(/[,}]/u, 1)[0] ?? '', start: valueOpen }]
  return entries
    .filter((entry: Entry): boolean => {
      const name: string | undefined = servedName(entry.text)
      return name === undefined || !isServedFromEndpoints(filePath, imports, name)
    })
    .map((entry: Entry): PayloadViolation => violationAt(code, entry.start + entry.text.search(/\S/u)))
}

/**
 * Every endpoint a collection, a global or the root configuration wires to a handler outside `src/endpoints`.
 *
 * A file under `src/endpoints` is the module the rule asks for, so it is left to `require-endpoint-access`, which
 * judges every Local API call in it. Everywhere else an `endpoints:` entry must be a name imported from that
 * directory, or an object whose `handler` is one; an inline handler or one imported from anywhere else is reported.
 * @param filePath the member-relative path of the file.
 * @param source the file's text.
 * @returns one violation per misplaced endpoint.
 */
export const findMisplacedEndpoints = (filePath: string, source: string): readonly PayloadViolation[] => {
  if (filePath.startsWith(ENDPOINT_DIRECTORY)) {
    return []
  }
  const code: string = stripComments(source)
  const imports: ReadonlyMap<string, string> = importsOf(code)
  return [...payloadConfigsIn(code), ...buildConfigBodies(code)].flatMap(
    (config: Pick<FoundPayloadConfig, 'body' | 'bodyStart'>): readonly PayloadViolation[] => {
      const value: Entry | undefined = endpointsValueAt(config.body, config.bodyStart)
      return value === undefined ? [] : misplacedIn(code, filePath, imports, value)
    },
  )
}
