// The pinned browsers a verification run measures in, and the application clock it runs at.
//
// The browsers run in the Playwright image of the version the project resolves, started here before the
// suite and removed after it. The image is never pulled during verification: a run that fetched it
// would depend on the network and on what the registry serves that day, so a missing image is a failure
// that names the command to fetch it beforehand.
import { randomBytes } from 'node:crypto'
import { realpathSync } from 'node:fs'
import { createRequire } from 'node:module'
import { createConnection, type Socket } from 'node:net'
import { availableParallelism, totalmem } from 'node:os'
import path from 'node:path'
import { freePorts } from './browser-origin.js'
import {
  dockerCapacityArguments,
  dockerCapacityOf,
  isPinnedBrowserImage,
  type MachineCapacity,
  pinnedBrowserEndpoint,
  pinnedBrowserServerArguments,
  sweepWorkersAcross,
} from './browser-run.js'
import { failed, type GateResult, type RunResult, run } from './exec.js'
import { asText, readKey } from './json-shapes.js'
import { type ContainerOwner, claimOwnership, isOwnershipFailure, type OwnershipFailure } from './owned-containers.js'
import { type Member as Context, harnessDirectory, readJson } from './project.js'

const START_TIMEOUT_MS: number = 60_000

const SECRET_BYTES: number = 16

// Long enough for the server to start on a machine whose host network works, short enough that a machine
// whose does not falls back to the tunnel without delaying the run much.
const HOST_NETWORK_PROBE_MS: number = 20_000

const POLL_MS: number = 250

const MILLISECONDS_PER_SECOND: number = 1000

/** The browsers a run connects to, and how to remove them afterwards. */
export interface PinnedBrowsers {
  readonly endpoint: string
  readonly stop: () => void
  /** Whether pages reach the application only through the client's tunnel. */
  readonly isTunnelled: boolean
  /** The workers the sweep may use, given both the host and the machine the browsers run on. */
  readonly workers: number
}

const isListening = async (port: number): Promise<boolean> =>
  await new Promise<boolean>((resolve): void => {
    const socket: Socket = createConnection({ host: '127.0.0.1', port })
    socket.once('connect', (): void => {
      socket.destroy()
      resolve(true)
    })
    socket.once('error', (): void => {
      resolve(false)
    })
  })

const isListeningBy = async (port: number, deadline: number): Promise<boolean> => {
  if (await isListening(port)) {
    return true
  }
  if (performance.now() > deadline) {
    return false
  }
  await new Promise<void>((resolve): void => {
    setTimeout(resolve, POLL_MS)
  })
  return await isListeningBy(port, deadline)
}

interface ResolvedPlaywright {
  readonly directory: string
  readonly version: string
}

// The `playwright-core` the project's own `@playwright/test` loads, which is the version the image must
// carry browsers for.
const resolvePlaywrightCore = (context: Context): ResolvedPlaywright | undefined => {
  try {
    const fromProject: NodeJS.Require = createRequire(path.join(context.root, 'package.json'))
    const testManifest: string = fromProject.resolve('@playwright/test/package.json')
    const fromTest: NodeJS.Require = createRequire(testManifest)
    const playwrightManifest: string = fromTest.resolve('playwright/package.json')
    const coreManifest: string = createRequire(playwrightManifest).resolve('playwright-core/package.json')
    const manifest: unknown = createRequire(coreManifest)(coreManifest)
    const version: unknown =
      typeof manifest === 'object' && manifest !== null ? Reflect.get(manifest, 'version') : undefined
    return typeof version === 'string' ? { directory: realpathSync(path.dirname(coreManifest)), version } : undefined
  } catch {
    return undefined
  }
}

// This run, recorded on the server it starts, once the containers of runs that are gone have been removed.
const ownerAfterRemovingOrphans = (context: Context): ContainerOwner | GateResult => {
  const claim: ContainerOwner | OwnershipFailure = claimOwnership(context.root)
  return isOwnershipFailure(claim) ? failed(claim.problem, claim.details) : claim
}

const hostCapacity = (): MachineCapacity => ({
  processors: availableParallelism(),
  memoryBytes: totalmem(),
})

// The machine Docker runs the browsers in, which Docker Desktop and Colima keep smaller than the host; on
// Linux it is the host itself. Unknown when Docker cannot say, and the host then decides alone.
const browserMachineCapacity = (context: Context): MachineCapacity | undefined => {
  const reported: RunResult = run('docker', [...dockerCapacityArguments], {
    cwd: context.root,
  })
  return reported.code === 0 ? dockerCapacityOf(reported.stdout) : undefined
}

