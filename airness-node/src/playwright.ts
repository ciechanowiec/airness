// The Playwright configuration the e2e gate passes to Playwright with `--config`. It lives in the harness's
// own dist rather than in the project, so every path below is absolute: the harness's shipped specs run from
// beside this file, each declared stack layer's from the directory its manifest names, and the member's own
// specs and Playwright's output are found from the directory the run starts in, which the gate sets to the
// member.
//
// Everything here is policy rather than preference. `forbidOnly` is the end-to-end analogue of the
// focused-test ban the lint gates enforce for Vitest: a left-behind `test.only` would otherwise turn a run
// into one green test. Project and layer specifications run on one worker; the automatic sweep shards
// independent pages across the capacity the browser launcher measured. The project-specific inputs are the origin and,
// for an application that is not a Next.js one, the script that serves it, both declared under the
// `airness` key of package.json. Neither reaches a rule: they say where the application answers and how it
// starts, and every ban and shipped spec is the same whatever they say.
import { existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig, devices } from '@playwright/test'
import { DEVICE_SCALE_FACTOR } from './browser-rules.js'
import {
  BROWSER_ENDPOINT_ENVIRONMENT_VARIABLE,
  BROWSER_TUNNEL_ENVIRONMENT_VARIABLE,
  CLOCK_ENVIRONMENT_VARIABLE,
} from './browser-run.js'
import {
  browserServerEndpoint,
  canReuseBrowserServer,
  VERIFICATION_ENVIRONMENT_VARIABLE,
} from './browser-server-policy.js'
import { coversEveryEngine } from './change-scope-policy.js'
import { clockedEnvironment } from './clock-environment.js'
import { portOf, runEnvironmentFiles } from './environment-files.js'
import { type LayerProject, layerProjectsFor, SHIPPED_SPECS } from './layer.js'
import { loadProductionEnvironment } from './production-environment.js'
import { shellArgument } from './production-output.js'
import { readJson } from './project.js'
import { projectDirectory, projectSettings } from './project-settings.js'
import { SWEEP_WORKERS } from './sweep-workers.js'
import { hasOwnRuntime } from './workspace.js'

const here: string = path.dirname(fileURLToPath(import.meta.url))

/** The member's own end-to-end specifications, by the harness's convention. */
const PROJECT_SPECS: string = path.join(projectDirectory, 'tests', 'e2e')

// Below node_modules, so a run leaves the working tree exactly as it found it.
const OUTPUT_DIRECTORY: string = path.join(projectDirectory, 'node_modules', '.airness', 'e2e', 'test-results')

const manifest: unknown = readJson(path.join(projectDirectory, 'package.json'))
const isNext: boolean = hasOwnRuntime(manifest)
const isVerification: boolean = process.env[VERIFICATION_ENVIRONMENT_VARIABLE] !== undefined

// Read before anything else, because a spec module is what needs it. Playwright evaluates this config in
// the runner and again in every worker, which makes it the one place a value reaches both the pass that
// collects the specs and the processes that run them, and collection is already too late for a helper that
// reads the environment at import time. A `globalSetup` hook would not serve, because it runs in a process of
// its own and its environment never reaches a worker.
const loadEnvironment = (): void => {
  if (isVerification && isNext) {
    loadProductionEnvironment(projectDirectory)
    return
  }
  if (isVerification) {
    return
  }
  const present: readonly string[] = runEnvironmentFiles((relative: string): boolean =>
    existsSync(path.join(projectDirectory, relative)),
  )
  for (const file of present) {
    process.loadEnvFile(path.join(projectDirectory, file))
  }
}

// eslint-disable-next-line unicorn/no-top-level-side-effects -- the environment must be loaded while this configuration is evaluated, in the runner and in every worker, before any spec module is imported
loadEnvironment()

