import path from 'node:path'
import { ESLint } from 'eslint'
import { defineConfig } from 'eslint/config'
import { describe, expect, it } from 'vitest'
import nodeConfig from '@/eslint.js'
import { componentTestBlocks, endToEndBlocks, nextBlocks } from '@/eslint-next.js'
import { asRecord } from '@/json-shapes.js'

const eslint: ESLint = new ESLint({
  overrideConfigFile: true,
  overrideConfig: defineConfig([...nodeConfig, ...nextBlocks, ...endToEndBlocks, ...componentTestBlocks]),
})

const rulesAt = async (file: string): Promise<Record<string, unknown>> => {
  const config: unknown = await eslint.calculateConfigForFile(path.resolve(file))
  return asRecord(asRecord(config)['rules'])
}

describe('the application lint scopes', () => {
  it('allows test literals and setup in Playwright while retaining production checks', async () => {
    const browser: Record<string, unknown> = await rulesAt('tests/e2e/example.e2e.spec.ts')
    expect(browser['@typescript-eslint/no-magic-numbers']).toEqual(expect.arrayContaining([0]))
    expect(browser['unicorn/no-top-level-assignment-in-function']).toEqual([0])
    expect(browser['playwright/no-focused-test']).toEqual([2])
    const networkMessage: unknown = expect.stringContaining('network guard')
    expect(browser['no-restricted-syntax']).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          message: networkMessage,
        }),
      ]),
    )
    const production: Record<string, unknown> = await rulesAt('src/component.tsx')
    expect(production['@typescript-eslint/no-magic-numbers']).toEqual(expect.arrayContaining([2]))
    expect(production['unicorn/no-top-level-assignment-in-function']).toEqual([2])
  })

  it('keeps user-facing component assertions and configuration composition in their proper scopes', async () => {
    const component: Record<string, unknown> = await rulesAt('tests/component/form.component.spec.tsx')
    expect(component['testing-library/no-container']).toEqual([2])
    expect(component['testing-library/prefer-user-event']).toEqual([2])
    const config: Record<string, unknown> = await rulesAt('next.config.ts')
    expect(config['unicorn/no-top-level-side-effects']).toEqual(expect.arrayContaining([0]))
    const logic: Record<string, unknown> = await rulesAt('src/logic.ts')
    expect(logic['unicorn/no-top-level-side-effects']).toEqual(expect.arrayContaining([2]))
  })
})