const readyImage = (context: Context, version: string): GateResult | { readonly image: string } => {
  const manifest: unknown = readJson(path.join(harnessDirectory(), 'package.json'))
  const image: string = asText(readKey(readKey(manifest, 'airness'), 'browserImage'))
  if (!isPinnedBrowserImage(version, image)) {
    return failed('the harness browser image pin does not match the installed Playwright', [
      'install the @airness/node and @playwright/test versions the Maven parent requires',
    ])
  }
  const inspected: RunResult = run('docker', ['image', 'inspect', '--format={{.Id}}', image], { cwd: context.root })
  if (inspected.code !== 0) {
    return failed(`the pinned browser image ${image} is not available locally`, [
      `run \`docker pull ${image}\` before verification; the run never fetches it`,
      ...(inspected.output.length > 0 ? [inspected.output] : []),
    ])
  }
  return { image }
}

/**
 * Start the pinned browser server for one end-to-end run.
 * @param context the member under test.
 * @param avoid the ports this run already chose for its own servers.
 * @returns the endpoint and its stop, or the failure that prevented starting it.
 */
export const startPinnedBrowsers = async (
  context: Context,
  avoid: readonly number[] = [],
): Promise<PinnedBrowsers | GateResult> => {
  const playwright: ResolvedPlaywright | undefined = resolvePlaywrightCore(context)
  if (playwright === undefined) {
    return failed("playwright-core could not be resolved through the project's @playwright/test", [
      "install the project dependencies; the pinned browsers run the project's own Playwright",
    ])
  }
  const ready: GateResult | { readonly image: string } = readyImage(context, playwright.version)
  if (!('image' in ready)) {
    return ready
  }
  const image: string = ready.image
  const owner: ContainerOwner | GateResult = ownerAfterRemovingOrphans(context)
  if (!('pid' in owner)) {
    return owner
  }
  // A Linux Docker engine shares the host's network, so the container reaches the application without a
  // tunnel. Docker Desktop's VM does not share the host's loopback, which shows as a server the host
  // cannot reach on its own loopback, and the run then publishes a port and tunnels instead.
  const shared: PinnedBrowsers | GateResult | undefined =
    process.platform === 'linux'
      ? await startServer(
          context,
          {
            image,
            playwrightCore: playwright.directory,
            isHostNetwork: true,
            owner,
            avoid,
          },
          HOST_NETWORK_PROBE_MS,
        )
      : undefined
  return shared === undefined || isStartFailure(shared)
    ? await startServer(
        context,
        {
          image,
          playwrightCore: playwright.directory,
          isHostNetwork: false,
          owner,
          avoid,
        },
        START_TIMEOUT_MS,
      )
    : shared
}

interface ServerPlan {
  readonly image: string
  readonly playwrightCore: string
  readonly isHostNetwork: boolean
  readonly owner: ContainerOwner
  /** Ports this run already chose for its own servers, which the browser server must not take. */
  readonly avoid: readonly number[]
}

const startServer = async (
  context: Context,
  plan: ServerPlan,
  timeoutMs: number,
): Promise<PinnedBrowsers | GateResult> => {
  const [port = 0] = await freePorts(1, plan.avoid)
  const name: string = `airness-browsers-${String(port)}`
  const secret: string = randomBytes(SECRET_BYTES).toString('hex')
  const started: RunResult = run('docker', pinnedBrowserServerArguments({ ...plan, name, port, secret }), {
    cwd: context.root,
  })
  if (started.code !== 0) {
    return failed('the pinned browser server did not start', [started.output])
  }
  const hooks: readonly {
    readonly signal: NodeJS.Signals
    readonly handle: () => void
  }[] = (['SIGINT', 'SIGTERM', 'SIGHUP'] as const).map((signal) => ({
    signal,
    handle: (): void => {
      stop()
      process.kill(process.pid, signal)
    },
  }))
  const stop = (): void => {
    for (const hook of hooks) {
      process.removeListener(hook.signal, hook.handle)
    }
    run('docker', ['rm', '--force', name], { cwd: context.root })
  }
  for (const hook of hooks) {
    process.once(hook.signal, hook.handle)
  }
  if (!(await isListeningBy(port, performance.now() + timeoutMs))) {
    const logs: RunResult = run('docker', ['logs', name], {
      cwd: context.root,
    })
    stop()
    return failed(`the pinned browser server did not listen within ${String(timeoutMs / MILLISECONDS_PER_SECOND)}s`, [
      logs.output,
    ])
  }
  return {
    endpoint: pinnedBrowserEndpoint(port, secret),
    stop,
    isTunnelled: !plan.isHostNetwork,
    workers: sweepWorkersAcross(hostCapacity(), browserMachineCapacity(context)),
  }
}

/** Whether a start answered a failure rather than browsers. */
export const isStartFailure = (started: PinnedBrowsers | GateResult): started is GateResult => !('endpoint' in started)
