import { describe, expect, it } from 'vitest'
import { composeLayered, type LayerRelaxation } from '@/layer-lint-policy.js'

// A node configuration in the shapes the real one has: a file-less block, one scoped to some files, and a
// list rule a layer may extend.
const SELECTOR: Readonly<Record<string, string>> = { selector: 'WithStatement', message: 'no with' }
const NODE: readonly unknown[] = [
  {
    rules: {
      'no-magic-numbers': ['error', { ignore: [0, 1] }],
      'no-restricted-syntax': ['error', SELECTOR],
      'unicorn/filename-case': ['error', { cases: { kebabCase: true } }],
      'unicorn/no-top-level-side-effects': 'error',
    },
  },
  { files: ['**/*.ts'], rules: { 'functional/immutable-data': ['error', { ignoreIdentifierPattern: [] }] } },
]

// The relaxations a fixture layer is granted, so the policy is measured whatever the harness registers.
const RELAXATIONS: readonly LayerRelaxation[] = [
  {
    layer: 'layer-a',
    rule: 'unicorn/no-top-level-side-effects',
    files: ['src/framework.config.ts'],
    reason: 'the framework consumes an eagerly built configuration',
  },
  {
    layer: 'layer-a',
    rule: 'unicorn/filename-case',
    files: ['src/migrations/**/*.ts'],
    reason: 'the framework names its migrations in snake case',
  },
]

const layered = (...blocks: readonly unknown[]): readonly unknown[] =>
  composeLayered(NODE, [{ name: 'layer-a', config: [...NODE, ...blocks] }], RELAXATIONS)

const refusal = (...blocks: readonly unknown[]): string => {
  try {
    layered(...blocks)
    return ''
  } catch (error: unknown) {
    return error instanceof Error ? error.message : String(error)
  }
}

describe('a stack layer joining the node ESLint configuration', () => {
  it('adds the blocks that only add to the verdict after the node blocks', () => {
    const extended: unknown = {
      files: ['src/collections/**/*.ts'],
      ignores: ['src/collections/generated.ts'],
      rules: {
        'no-restricted-syntax': ['error', SELECTOR, { selector: 'ArrowFunctionExpression', message: 'by reference' }],
        'id-denylist': ['warn', 'left'],
      },
    }
    expect(layered(extended)).toEqual([...NODE, extended])
    expect(composeLayered(NODE, [])).toEqual(NODE)
  })

  it('refuses a node rule turned off, lowered or given options that do not extend it', () => {
    expect(refusal({ files: ['src/**'], rules: { 'no-magic-numbers': 'off' } })).toContain(
      'block 0 of the stack layer layer-a weakens the node rule no-magic-numbers',
    )
    expect(refusal({ files: ['src/**'], rules: { 'no-magic-numbers': ['warn', { ignore: [0, 1] }] } })).toContain(
      'no-magic-numbers',
    )
    expect(refusal({ rules: { 'no-magic-numbers': ['error', { ignore: [0, 1, 2] }] } })).toContain('no-magic-numbers')
    expect(refusal({ rules: { 'no-restricted-syntax': ['error', { selector: 'X', message: 'x' }] } })).toContain(
      'no-restricted-syntax',
    )
    expect(refusal({ rules: { 'functional/immutable-data': 0 } })).toContain('functional/immutable-data')
  })

  it('refuses a global ignores block, a parser change, a block that is no block, and a layer that drops node blocks', () => {
    expect(refusal({ ignores: ['src/**'] })).toContain('ignores files for every block')
    expect(refusal({ files: ['src/**'], languageOptions: {} })).toContain('sets languageOptions')
    expect(refusal('blocks')).toContain('is no configuration block')
    expect(() => composeLayered(NODE, [{ name: 'layer-a', config: [NODE[1], NODE[0]] }], RELAXATIONS)).toThrow(
      "does not start with the node layer's blocks",
    )
    expect(() => composeLayered(NODE, [{ name: 'layer-a', config: 'none' }], RELAXATIONS)).toThrow('does not start')
  })

  it('admits the relaxations the harness lists for the layer, for exactly the files it lists', () => {
    const frameworkConfig: unknown = {
      files: ['src/framework.config.ts'],
      rules: { 'unicorn/no-top-level-side-effects': 'off' },
    }
    const migrations: unknown = {
      files: ['src/migrations/**/*.ts'],
      rules: { 'unicorn/filename-case': ['error', { cases: { kebabCase: true, snakeCase: true } }] },
    }
    expect(layered(frameworkConfig, migrations)).toHaveLength(NODE.length + 2)
    expect(refusal({ files: ['src/**'], rules: { 'unicorn/no-top-level-side-effects': 'off' } })).toContain(
      'unicorn/no-top-level-side-effects',
    )
    expect(() => composeLayered(NODE, [{ name: 'other', config: [...NODE, frameworkConfig] }], RELAXATIONS)).toThrow(
      'stack layer other',
    )
  })
})
