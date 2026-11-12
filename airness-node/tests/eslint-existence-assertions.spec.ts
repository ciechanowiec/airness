import { Linter } from 'eslint'
import tseslint from 'typescript-eslint'
import { describe, expect, it } from 'vitest'
import nodeConfig from '@/eslint.js'
import { testIdiomPlugins, testOutcomeRules } from '@/eslint-core.js'
import { existenceOnlyTest } from '@/eslint-existence-assertions.js'
import { endToEndBlocks } from '@/eslint-next.js'

// A test fails when every assertion it makes asks only whether a value exists or is truthy, and passes once one
// of them judges an outcome.

const RULE: string = 'specs/no-existence-only-test'

const linter: Linter = new Linter()

const reportedLines = (code: string): readonly number[] =>
  linter
    .verify(code, [
      {
        languageOptions: { ecmaVersion: 'latest', sourceType: 'module', parser: tseslint.parser },
        plugins: { specs: { rules: { 'no-existence-only-test': existenceOnlyTest } } },
        rules: { [RULE]: 'error' },
      },
    ])
    .map((message: Linter.LintMessage): number => message.line)

// A test whose body is the given statements, on the line after its title.
const testOf = (...statements: readonly string[]): string => `it('reads', async () => {\n${statements.join('\n')}\n})\n`

const isReported = (...statements: readonly string[]): boolean => reportedLines(testOf(...statements)).length > 0

const EXISTENCE_SPELLINGS: readonly string[] = [
  'expect(users).toBeDefined()',
  'expect(users).toBeTruthy()',
  'expect(users).not.toBeUndefined()',
  'expect(users).not.toBeFalsy()',
  'expect(users).not.toBeNull()',
  'expect(users).not.toBe(undefined)',
  'expect(users).not.toEqual(undefined)',
  'expect(users).not.toStrictEqual(null)',
  'expect(users).not.toBe(void 0)',
  "expect(users)['toBeDefined']()",
  'expect(users).toEqual(expect.anything())',
  'expect(users).toStrictEqual(expect.anything())',
  "expect(users, 'the users were read').toBeDefined()",
  'expect.soft(users).toBeDefined()',
  'expect.poll(() => users).toBeTruthy()',
  'await expect(read()).resolves.toBeDefined()',
  'await expect(read()).resolves.not.toBeNull()',
  'expect(Boolean(users)).toBe(true)',
  'expect(!!users).toEqual(true)',
  'expect(Boolean(users)).not.toBe(false)',
  'expect(users !== undefined).toBe(true)',
  'expect(undefined !== users).toBe(true)',
  'expect(users != null).toStrictEqual(true)',
  "expect(typeof users !== 'undefined').toBe(true)",
  "expect('undefined' !== typeof users).toBe(true)",
  'expect(users === undefined).toBe(false)',
  'expect(users == null).toBeFalsy()',
  'expect(!users).toBe(false)',
  'expect(!users).not.toBeTruthy()',
  'assert(users)',
  'assert.ok(users)',
  'assert.isOk(users)',
  'assert.exists(users)',
  'assert.isDefined(users)',
  'assert.isNotNull(users)',
  'assert.isNotUndefined(users)',
  'assert.notEqual(users, undefined)',
  'assert.notStrictEqual(users, null)',
]

const OUTCOME_SPELLINGS: readonly string[] = [
  'expect(users).toEqual([alice])',
  'expect(users).toHaveLength(2)',
  'expect(users).toBeUndefined()',
  'expect(users).toBeNull()',
  'expect(users).toBeFalsy()',
  'expect(users).toBe(true)',
  'expect(users).not.toBe(alice)',
  'expect(users).not.toEqual(expect.anything())',
  'expect(users).toEqual(expect.any(Array))',
  'expect(read).toThrow()',
  'await expect(read()).rejects.toBeDefined()',
  'await expect(read()).rejects.toBeTruthy()',
  'expect(users === undefined).toBe(true)',
  'expect(!users).toBeTruthy()',
  'expect(Boolean(users)).toBe(false)',
  "expect('alice' in users).toBe(true)",
  'expect(users.length > 0).toBe(true)',
  'assert.strictEqual(users, alice)',
  'assert.notEqual(users, alice)',
  'expectSweptPage(page)',
  'await expectNoAxeDefects(page)',
  'assertSorted(users)',
  'scanner.expectClean(page)',
  'fc.assert(fc.property(fc.integer(), (value) => value >= 0))',
]

