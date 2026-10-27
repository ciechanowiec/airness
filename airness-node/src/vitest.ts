// The Vitest configuration the `tests` gate runs every member's suite under, and the one a project's
// managed `vitest.config.mts` re-exports for an editor. The gate passes this file to Vitest itself, so a
// project file that drops the coverage floor or the fixed order changes nothing the verdict reads.
import path from 'node:path'
import { defineConfig, type ViteUserConfig } from 'vitest/config'
import { coverageExclusions, coverageIncludes } from './coverage-policy.js'
import { layerDefaultsFor } from './layer.js'
import { readJson } from './project.js'
import { readSettings, type Settings } from './settings.js'
import { END_TO_END_SPEC_GLOB, UNIT_SPEC_GLOB } from './spec-roles.js'
import { testReporters } from './test-reporters.js'
import { hasOwnRuntime } from './workspace.js'

/** The per-file floor every measured file is held to, for lines, branches, functions and statements. */
export const COVERAGE_THRESHOLD: number = 80

// The order a shuffled suite runs in, fixed so the suite reaches one verdict on an unchanged tree. One
// seed samples one order rather than proving every order, but it is not the order the files are written
// in, which is the coupling that accumulates unseen.
const TEST_ORDER_SEED: number = 1_734_000_002

// The settings of the member the run starts in, with the defaults of its layer packages, read the way every
// gate reads them.
const manifest: unknown = readJson(path.join(process.cwd(), 'package.json'))
const settings: Settings = readSettings(manifest, layerDefaultsFor(process.cwd(), manifest, readJson))

const config: ViteUserConfig = defineConfig({
  resolve: { tsconfigPaths: true },
  test: {
    environment: 'node',
    reporters: [...testReporters(process.env)],
    include: [UNIT_SPEC_GLOB],
    // The end-to-end specs are Playwright's, which the e2e gate runs against a served application.
    exclude: [END_TO_END_SPEC_GLOB, '**/node_modules/**'],
    sequence: {
      shuffle: { files: true, tests: true },
      seed: TEST_ORDER_SEED,
      concurrent: false,
      hooks: 'stack',
      setupFiles: 'list',
    },
    coverage: {
      provider: 'v8',
      // The verdict is the floor; the report goes to the build log, and the scratch below node_modules, so
      // a run writes nothing into the tree.
      reporter: ['text'],
      reportsDirectory: 'node_modules/.airness/coverage',
      include: [...coverageIncludes(settings.sourceRoots)],
      exclude: [...coverageExclusions(settings, hasOwnRuntime(manifest))],
      thresholds: {
        perFile: true,
        lines: COVERAGE_THRESHOLD,
        branches: COVERAGE_THRESHOLD,
        functions: COVERAGE_THRESHOLD,
        statements: COVERAGE_THRESHOLD,
      },
    },
  },
})

export default config
