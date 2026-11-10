import path from 'node:path'
import { ESLint } from 'eslint'
import { defineConfig } from 'eslint/config'
import { describe, expect, it } from 'vitest'
import nodeConfig from '@/eslint.js'
import {
  NO_FAST_CHECK_SEED,
  NO_INHERITANCE,
  NO_LITERAL_ASSERTIONS,
  NO_MOCK_SYNTAX,
  NO_NETWORK_GUARD_ESCAPE,
  NO_TEST_ORDER_ESCAPE,
  type RestrictedSyntax,
} from '@/eslint-core.js'
import { componentTestBlocks, endToEndBlocks, nextBlocks } from '@/eslint-next.js'
import { SECURITY_RESTRICTIONS } from '@/eslint-security.js'
import { TIME_ZONE_RESTRICTIONS } from '@/eslint-time-zone.js'
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
  it('ignores a copy in Maven build output without excluding the corresponding authored source', async () => {
    expect(await eslint.isPathIgnored(path.resolve('target/generated-sources/copy/src/page.ts'))).toBe(true)
    expect(await eslint.isPathIgnored(path.resolve('src/page.ts'))).toBe(false)
    const production: Record<string, unknown> = await rulesAt('src/page.ts')
    expect(production['@typescript-eslint/no-explicit-any']).toEqual([2])
  })

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

  it.each(['src/page.ts', 'src/component.tsx', 'tests/logic.spec.ts', 'tests/e2e/example.e2e.spec.ts'])(
    'holds date formatting in %s to an explicit time zone',
    async (file: string) => {
      const zoneMessage: unknown = expect.stringContaining('[implicit-time-zone]')
      const rules: Record<string, unknown> = await rulesAt(file)
      expect(rules['no-restricted-syntax']).toEqual(
        expect.arrayContaining([expect.objectContaining({ message: zoneMessage })]),
      )
    },
  )

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

// A block that sets `no-restricted-syntax` replaces the setting rather than adding to it, so a scope whose block
// restates the tables by hand keeps only the ones it remembered. Every shared table is asked for by name in a
// file of every scope, so a table a scope drops fails here under its own name.
const BUILD_WIDE_TABLES: Readonly<Record<string, readonly RestrictedSyntax[]>> = {
  NO_INHERITANCE: NO_INHERITANCE.filter((entry: unknown): entry is RestrictedSyntax => typeof entry !== 'string'),
  SECURITY_RESTRICTIONS,
  TIME_ZONE_RESTRICTIONS,
  NO_MOCK_SYNTAX,
}

const SPEC_TABLES: Readonly<Record<string, readonly RestrictedSyntax[]>> = {
  ...BUILD_WIDE_TABLES,
  NO_LITERAL_ASSERTIONS,
  NO_FAST_CHECK_SEED,
  NO_TEST_ORDER_ESCAPE,
  NO_NETWORK_GUARD_ESCAPE,
}

const missingTables = async (
  file: string,
  tables: Readonly<Record<string, readonly RestrictedSyntax[]>>,
): Promise<readonly string[]> => {
  const rules: Record<string, unknown> = await rulesAt(file)
  const setting: unknown = rules['no-restricted-syntax']
  const entries: readonly unknown[] = Array.isArray(setting) ? setting : []
  return Object.entries(tables)
    .filter(([, table]: readonly [string, readonly RestrictedSyntax[]]): boolean =>
      table.some(
        (entry: RestrictedSyntax): boolean =>
          !entries.some((resolved: unknown): boolean => asRecord(resolved)['selector'] === entry.selector),
      ),
    )
    .map(([name]: readonly [string, readonly RestrictedSyntax[]]): string => name)
}

describe('the shared restricted-syntax tables', () => {
  it.each([
    'tests/logic.spec.ts',
    'tests/helpers/seed.ts',
    'tests/unit/price.spec.ts',
    'tests/int/orders.int.spec.ts',
    'tests/component/form.component.spec.tsx',
    'tests/e2e/example.e2e.spec.ts',
    'tests/e2e/helpers/sign-in.ts',
  ])('hold every spec table in %s', async (file: string) => {
    expect(await missingTables(file, SPEC_TABLES)).toEqual([])
  })

  it.each(['src/page.ts', 'src/component.tsx', 'scripts/release.ts', 'next.config.ts'])(
    'hold every build-wide table in %s',
    async (file: string) => {
      expect(await missingTables(file, BUILD_WIDE_TABLES)).toEqual([])
    },
  )
})
