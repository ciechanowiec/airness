import { describe, expect, it } from 'vitest'
import { testReporters } from '@/test-reporters.js'
import config, { COVERAGE_THRESHOLD } from '@/vitest.js'

describe('the shipped Vitest configuration', () => {
  it('keeps GitHub failure annotations without writing a separate library job summary', () => {
    expect(testReporters({})).toEqual(['default'])
    expect(testReporters({ GITHUB_ACTIONS: 'false' })).toEqual(['default'])
    expect(testReporters({ GITHUB_ACTIONS: 'true' })).toEqual([
      'default',
      ['github-actions', { jobSummary: { enabled: false } }],
    ])
  })
  it('holds every measured file to the floor, in a fixed shuffled order', () => {
    expect(config.test?.coverage).toEqual(
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
    expect(config.test?.sequence).toEqual(expect.objectContaining({ shuffle: { files: true, tests: true } }))
    expect(config.test?.include).toEqual(['tests/**/*.spec.ts'])
  })
})
