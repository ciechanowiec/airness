// Every collected test has its runtime and setup fixed by the harness. Database-backed application
// suites are serial, while plain library suites retain the ordinary Node worker pool. Every test reads the
// managed Git configuration, as the tests gate also sets it for the whole run, so a run outside the gate does too.
import { existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'
import { coverageExclusions, coverageIncludes } from './coverage-policy.js'
import { isBrowserAppAt } from './facets.js'
import { TYPESCRIPT_SOURCE_EXTENSIONS } from './file-roles.js'
import { isolatedGitEnvironment } from './git-isolation.js'
import { declaredDependencies } from './json-shapes.js'
import { LAYER_NAMES, layerDefaultsFor, layerTestDependenciesFor } from './layer.js'
import { readJson } from './project.js'
import { PROJECT_SETUP_FILE, readSettings, type Settings } from './settings.js'
import { testReporters } from './test-reporters.js'
import { escapeForRegex } from './text-escapes.js'
import { TEST_SEQUENCE, type TestSuite, testSuites } from './vitest-policy.js'
import { hasOwnRuntime } from './workspace.js'

/** The per-file floor for lines, branches, functions and statements. */
export const COVERAGE_THRESHOLD: number = 80

// Every measure of every measured file is held to the floor on its own.
const PER_FILE_THRESHOLDS: Readonly<Record<string, number | boolean>> = {
  perFile: true,
  lines: COVERAGE_THRESHOLD,
  branches: COVERAGE_THRESHOLD,
  functions: COVERAGE_THRESHOLD,
  statements: COVERAGE_THRESHOLD,
}

const GLOBAL_SETUP_FILE: string = 'vitest.global-setup.ts'

const TRANSFORM_INCLUDE: RegExp = new RegExp(
  `(?:${[...TYPESCRIPT_SOURCE_EXTENSIONS, '.jsx'].map((extension: string): string => escapeForRegex(extension)).join('|')})$`,
)

const workerSetup = (directory: string): readonly string[] => [
  fileURLToPath(import.meta.resolve('#harness/vitest-setup')),
  ...(existsSync(path.join(directory, PROJECT_SETUP_FILE)) ? [`./${PROJECT_SETUP_FILE}`] : []),
]

const globalSetup = (directory: string): readonly string[] =>
  existsSync(path.join(directory, GLOBAL_SETUP_FILE))
    ? [fileURLToPath(import.meta.resolve('#harness/vitest-global-setup')), path.join(directory, GLOBAL_SETUP_FILE)]
    : []

/** The configuration Vitest reads. */
type VitestConfig = Readonly<Record<string, unknown>>

/**
 * Construct the shipped configuration from a real project directory, without changing process state.
 * @param directory the project directory.
 * @param layers the packages read as layers, the registered ones unless a caller names others.
 * @returns the configuration.
 */
export const vitestConfig = (directory: string, layers: readonly string[] = LAYER_NAMES): VitestConfig => {
  const manifest: unknown = readJson(path.join(directory, 'package.json'))
  const settings: Settings = readSettings(manifest, layerDefaultsFor(directory, manifest, readJson, layers))
  const hasReact: boolean = Object.hasOwn(declaredDependencies(manifest), 'react')
  const isBrowser: boolean = hasReact || isBrowserAppAt(manifest, settings)
  const setup: readonly string[] = workerSetup(directory)
  const inline: readonly string[] = layerTestDependenciesFor(directory, manifest, readJson, layers)

  const project = (suite: TestSuite): Record<string, unknown> => ({
    extends: false,
    plugins: hasReact ? [react()] : [],
    oxc: { include: TRANSFORM_INCLUDE },
    resolve: { tsconfigPaths: true },
    test: {
      name: suite.name,
      environment: suite.environment,
      include: [...suite.include],
      exclude: [...suite.exclude],
      setupFiles: [...setup],
      env: { ...isolatedGitEnvironment(directory) },
      server: { deps: { inline: [...inline] } },
      sequence: TEST_SEQUENCE,
      fileParallelism: !(isBrowser || settings.testWrapper.length > 0),
    },
  })

  return {
    ...defineConfig({
      resolve: { tsconfigPaths: true },
      oxc: { include: TRANSFORM_INCLUDE },
      test: {
        reporters: [...testReporters(process.env)],
        globalSetup: [...globalSetup(directory)],
        sequence: TEST_SEQUENCE,
        projects: testSuites(isBrowser).map((suite: TestSuite): Record<string, unknown> => project(suite)),
        coverage: {
          provider: 'v8',
          reporter: ['text'],
          reportsDirectory: 'node_modules/.airness/coverage',
          include: [...coverageIncludes(settings.sourceRoots)],
          exclude: [...coverageExclusions(settings, hasOwnRuntime(manifest))],
          thresholds: { ...PER_FILE_THRESHOLDS },
        },
      },
    }),
  }
}

// Do not make a consuming config resolve Vite's transitive types merely to re-export this value.
const config: VitestConfig = vitestConfig(process.cwd())
export default config
