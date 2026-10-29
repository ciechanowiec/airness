import { describe, expect, it } from 'vitest'
import { asFindings } from '@/tool-findings.js'

describe('asFindings', () => {
  it('keeps the non-blank lines of the output', () => {
    expect(asFindings('first\n\n  \nsecond\n')).toStrictEqual(['first', 'second'])
  })

  it('keeps only the last forty lines of a long output', () => {
    const lines: readonly string[] = Array.from(
      { length: 50 },
      (_unused: unknown, index: number): string => `line ${String(index)}`,
    )
    const findings: readonly string[] = asFindings(lines.join('\n'))
    expect(findings).toHaveLength(40)
    expect(findings.at(-1)).toBe('line 49')
  })

  it('cuts a line too long to read, such as minified code in a stack trace', () => {
    expect(asFindings('x'.repeat(1000))[0]).toBe(`${'x'.repeat(400)} [cut]`)
  })

  it('names a silent failure rather than reporting no finding', () => {
    expect(asFindings('')).toStrictEqual(['the tool printed nothing to explain its failure'])
  })
})
