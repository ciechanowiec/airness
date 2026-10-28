import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { asRecord, isArray } from '@/json-shapes.js'
import { testReporters } from '@/test-reporters.js'
import config, { COVERAGE_THRESHOLD, vitestConfig } from '@/vitest.js'

describe('the shipped Vitest configuration', () => {
  it.each([{ dependencies: { react: '19.3.1' } }, { airness: { testWrapper: ['node', 'wrapper.js'] } }])(
    'serializes application or wrapped suites and includes project setup after the guard',
    (manifest: object) => {
      const directory: string = mkdtempSync(path.join(tmpdir(), 'airness-vitest-config-'))
      try {
        writeFileSync(path.join(directory, 'package.json'), JSON.stringify(manifest))
        writeFileSync(path.join(directory, 'vitest.setup.ts'), 'export {}\n')
        writeFileSync(path.join(directory, 'vitest.global-setup.ts'), 'export const setup = (): void => {}\n')
        expect(asRecord(vitestConfig(directory)['test'])['globalSetup']).toEqual([
          expect.stringContaining('vitest-global-setup.js'),
          path.join(directory, 'vitest.global-setup.ts'),
        ])
        const projects: unknown = asRecord(vitestConfig(directory)['test'])['projects']
        expect(isArray(projects)).toBe(true)
        const declared: readonly unknown[] = isArray(projects) ? projects : []
        for (const entry of declared) {
          expect(asRecord(entry)['plugins']).toEqual(expect.any(Array))
          expect(asRecord(asRecord(entry)['test'])['fileParallelism']).toBe(false)
          expect(asRecord(asRecord(entry)['test'])['setupFiles']).toEqual([
            expect.stringContaining('vitest-setup.js'),
            './vitest.setup.ts',
          ])
        }
      } finally {
        rmSync(directory, { recursive: true, force: true })
      }
    },
  )
  it('keeps GitHub failure annotations without writing a separate library job summary', () => {
    expect(testReporters({})).toEqual(['default'])
    expect(testReporters({ GITHUB_ACTIONS: 'false' })).toEqual(['default'])
    expect(testReporters({ GITHUB_ACTIONS: 'true' })).toEqual([
      'default',
      ['github-actions', { jobSummary: { enabled: false } }],
    ])
  })
  it('holds every measured file to the floor, in a fixed shuffled order', () => {
    expect(asRecord(config['test'])['coverage']).toEqual(
      expect.objectContaining({
        provider: 'v8',
        thresholds: {
          perFile: true,
          lines: COVERAGE_THRESHOLD,
          branches: COVERAGE_THRESHOLD,
          functions: COVERAGE_THRESHOLD,
          statements: COVERAGE_THRESHOLD,
        },
      }),
    )
    expect(asRecord(config['test'])['sequence']).toEqual(
      expect.objectContaining({ shuffle: { files: true, tests: true } }),
    )
    const projects: unknown = asRecord(config['test'])['projects']
    expect(isArray(projects)).toBe(true)
    const declared: readonly unknown[] = isArray(projects) ? projects : []
    for (const entry of declared) {
      const suite: Record<string, unknown> = asRecord(asRecord(entry)['test'])
      expect(suite['sequence']).toEqual(expect.objectContaining({ setupFiles: 'list' }))
      expect(suite['setupFiles']).toEqual(expect.arrayContaining([expect.stringContaining('vitest-setup.js')]))
    }
  })
})
