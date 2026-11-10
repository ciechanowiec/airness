import { Linter } from 'eslint'
import { describe, expect, it } from 'vitest'
import nodeConfig from '@/eslint.js'
import { testIdiomPlugins, testIdiomRules } from '@/eslint-core.js'
import { endToEndBlocks } from '@/eslint-next.js'
import { specFunctionLength } from '@/eslint-spec-length.js'

// Every function of a spec is held to the 50-line cap the core rule holds production code to, but the body of
// a group of tests, which is a list of declarations rather than a unit of logic.

const CAP: number = 50
const RULE: string = 'specs/function-length'

const linter: Linter = new Linter()

const reportedLines = (code: string): readonly number[] =>
  linter
    .verify(code, [
      {
        languageOptions: { ecmaVersion: 'latest', sourceType: 'module' },
        plugins: {
          specs: { rules: { 'function-length': specFunctionLength(CAP) } },
        },
        rules: { [RULE]: 'error' },
      },
    ])
    .map((message: Linter.LintMessage): number => message.line)

// A body of the given number of code lines, the opening and closing lines of the function included.
const statements = (lines: number): string => 'expect(1).toBe(1)\n'.repeat(lines - 2)

// A call up to its callback, such as `it('reads', ` or `beforeEach(`, then a callback of the given length.
const wrapped = (call: string, lines: number): string => `${call}() => {\n${statements(lines)}})\n`

describe('the functions a spec holds to the cap', () => {
  it('reports a test one line past the cap, at the test', () => {
    expect(reportedLines(wrapped("it('reads', ", CAP + 1))).toEqual([1])
  })

  it('passes a test at the cap', () => {
    expect(reportedLines(wrapped("it('reads', ", CAP))).toEqual([])
  })

  it('reports every spelling of a test and a hook', () => {
    const calls: readonly string[] = [
      "test('reads', ",
      "it.each([1])('reads %i', ",
      "it.concurrent('reads', ",
      'beforeEach(',
      'afterAll(',
    ]
    const reported: readonly (readonly number[])[] = calls.map((call: string): readonly number[] =>
      reportedLines(wrapped(call, CAP + 1)),
    )
    expect(reported).toEqual(calls.map((): readonly number[] => [1]))
  })

  it('reports a helper, written as a declaration or as a function expression', () => {
    const declaration: string = `function arrange() {\n${statements(CAP + 1)}}\n`
    const expression: string = `const arrange = function () {\n${statements(CAP + 1)}}\n`
    expect([reportedLines(declaration), reportedLines(expression)]).toEqual([[1], [1]])
  })

  it('counts neither a blank line nor a comment', () => {
    const padded: string = `it('reads', () => {\n${'// why\n\n'.repeat(CAP)}${statements(CAP)}})\n`
    expect(reportedLines(padded)).toEqual([])
  })
})

describe('the group bodies a spec leaves to the file cap', () => {
  it('passes every spelling of a group, while still reading the tests inside it', () => {
    const groups: readonly string[] = [
      "describe('reads', ",
      "suite('reads', ",
      "describe.each([1])('reads %i', ",
      "test.describe.serial('reads', ",
    ]
    const inner: string = `  it('reads', () => {\n${statements(CAP + 1)}  })\n`
    const reported: readonly (readonly number[])[] = groups.map((group: string): readonly number[] =>
      reportedLines(`${group}() => {\n${inner}${inner}})\n`),
    )
    expect(reported).toEqual(groups.map((): readonly number[] => [2, CAP + 3]))
  })
})

describe('the shipped configurations', () => {
  it('turns the rule on wherever the test idiom applies', () => {
    expect(testIdiomRules['airness-specs/function-length']).toBe('error')
    expect(Object.keys(testIdiomPlugins)).toEqual(['airness-specs'])
  })

  it('mounts the plugin in the unit test block and the end-to-end block', () => {
    const mounting: readonly unknown[] = [...nodeConfig, ...endToEndBlocks].filter(
      (block: unknown): boolean =>
        typeof block === 'object' &&
        block !== null &&
        'plugins' in block &&
        typeof block.plugins === 'object' &&
        block.plugins !== null &&
        'airness-specs' in block.plugins,
    )
    expect(mounting.length).toBeGreaterThanOrEqual(2)
  })
})
