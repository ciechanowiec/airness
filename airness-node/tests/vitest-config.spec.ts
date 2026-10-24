import { describe, expect, it } from 'vitest'
import config, { COVERAGE_THRESHOLD } from '@/vitest.js'

describe('the shipped Vitest configuration', () => {
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
