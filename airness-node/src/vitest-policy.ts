import { END_TO_END_SPEC_GLOB, UNIT_SPEC_GLOB } from './spec-roles.js'

/** A disjoint group of specifications and the JavaScript realm it needs. */
export interface TestSuite {
  readonly name: string
  readonly environment: 'node' | 'jsdom' | 'happy-dom'
  readonly include: readonly string[]
  readonly exclude: readonly string[]
}

const COMPONENTS: string = 'tests/component/**/*.component.spec.{ts,tsx}'
const APPLICATION_UNITS: string = 'tests/unit/**/*.unit.spec.{ts,tsx}'
const COMMON_EXCLUDES: readonly string[] = [END_TO_END_SPEC_GLOB, '**/node_modules/**']

/** Keep server integration tests in Node, collect components in a DOM with native dialog and fetch support, and leave no duplicate suite. */
export const testSuites = (isBrowser: boolean): readonly TestSuite[] => {
  const units: readonly string[] = isBrowser ? [APPLICATION_UNITS] : []
  return [
    {
      name: 'node',
      environment: 'node',
      include: [UNIT_SPEC_GLOB],
      exclude: [...COMMON_EXCLUDES, COMPONENTS, ...units],
    },
    { name: 'jsdom', environment: 'jsdom', include: units, exclude: COMMON_EXCLUDES },
    { name: 'components', environment: 'happy-dom', include: [COMPONENTS], exclude: COMMON_EXCLUDES },
  ]
}

/** The fixed ordering shared by the root run and every environment-specific suite. */
export interface TestSequence {
  readonly shuffle: { readonly files: boolean; readonly tests: boolean }
  readonly seed: number
  readonly concurrent: boolean
  readonly hooks: 'stack'
  readonly setupFiles: 'list'
}

/** A fixed shuffle exposes order dependence without making the result depend on the wall clock. */
export const TEST_SEQUENCE: TestSequence = {
  shuffle: { files: true, tests: true },
  seed: 1_734_000_002,
  concurrent: false,
  hooks: 'stack',
  setupFiles: 'list',
}