if (process.env[CLOCK_ENVIRONMENT_VARIABLE] !== undefined) {
  await import('./clock-preload.js')
}
for (const signal of ['SIGTERM', 'SIGHUP'] as const) {
  process.once(signal, (): void => {
    process.kill(process.pid, 'SIGINT')
  })
}
const isReuseExistingServer: boolean = canReuseBrowserServer(process.env)
// Resolved rather than joined, so the launcher is a module this configuration names and not a bare path.
const productionLauncher: string = fileURLToPath(import.meta.resolve('./production-server.js'))

// A framework that compiles a route on its first request can exceed Playwright's 30s default on a cold
// runner, so the budget gives compilation headroom instead of leaning on a retry to mask it.
const TEST_TIMEOUT_MS: number = 90_000
const SERVER_TIMEOUT_MS: number = 180_000

// Spread rather than assigned, so an origin naming no port leaves the framework's default in place rather
// than setting PORT to the string "undefined".
const withPort = (
  base: Readonly<Record<string, string>>,
  port: string | undefined,
): Readonly<Record<string, string>> => (port === undefined ? base : { ...base, PORT: port })

// The command that serves the application under test. A Next.js application in a verification run starts
// its production build, which the build gate made before this one runs; outside verification it starts the
// development server. Any other browser application starts through the script it declared.
const serveCommand = (): string | undefined => {
  if (isNext) {
    return isVerification ? `${shellArgument(process.execPath)} ${shellArgument(productionLauncher)}` : 'next dev'
  }
  return projectSettings.serve === undefined ? undefined : `pnpm run ${shellArgument(projectSettings.serve)}`
}

const command: string | undefined = serveCommand()

const serverEnvironment: Readonly<Record<string, string>> = withPort(
  {
    NEXT_TELEMETRY_DISABLED: '1',
    ...clockedEnvironment({ settings: projectSettings }, '--no-deprecation'),
    ...(isVerification && {
      NODE_ENV: 'production',
      HOSTNAME: browserServerEndpoint(projectSettings.serverUrl).host,
    }),
  },
  portOf(projectSettings.serverUrl),
)

const endpoint: string | undefined = process.env[BROWSER_ENDPOINT_ENVIRONMENT_VARIABLE]
const remote: Readonly<Record<string, unknown>> =
  endpoint === undefined
    ? {}
    : {
        connectOptions: {
          wsEndpoint: endpoint,
          ...(process.env[BROWSER_TUNNEL_ENVIRONMENT_VARIABLE] !== undefined && { exposeNetwork: '<loopback>' }),
        },
      }
const browser: Readonly<Record<string, unknown>> = {
  ...devices['Desktop Chrome'],
  deviceScaleFactor: DEVICE_SCALE_FACTOR,
  ...(endpoint === undefined && { channel: 'chromium' }),
}
const SWEEP_SPEC: RegExp = /a11y\.e2e\.spec\.js$/u
const SWEEP_DISCOVERY: string = 'sweep-discovery'
const SWEEP_SUMMARY: string = 'sweep-summary'
const sweepProjects: NonNullable<ReturnType<typeof defineConfig>['projects']> = [
  {
    name: SWEEP_DISCOVERY,
    testDir: here,
    testMatch: SWEEP_SPEC,
    grep: /@airness-discovery/u,
    teardown: SWEEP_SUMMARY,
    use: browser,
  },
  {
    name: 'sweep-chromium',
    testDir: here,
    testMatch: SWEEP_SPEC,
    grep: /@airness-chromium/u,
    dependencies: [SWEEP_DISCOVERY],
    fullyParallel: true,
    use: browser,
  },
  ...(coversEveryEngine(process.env)
    ? [
        {
          name: 'sweep-firefox',
          testDir: here,
          testMatch: SWEEP_SPEC,
          grep: /@airness-engine/u,
          dependencies: [SWEEP_DISCOVERY],
          fullyParallel: true,
          use: {
            ...devices['Desktop Firefox'],
            deviceScaleFactor: DEVICE_SCALE_FACTOR,
          },
        },
        {
          name: 'sweep-webkit',
          testDir: here,
          testMatch: SWEEP_SPEC,
          grep: /@airness-engine/u,
          dependencies: [SWEEP_DISCOVERY],
          fullyParallel: true,
          use: {
            ...devices['Desktop Safari'],
            deviceScaleFactor: DEVICE_SCALE_FACTOR,
          },
        },
      ]
    : []),
  {
    name: SWEEP_SUMMARY,
    testDir: here,
    testMatch: SWEEP_SPEC,
    grep: /@airness-summary/u,
    use: browser,
  },
]

