// The source rules that hold in any JS/TS package, whatever framework it uses. A framework's own source
// rules stay with its stack layer, so no rule here asks which framework the package is built on.

import type { TextRange } from 'typescript'
import { literalRanges } from './source-comments.js'
import { findAbsentSecretAcceptances, findFailOpenSecretGuards } from './source-security.js'
import { lineOf, stripComments } from './source-text.js'
import type { SourceViolation } from './source-violation.js'

// Anchored on the keyword that immediately precedes the specifier rather than on the statement that
// opens it. Requiring the whole statement to fit on one line missed a multi-line brace list, which is
// how a long import is normally written, and requiring `from` missed both a side-effect import and the
// dynamic `import('../x')` form. Comments are already blanked, so a `from` reached here is code.
const RELATIVE_IMPORT: RegExp = /\b(?:from|import)\s*(?:\(\s*)?['"](\.\.\/[^'"]*)['"]/g
// Test helpers live outside the `@/` alias root. The exemption is anchored at a path boundary so
// `../helpersOfMine` is not excused by `../helpers`.
//
// The ascent repeats, because its LENGTH was never part of that reason. Anchored at exactly one `../`,
// the exemption reached a spec sitting directly under `tests/` and no deeper - so a suite whose
// directories mirror `src/`, which is the ordinary layout, could not import the helpers the exemption
// exists to admit. What still discriminates is the segment the climb LANDS on: `../../../helpers/x` is
// a helper root reached from depth, while `../../../src/helpers/x` climbed into source and is reported.
const RELATIVE_IMPORT_EXEMPT: RegExp = /^(?:\.\.\/)+helpers(?:\/|$)/

// An import written inside a string - a fixture a spec lints, a message naming the construct - is data.
// Whether the keyword lies inside a literal is decided by the TypeScript parser, which reads JSX text, a
// regex and a template interpolation correctly, so an apostrophe or a quote in one of them never hides the
// imports that follow it.
const isInLiteral = (literals: readonly TextRange[], index: number): boolean =>
  literals.some((literal: TextRange): boolean => index >= literal.pos && index < literal.end)

const findDeepRelativeImports = (source: string, file: string): readonly SourceViolation[] => {
  const literals: readonly TextRange[] = literalRanges(source, file)
  return [...stripComments(source).matchAll(RELATIVE_IMPORT)]
    .filter((match: RegExpExecArray): boolean => !isInLiteral(literals, match.index))
    .flatMap((match: RegExpExecArray): readonly SourceViolation[] => {
      const specifier: string = match[1] ?? ''
      return RELATIVE_IMPORT_EXEMPT.test(specifier)
        ? []
        : [
            {
              line: lineOf(source, match.index),
              rule: 'no-deep-relative-imports',
              reason: `use the "@/" path alias instead of the parent-relative import "${specifier}"`,
            },
          ]
    })
}

/**
 * The rules that are about the language rather than about a framework.
 *
 * Reaching for a parent-relative import instead of the path alias is a defect in any package, and so is
 * a credential guard that opens when the credential is unset.
 * @param source the file's text.
 * @param file the file's name, which decides whether angle brackets are JSX.
 * @returns one violation per offending line.
 */
export const findSourceViolations = (source: string, file: string = 'source.ts'): readonly SourceViolation[] => [
  ...findDeepRelativeImports(source, file),
  ...findFailOpenSecretGuards(source),
  ...findAbsentSecretAcceptances(source),
]
