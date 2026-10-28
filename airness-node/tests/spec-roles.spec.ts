import { describe, expect, it } from 'vitest'
import { misplacedSpecs } from '@/spec-roles.js'

describe('misplacedSpecs', () => {
  it('accepts unit specs under tests and end-to-end specs of a browser application', () => {
    expect(
      misplacedSpecs(
        [
          'tests/total.spec.ts',
          'tests/deep/sum.spec.ts',
          'tests/component/button.component.spec.tsx',
          'tests/e2e/home.e2e.spec.ts',
          'src/a.ts',
        ],
        true,
      ),
    ).toEqual([])
  })

  it('reports a spec beside the sources, a test-named file and a non-e2e spec under tests/e2e', () => {
    expect(misplacedSpecs(['src/a.spec.ts', 'scripts/b.test.mjs', 'tests/e2e/c.spec.ts'], true)).toHaveLength(3)
  })

  it('reports an end-to-end spec of a member that is no browser application', () => {
    expect(misplacedSpecs(['tests/e2e/home.e2e.spec.ts'], false)).toEqual([
      'tests/e2e/home.e2e.spec.ts is an end-to-end spec in a member that is no browser application, so nothing runs it',
    ])
  })
})
