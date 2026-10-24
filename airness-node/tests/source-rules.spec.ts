import { describe, expect, it } from 'vitest'
import { findSourceViolations } from '@/source-rules.js'
import type { SourceViolation } from '@/source-violation.js'

const sourceRulesOf = (source: string): readonly string[] =>
  findSourceViolations(source).map((violation: SourceViolation): string => violation.rule)

describe('no-deep-relative-imports', () => {
  it('flags a parent-relative import', () => {
    expect(sourceRulesOf("import { thing } from '../lib/thing'")).toEqual(['no-deep-relative-imports'])
  })

  it('flags a parent-relative import written across lines, as a side effect, or dynamically', () => {
    expect(sourceRulesOf("import {\n  thing,\n} from '../lib/thing'")).toEqual(['no-deep-relative-imports'])
    expect(sourceRulesOf("import '../lib/polyfill'")).toEqual(['no-deep-relative-imports'])
    expect(sourceRulesOf("const lib = await import('../lib/thing')")).toEqual(['no-deep-relative-imports'])
  })

  it('reports the line that carries the import', () => {
    expect(findSourceViolations("const a = 1\nimport { thing } from '../lib/thing'")[0]?.line).toBe(2)
  })

  it('accepts a same-directory import', () => {
    expect(sourceRulesOf("import { thing } from './thing'")).toEqual([])
  })

  it('reads code rather than prose', () => {
    expect(sourceRulesOf("// import { thing } from '../lib/thing'")).toEqual([])
  })

  it('reads code rather than a string that quotes an import', () => {
    expect(sourceRulesOf('const fixture = "import { thing } from \'../lib/thing\'"')).toEqual([])
  })

  it('exempts test helpers, and nothing a framework generates', () => {
    expect(sourceRulesOf("import { seed } from '../helpers/seed'")).toEqual([])
    expect(sourceRulesOf("import { importMap } from '../importMap'")).toEqual(['no-deep-relative-imports'])
  })

  it('reports an import after an apostrophe in JSX text', () => {
    const source: string = "export const A = () => <p>Don't</p>\nexport const load = () => import('../secret/B')\n"
    expect(findSourceViolations(source, 'src/a.tsx').map((violation) => violation.rule)).toEqual([
      'no-deep-relative-imports',
    ])
  })

  it('reports an import after a regular expression that holds a quote', () => {
    const source: string =
      "export const quoted = (c: string): boolean => {\n  return /[\"']/.test(c)\n}\nexport const x = import('../x')\n"
    expect(sourceRulesOf(source)).toEqual(['no-deep-relative-imports'])
  })

  it('reports an import inside a template interpolation', () => {
    // Spliced at the dollar sign, so the spec itself holds no placeholder inside a plain string.
    const source: string = ['export const text = `$', "{await import('../x')}`", ''].join('')
    expect(sourceRulesOf(source)).toEqual(['no-deep-relative-imports'])
  })

  it('exempts a helper reached from a nested spec directory', () => {
    expect(sourceRulesOf("import { seed } from '../../../helpers/seed'")).toEqual([])
  })

  it('reports a climb that lands inside source rather than on a helper root', () => {
    expect(sourceRulesOf("import { seed } from '../../src/helpers/seed'")).toEqual(['no-deep-relative-imports'])
    expect(sourceRulesOf("import { seed } from '../helpersOfMine/seed'")).toEqual(['no-deep-relative-imports'])
  })
})

describe('the secret-guard rules join the import rule', () => {
  it('reports a fail-open credential guard as a source rule', () => {
    expect(sourceRulesOf('if (secret && supplied !== secret) return unauthorized()')).toEqual([
      'no-fail-open-secret-guard',
    ])
  })
})
