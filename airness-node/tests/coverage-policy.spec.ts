import { describe, expect, it } from 'vitest'
import { coverageCandidates, coverageExclusions, coverageIncludes, measurableFiles } from '@/coverage-policy.js'
import { readSettings, type Settings } from '@/settings.js'

const SETTINGS: Settings = readSettings({
  airness: {
    generatedArtefacts: [{ pattern: 'src/generated/**', reason: 'the client generator writes it' }],
    coverageExclude: [{ pattern: 'src/entry.ts', reason: 'the entry point runs as a process of its own' }],
  },
})

const FILES: readonly string[] = [
  'src/total.ts',
  'src/entry.ts',
  'src/generated/client.ts',
  'src/view.tsx',
  'src/types.d.ts',
  'scripts/release.mts',
  'tests/total.spec.ts',
  'gate/src/total.ts',
  'README.md',
]

describe('the coverage floor', () => {
  it('measures every TypeScript format, components included, under every source root but the test tree', () => {
    expect(coverageIncludes(['src', 'tests', 'scripts/'])).toEqual([
      'src/**/*.ts',
      'src/**/*.tsx',
      'src/**/*.mts',
      'src/**/*.cts',
      'scripts/**/*.ts',
      'scripts/**/*.tsx',
      'scripts/**/*.mts',
      'scripts/**/*.cts',
    ])
    expect(coverageIncludes(['.'])).toEqual(['**/*.ts', '**/*.tsx', '**/*.mts', '**/*.cts'])
  })

  it('excludes the invariant roles, the generated files and the declared exclusions', () => {
    expect(coverageExclusions(SETTINGS)).toEqual(
      expect.arrayContaining(['tests/**', 'src/generated/**', 'src/entry.ts']),
    )
  })

  it('selects the files measured, inside the member, before and after the declared exclusions', () => {
    expect(measurableFiles(FILES, SETTINGS, ['gate'])).toEqual([
      'src/total.ts',
      'src/entry.ts',
      'src/view.tsx',
      'scripts/release.mts',
    ])
    expect(coverageCandidates(FILES, SETTINGS, ['gate'])).toEqual([
      'src/total.ts',
      'src/view.tsx',
      'scripts/release.mts',
    ])
  })
})

describe('the coverage floor of a Next.js application', () => {
  const files: readonly string[] = [
    'src/app/page.tsx',
    'src/app/blog/[slug]/layout.tsx',
    'src/app/api/route.ts',
    'src/app/blog/card.tsx',
    'src/proxy.ts',
    'src/price.ts',
  ]

  it('leaves the pages the crawl renders and the proxy to the e2e gate, and measures route handlers', () => {
    const measured: readonly string[] = ['src/app/api/route.ts', 'src/app/blog/card.tsx', 'src/price.ts']
    expect(coverageCandidates(files, readSettings({}), [], true)).toEqual(measured)
    expect(measurableFiles(files, readSettings({}), [], true)).toEqual(measured)
    expect(coverageExclusions(readSettings({}), true)).toContain('src/proxy.ts')
  })

  it('measures the same files in a member that is no Next.js application', () => {
    expect(coverageCandidates(files, readSettings({}), [])).toEqual(files)
  })
})
