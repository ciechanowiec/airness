import path from 'node:path'
import { ESLint, Linter } from 'eslint'
import { defineConfig } from 'eslint/config'
import tseslint from 'typescript-eslint'
import { describe, expect, it } from 'vitest'
import nodeConfig from '@/eslint.js'
import { nextBlocks } from '@/eslint-next.js'
import { asRecord, isArray } from '@/json-shapes.js'

// A picture that bypasses the Next.js image optimizer, linted on its own: every spelling that switches the
// optimizer off is reported once, and every spelling that leaves it on is passed over.

const linter: Linter = new Linter({ configType: 'flat' })

// The facet's own entries for a picture, read out of the block that carries them, so the test judges what a
// project is linted with.
const pictureEntries: readonly unknown[] = nextBlocks
  .map((block: unknown): unknown => asRecord(asRecord(block)['rules'])['no-restricted-syntax'])
  .filter((setting: unknown): boolean => isArray(setting))
  .flatMap((setting: unknown): readonly unknown[] => (isArray(setting) ? setting.slice(1) : []))
  .filter((entry: unknown): boolean => JSON.stringify(entry).includes('unoptimized'))

const reportsOf = (code: string): number =>
  linter.verify(
    code,
    [
      {
        files: ['**/*.tsx'],
        languageOptions: { parser: tseslint.parser, parserOptions: { ecmaFeatures: { jsx: true } } },
        rules: { 'no-restricted-syntax': ['error', ...pictureEntries] },
      },
    ],
    'picture.tsx',
  ).length

describe('a picture that bypasses the image optimizer', () => {
  it.each([
    '<Image src={cover} alt="" width={1} height={1} unoptimized />',
    '<Image src={cover} alt="" width={1} height={1} unoptimized={true} />',
    '<Image src={cover} alt="" width={1} height={1} unoptimized={isPreview} />',
    'export default { images: { unoptimized: true } }',
    "export default { images: { 'unoptimized': true } }",
    'getImageProps({ src: cover, alt: "", width: 1, height: 1, unoptimized: true })',
  ])('is reported in %s', (code: string) => {
    expect(reportsOf(code)).toBe(1)
  })

  it.each([
    '<Image src={cover} alt="" width={1} height={1} />',
    '<Image src={cover} alt="" width={1} height={1} unoptimized={false} />',
    'export default { images: { unoptimized: false, formats: ["image/avif"] } }',
    'export default { images: { minimumCacheTTL: 86400 } }',
  ])('is not reported in %s', (code: string) => {
    expect(reportsOf(code)).toBe(0)
  })
})

describe('the Next.js facet', () => {
  const eslint: ESLint = new ESLint({
    overrideConfigFile: true,
    overrideConfig: defineConfig([...nodeConfig, ...nextBlocks]),
  })

  it.each(['src/app/page.tsx', 'src/lib/cover.ts', 'next.config.ts', 'next.config.mjs'])(
    'refuses a picture past the optimizer in %s',
    async (file: string) => {
      const config: unknown = await eslint.calculateConfigForFile(path.resolve(file))
      const setting: unknown = asRecord(asRecord(config)['rules'])['no-restricted-syntax']
      expect(JSON.stringify(setting)).toContain("JSXAttribute[name.name='unoptimized']")
    },
  )
})