// The specifications each declared stack layer ships, as harness-owned as the node layer's own: read from the
// installed layer package, never from a file of the project. A layer that names a directory holding none fails
// the run while this configuration loads, rather than letting Playwright pass a project with no tests.
const layerProjects: readonly LayerProject[] = layerProjectsFor(projectDirectory, manifest, readJson, browser)

// Structural rather than Playwright's own `PlaywrightTestConfig`: the harness owns the config and the run
// resolves the runner, so an emitted declaration should not make the runner's types a condition of reading
// this entry point.
const declared: ReturnType<typeof defineConfig> = defineConfig({
  timeout: TEST_TIMEOUT_MS,
  forbidOnly: true,
  retries: 0,
  // A test that passes only on a retry is flaky, and Playwright exits zero for it unless told otherwise. The
  // configuration never retries a test, on CI or locally: a first failure remains the verdict.
  failOnFlakyTests: true,
  workers: SWEEP_WORKERS,
  // `list` alone: an HTML reporter would write a report into the project and, on failure, open a browser
  // that hangs a non-interactive run.
  reporter: [
    ['list'],
    ['html', { open: 'never', outputFolder: path.join(path.dirname(OUTPUT_DIRECTORY), 'html-report') }],
  ],
  outputDir: OUTPUT_DIRECTORY,
  use: {
    baseURL: projectSettings.serverUrl,
    trace: 'retain-on-failure',
    reducedMotion: 'reduce',
    locale: 'en-US',
    timezoneId: 'UTC',
    colorScheme: 'light',
    deviceScaleFactor: DEVICE_SCALE_FACTOR,
    serviceWorkers: 'block',
    ...remote,
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'harness',
      testDir: here,
      testMatch: SHIPPED_SPECS,
      testIgnore: SWEEP_SPEC,
      workers: 1,
      use: browser,
    },
    ...sweepProjects,
    ...layerProjects.map((project: LayerProject) => ({
      ...project,
      workers: 1,
    })),
    ...(existsSync(PROJECT_SPECS)
      ? [
          {
            name: 'project',
            testDir: PROJECT_SPECS,
            testMatch: '**/*.e2e.spec.ts',
            workers: 1,
            use: browser,
          },
        ]
      : []),
  ],
  ...(command !== undefined && {
    webServer: [
      ...projectSettings.auxiliaryServers.map((server) => ({
        command: server.command,
        url: server.url,
        cwd: path.resolve(projectDirectory, server.cwd ?? '.'),
        reuseExistingServer: isReuseExistingServer,
        timeout: SERVER_TIMEOUT_MS,
        env: withPort({ ...serverEnvironment, __NEXT_PROCESSED_ENV: '' }, portOf(server.url)),
      })),
      {
        command,
        cwd: projectDirectory,
        url: projectSettings.serverUrl,
        reuseExistingServer: isReuseExistingServer,
        timeout: SERVER_TIMEOUT_MS,
        env: serverEnvironment,
      },
    ],
  }),
})

// A shallow copy rather than an assertion: a spread yields an anonymous object type, which carries the index
// signature an interface does not, so the structural annotation above holds without one.
const config: Readonly<Record<string, unknown>> = { ...declared }

export default config
