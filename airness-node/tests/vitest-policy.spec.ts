import { describe, expect, it } from 'vitest'
import { matchesGlob } from '@/file-roles.js'
import { type TestSuite, testSuites } from '@/vitest-policy.js'

const environments = (file: string, isBrowser: boolean): readonly string[] =>
  testSuites(isBrowser)
    .filter(
      (suite: TestSuite): boolean =>
        suite.include.some((pattern: string): boolean => matchesGlob(pattern, file)) &&
        !suite.exclude.some((pattern: string): boolean => matchesGlob(pattern, file)),
    )
    .map((suite: TestSuite): string => suite.environment)

describe('test runtime selection', () => {
  it('keeps integration tests in Node and component tests in Happy DOM without double collection', () => {
    expect(environments('tests/int/upload.int.spec.ts', true)).toEqual(['node'])
    expect(environments('tests/int/upload.int.spec.tsx', true)).toEqual(['node'])
    expect(environments('tests/component/form.component.spec.tsx', true)).toEqual(['happy-dom'])
    expect(environments('tests/component/dom.component.spec.ts', false)).toEqual(['happy-dom'])
    expect(environments('tests/e2e/site.e2e.spec.ts', true)).toEqual([])
  })

  it('preserves application unit tests using the DOM and ordinary library tests using Node', () => {
    expect(environments('tests/unit/links.unit.spec.ts', true)).toEqual(['jsdom'])
    expect(environments('tests/unit/math.unit.spec.ts', false)).toEqual(['node'])
    expect(environments('tests/policy.spec.ts', false)).toEqual(['node'])
    expect(environments('tests/support/example.ts', true)).toEqual([])
  })
})
