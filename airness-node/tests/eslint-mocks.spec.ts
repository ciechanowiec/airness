import { Linter } from 'eslint'
import tseslint from 'typescript-eslint'
import { describe, expect, it } from 'vitest'
import { NO_MOCK_PROPERTIES, NO_MOCK_SYNTAX } from '@/eslint-mocks.js'

// The mock ban, linted on its own: Vitest's mocking methods are refused under every name the language lets a
// spec reach them by, and the utility object stays usable for what is not a mock.

const linter: Linter = new Linter({ configType: 'flat' })

const rulesOf = (code: string): readonly string[] =>
  linter
    .verify(
      code,
      [
        {
          files: ['**/*.ts'],
          languageOptions: { parser: tseslint.parser },
          rules: {
            'no-restricted-properties': ['error', ...NO_MOCK_PROPERTIES],
            'no-restricted-syntax': ['error', ...NO_MOCK_SYNTAX],
          },
        },
      ],
      'unit.spec.ts',
    )
    .map((message: Linter.LintMessage): string => message.ruleId ?? message.message)

describe('the mock ban', () => {
  it.each([
    "import { vi } from 'vitest'; vi.fn()",
    "import { vi } from 'vitest'; vi['spyOn'](clock, 'now')",
    "import { vitest } from 'vitest'; vitest.fn()",
    "import { vitest } from 'vitest'; vitest.spyOn(clock, 'now')",
    "import { vitest } from 'vitest'; vitest.mock('./clock')",
  ])('refuses a mocking method of either export: %s', (code: string) => {
    expect(rulesOf(code)).toEqual(['no-restricted-properties'])
  })

  it.each([
    "import { vi as double } from 'vitest'",
    "import { vitest as double } from 'vitest'",
    "import { 'vi' as double } from 'vitest'",
  ])('refuses the utility object imported under another name: %s', (code: string) => {
    expect(rulesOf(code)).toEqual(['no-restricted-syntax'])
  })

  it.each([
    "import * as Vitest from 'vitest'; Vitest.vi.fn()",
    "import * as Vitest from 'vitest'; Vitest.vitest.spyOn(clock, 'now')",
    "import * as Vitest from 'vitest'; Vitest['vi']['mock']('./clock')",
  ])('refuses a mocking method reached through a namespace import: %s', (code: string) => {
    expect(rulesOf(code)).toEqual(['no-restricted-syntax'])
  })

  it.each([
    "import { vi } from 'vitest'; const double = vi",
    "import { vitest } from 'vitest'; const double = vitest",
    "import { vi } from 'vitest'; const { fn } = vi",
    "import * as Vitest from 'vitest'; const double = Vitest.vi",
    "import * as Vitest from 'vitest'; const double = Vitest['vitest']",
    "import * as Vitest from 'vitest'; const { vi: double } = Vitest",
    "const { vitest: double } = await import('vitest')",
    "import { vi } from 'vitest'; let double; double = vi",
    "import * as Vitest from 'vitest'; let double; double = Vitest.vi",
  ])('refuses the utility object bound to another name: %s', (code: string) => {
    expect(rulesOf(code)).toContain('no-restricted-syntax')
  })

  it.each([
    "import { vi } from 'vitest'; vi.useFakeTimers()",
    "import { vitest } from 'vitest'; vitest.waitFor(() => ready)",
    "import * as Vitest from 'vitest'; Vitest.vi.useFakeTimers()",
    "const { vi } = await import('vitest')",
    "const { vitest } = await import('vitest')",
    'const { vitest } = versions',
    "import vitest from '@vitest/eslint-plugin'; const plugin = vitest",
    'const version = versions.vitest',
    "import { vitest } from 'vitest'; const version = versions.vitest",
    "import { expect as assert } from 'vitest'",
  ])('leaves what is not a mock alone: %s', (code: string) => {
    expect(rulesOf(code)).toEqual([])
  })
})