describe('a test whose every assertion asks only whether a value exists', () => {
  it.each(EXISTENCE_SPELLINGS)('is reported at the test when it asserts only %s', (assertion: string) => {
    expect(reportedLines(testOf(assertion))).toEqual([1])
  })

  it('is reported when several existence assertions stand together', () => {
    expect(isReported('expect(users).toBeDefined()', 'expect(users[0]).toBeTruthy()')).toBe(true)
  })

  it('is reported for every spelling of a test', () => {
    const tests: readonly string[] = [
      "it('reads', () => {",
      "test('reads', () => {",
      "it.each([1])('reads %i', (value) => {",
      "test.for([1])('reads %i', (value) => {",
      "it.concurrent('reads', function () {",
      "test('reads', { timeout: 10 }, () => {",
      "test('reads', async ({ page }) => {",
    ]
    const reported: readonly (readonly number[])[] = tests.map((opening: string): readonly number[] =>
      reportedLines(`${opening}\n  expect(users).toBeDefined()\n})\n`),
    )
    expect(reported).toEqual(tests.map((): readonly number[] => [1]))
  })

  it('reads the assertions of a property, a step and a nested callback as the test own', () => {
    const nested: readonly string[] = [
      'fc.assert(fc.property(fc.integer(), (value) => { expect(read(value)).toBeDefined() }))',
      "await test.step('reads', async () => { expect(users).toBeTruthy() })",
      'for (const user of users) { expect(user).toBeDefined() }',
      '[alice].forEach((user) => { expect(user).not.toBeNull() })',
    ]
    expect(nested.map((statement: string): boolean => isReported(statement))).toEqual(nested.map((): boolean => true))
  })
})

describe('a test that judges an outcome', () => {
  it.each(OUTCOME_SPELLINGS)('passes with %s beside an existence check', (assertion: string) => {
    expect(reportedLines(testOf('expect(users).toBeDefined()', assertion))).toEqual([])
  })

  it('passes when an assertion inside a property judges an outcome', () => {
    expect(isReported('fc.assert(fc.property(fc.integer(), (value) => { expect(read(value)).toBe(value) }))')).toBe(
      false,
    )
  })

  it('leaves a test that reaches no assertion to the rule that demands one', () => {
    expect(isReported('read()')).toBe(false)
  })
})

describe('calls that declare no test', () => {
  it('passes the existence checks of a group, a hook, a step and a helper', () => {
    const declarations: readonly string[] = [
      "describe('reads', () => { expect(users).toBeDefined() })\n",
      "test.describe('reads', () => { expect(users).toBeDefined() })\n",
      'beforeEach(() => { expect(users).toBeDefined() })\n',
      'test.beforeEach(async () => { expect(users).toBeDefined() })\n',
      "test.step('reads', () => { expect(users).toBeDefined() })\n",
      'const readUsers = () => { expect(users).toBeDefined() }\n',
      "it.todo('reads')\n",
    ]
    expect(declarations.map((code: string): readonly number[] => reportedLines(code))).toEqual(
      declarations.map((): readonly number[] => []),
    )
  })
})

// Whether a flat-config block holds the given key under the given setting, such as a rule under `rules`.
const hasEntry = (block: unknown, setting: string, key: string): boolean => {
  const value: unknown = typeof block === 'object' && block !== null ? Reflect.get(block, setting) : undefined
  return typeof value === 'object' && value !== null && Object.hasOwn(value, key)
}

describe('the shipped configurations', () => {
  it('turns the rule on and mounts it in the unit test block and the end-to-end block', () => {
    expect(testOutcomeRules['airness-specs/no-existence-only-test']).toBe('error')
    const enabling: readonly unknown[] = [...nodeConfig, ...endToEndBlocks].filter(
      (block: unknown): boolean =>
        hasEntry(block, 'rules', 'airness-specs/no-existence-only-test') && hasEntry(block, 'plugins', 'airness-specs'),
    )
    expect(enabling.length).toBeGreaterThanOrEqual(2)
    expect(Object.keys(testIdiomPlugins)).toEqual(['airness-specs'])
  })
})
