// The disposable services a test wrapper runs one command against: containers of this run's own, published on
// loopback ports Docker chooses, handed to the command through its environment, and removed afterwards. No two
// runs share a name, a port or the data of a stack somebody runs for real, so verifications of several
// projects, or of two worktrees of one, can run on one machine at the same time.
//
// A run that is asked to stop, by SIGINT, SIGTERM or SIGHUP, passes the signal on to the command and removes
// its containers once the command has exited. A wrapper ended outright, or ended with the process group a
// package manager runs it in, cannot, so before it starts anything each run also starts a guard of its own,
// outside the wrapper's process tree and deaf to the stop signals, which removes them once the wrapper is gone. Each container
// also records the run that owns it, as the pinned browser server does, so a machine that went down with
// both leaves containers the next run removes. Everything a run creates carries the run's own random prefix,
// and every removal finds it by that prefix, so a step that failed halfway leaves nothing a removal misses.
//
// The image gate asks for the services as a fresh deployment finds them, through a variable of the harness's own
// in the environment it starts the wrapper with. A run asked that way runs no `prepare`, which is where the
// application's own data steps, such as migrations, run, so an image that cannot bring an empty database to its
// schema itself fails rather than passing on a schema the wrapper laid down. It also gives the containers a
// network of the run's own and hands the command a description of what it started, so the image, run as a
// container on that network, reaches each service by its container rather than by a port on the host's loopback.
import { type ChildProcess, spawn } from 'node:child_process'
import { randomBytes } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import { type RunResult, run } from './exec.js'
import {
  type ContainerOwner,
  claimOwnership,
  isOwnershipFailure,
  type OwnershipFailure,
  ownerLabelArguments,
} from './owned-containers.js'
import { pause, subscribeToStops } from './process-tree.js'
import {
  IMAGE_VERIFICATION_ON,
  IMAGE_VERIFICATION_VARIABLE,
  type PublishedPort,
  SERVICES_DESCRIPTION_VARIABLE,
  type ServicesDescription,
} from './services-description.js'
import { guardArguments, removeByPrefix } from './services-guard.js'

/** One container a run needs. */
export interface ServiceContainer {
  /** The name the plan refers to it by, unique within the plan. */
  readonly key: string
  readonly image: string
  readonly environment?: Readonly<Record<string, string>>
  /** The container ports published on a loopback port Docker chooses, such as `5432/tcp`. */
  readonly ports: readonly string[]
  /** The name other containers on the run's network reach it by. */
  readonly alias?: string
  /** Arguments passed after the image, replacing its default command's arguments. */
  readonly command?: readonly string[]
  /** A command run inside the container, which succeeds once the service answers. */
  readonly ready: readonly string[]
}

/** The running services, as a plan's later steps see them. */
export interface Services {
  /**
   * The loopback port a container port was published on.
   * @param key the container's key.
   * @param containerPort the container port, such as `5432/tcp`.
   * @returns the host port.
   */
  readonly port: (key: string, containerPort: string) => number
  /** The run's private network, when the plan asked for one. */
  readonly network: string | undefined
  /**
   * Run a one-off container on the run's network, such as one that creates a bucket, and wait for it.
   * @param image the image.
   * @param commandArguments the arguments after the image.
   * @param environment variables the container is started with, such as the credentials of a service.
   */
  readonly once: (
    image: string,
    commandArguments: readonly string[],
    environment?: Readonly<Record<string, string>>,
  ) => void
}

/** What a wrapper starts, and what it hands the command. */
export interface ServicesPlan {
  /** The prefix of every name this run creates; a random run identifier follows it. */
  readonly project: string
  readonly containers: readonly ServiceContainer[]
  /** Whether the containers share a private network of this run's own. */
  readonly network?: boolean
  /** The variables the command reads its services from. */
  readonly environment: (services: Services) => Readonly<Record<string, string>>
  /**
   * A step run once every service answers and before the command, such as applying migrations. The image gate's
   * run skips it, so provisioning that a deployment's services have before the application first starts, such as
   * a bucket, belongs in a container's `ready` command instead.
   */
  readonly prepare?: (services: Services, environment: Readonly<Record<string, string>>) => void
  /** How long every service together may take to answer. */
  readonly readyTimeoutMs?: number
}

/** The abort signal of a run, whose reason is the stop signal the run was sent. */
export interface StopSignal extends AbortSignal {
  readonly reason: NodeJS.Signals | undefined
}

/** The command a run starts, with what it needs to be stopped. */
export interface CommandLaunch {
  readonly command: string
  readonly commandArguments: readonly string[]
  readonly environment: NodeJS.ProcessEnv
  /** Aborted with the stop signal once the run is asked to stop, which the command is then sent. */
  readonly stop: StopSignal
}

/** What the helper does to the machine, replaceable so its decisions can be measured without Docker. */
export interface ServicesRuntime {
  /** The environment the run inherits and hands on to the command. */
  readonly environment: NodeJS.ProcessEnv
  readonly docker: (commandArguments: readonly string[]) => RunResult
  /** Start the command; the promise is its exit code, or 128 plus the signal number when a signal ended it. */
  readonly start: (launch: CommandLaunch) => Promise<number>
  readonly claim: () => ContainerOwner | OwnershipFailure
  readonly pause: (milliseconds: number) => Promise<void>
  /** Subscribe to the stop signals; the returned function unsubscribes. */
  readonly onSignal: (handler: (signal: NodeJS.Signals) => void) => () => void
  /** Start the guard that removes what the run with this prefix started once this process is gone. */
  readonly guard: (prefix: string) => void
  readonly report: (line: string) => void
}

const SIGNAL_NUMBERS: Readonly<Record<string, number>> = {
  SIGHUP: 1,
  SIGINT: 2,
  SIGTERM: 15,
}
const SIGNAL_EXIT_BASE: number = 128
const RUN_ID_BYTES: number = 6
const SECRET_BYTES: number = 18
const READY_TIMEOUT_MS: number = 120_000
const READY_INTERVAL_MS: number = 500
const LOG_LINES: string = '40'
const ARGV_FIRST_ARGUMENT: number = 2
const PREFIX: string = '[services]'

const signalled = (signal: NodeJS.Signals): number => SIGNAL_EXIT_BASE + (SIGNAL_NUMBERS[signal] ?? 0)

const startCommand = async (launch: CommandLaunch): Promise<number> =>
  await new Promise<number>((resolve): void => {
    const child: ChildProcess = spawn(launch.command, [...launch.commandArguments], {
      env: launch.environment,
      stdio: 'inherit',
    })
    const forward = (): void => {
      child.kill(launch.stop.reason ?? 'SIGTERM')
    }
    launch.stop.addEventListener('abort', forward, { once: true })
    child.once('error', (error: Error): void => {
      console.error(`${PREFIX} ${launch.command} could not be started: ${error.message}`)
      resolve(1)
    })
    child.once('exit', (code: number | null, signal: NodeJS.Signals | null): void => {
      launch.stop.removeEventListener('abort', forward)
      resolve(code ?? (signal === null ? 1 : signalled(signal)))
    })
  })

/** Where a guard comes from and where it says that it could not start, replaceable only by tests. */
export interface GuardStart {
  /** The guard's entry point. */
  readonly script?: string
  /** The Node executable that runs it. */
  readonly command?: string
  /** The shell that starts it in the background and leaves it behind. */
  readonly shell?: string
  /** Where a guard that could not be started is reported. */
  readonly complain?: (line: string) => void
}

// Started in the background by a shell that ends at once, the guard is left to the system, so it is no longer
// under the wrapper: what stops a run, which sends its signal to every process under the command it ran, does
// not reach it. The guard also ignores the stop signals itself, since Node resets every signal a shell set to
// be ignored, so only SIGKILL ends it before its work is done.
const GUARD_LAUNCH: string = '"$@" &'

/**
 * Start the guard of a run as a process of its own: detached, so it leads a session of its own that no
 * terminal signal and no end of the wrapper's process group reaches, outside the wrapper's process tree, and
 * writing nowhere, so no output pipe that closes with the wrapper can end it. The wrapper does not wait for it.
 * @param prefix the run's prefix.
 * @param start where the guard comes from, the harness's own entry point run by this Node unless a test says.
 */
export const startGuard = (prefix: string, start: GuardStart = {}): void => {
  const script: string = start.script ?? fileURLToPath(import.meta.resolve('#harness/services-guard-main'))
  const guard: ChildProcess = spawn(
    start.shell ?? '/bin/sh',
    [
      '-c',
      GUARD_LAUNCH,
      'airness-services-guard',
      start.command ?? process.execPath,
      ...guardArguments(script, process.pid, prefix),
    ],
    {
      detached: true,
      stdio: 'ignore',
      cwd: process.cwd(),
    },
  )
  guard.once('error', (error: Error): void => {
    ;(start.complain ?? console.error)(
      `${PREFIX} the guard of ${prefix} could not be started, so only this process removes it: ${error.message}`,
    )
  })
  guard.unref()
}

/** The machine itself. */
export const SYSTEM_RUNTIME: ServicesRuntime = {
  environment: process.env,
  docker: (commandArguments: readonly string[]): RunResult => run('docker', commandArguments, { cwd: process.cwd() }),
  start: startCommand,
  claim: (): ContainerOwner | OwnershipFailure => claimOwnership(process.cwd()),
  pause,
  onSignal: subscribeToStops,
  guard: (prefix: string): void => {
    startGuard(prefix)
  },
  report: (line: string): void => {
    console.info(line)
  },
}

/**
 * A credential of this run's own, for a service the run starts.
 * @returns letters and digits only, so it needs no quoting in a URL or a shell.
 */
export const randomSecret = (): string => randomBytes(SECRET_BYTES).toString('hex')

/**
 * The command a wrapper runs, read off argv, which begins with the node binary and the script path; an
 * explicit `--` pushes the command one place further along.
 * @param argv the process arguments.
 * @returns the command and its arguments.
 */
export const commandAfterSeparator = (argv: readonly string[]): readonly [string, readonly string[]] => {
  const index: number = argv[ARGV_FIRST_ARGUMENT] === '--' ? ARGV_FIRST_ARGUMENT + 1 : ARGV_FIRST_ARGUMENT
  const command: string | undefined = argv[index]
  if (command === undefined || command.length === 0) {
    throw new Error(`${PREFIX} a command is required after \`--\``)
  }
  return [command, argv.slice(index + 1)]
}

/**
 * Read the host port from what `docker port` printed: the first binding, which is the loopback one.
 * @param printed what `docker port <container> <port>` wrote.
 * @returns the port, or undefined when none is named.
 */
export const publishedPortOf = (printed: string): number | undefined => {
  const first: string = printed.split('\n', 1)[0]?.trim() ?? ''
  const port: number = Number(first.slice(first.lastIndexOf(':') + 1))
  return first.includes(':') && Number.isSafeInteger(port) && port > 0 ? port : undefined
}

const environmentArguments = (environment: Readonly<Record<string, string>>): readonly string[] =>
  Object.entries(environment).map(([key, value]: readonly [string, string]): string => `--env=${key}=${value}`)

/** Where a run's containers are started: its prefix, its owner and its network. */
export interface RunPlacement {
  readonly prefix: string
  readonly owner: ContainerOwner
  readonly network: string | undefined
}

/**
 * The `docker run` arguments that start one service.
 * @param container the service.
 * @param placement the run it belongs to.
 * @returns the arguments.
 */
export const serviceRunArguments = (container: ServiceContainer, placement: RunPlacement): readonly string[] => [
  'run',
  '--detach',
  '--rm',
  `--name=${placement.prefix}-${container.key}`,
  ...ownerLabelArguments(placement.owner),
  ...(placement.network === undefined ? [] : [`--network=${placement.network}`]),
  ...(placement.network === undefined || container.alias === undefined ? [] : [`--network-alias=${container.alias}`]),
  ...container.ports.map((port: string): string => `--publish=127.0.0.1::${port}`),
  ...environmentArguments(container.environment ?? {}),
  container.image,
  ...(container.command ?? []),
]

const docked = (runtime: ServicesRuntime, commandArguments: readonly string[], what: string): string => {
  const result: RunResult = runtime.docker(commandArguments)
  if (result.code !== 0) {
    throw new Error(`${PREFIX} ${what} failed: ${result.output.trim()}`)
  }
  return result.stdout.trim()
}

/** One service being waited for. */
interface Awaited {
  readonly name: string
  readonly ready: readonly string[]
  readonly deadline: number
  readonly stop: StopSignal
}

const awaitReady = async (runtime: ServicesRuntime, awaited: Awaited): Promise<void> => {
  if (awaited.stop.aborted) {
    throw new Error(`${PREFIX} stopped before ${awaited.name} answered`)
  }
  if (runtime.docker(['exec', awaited.name, ...awaited.ready]).code === 0) {
    return
  }
  if (performance.now() > awaited.deadline) {
    const logs: RunResult = runtime.docker(['logs', '--tail', LOG_LINES, awaited.name])
    throw new Error(
      `${PREFIX} ${awaited.name} did not answer \`${awaited.ready.join(' ')}\` in time:\n${logs.output.trim()}`,
    )
  }
  await runtime.pause(READY_INTERVAL_MS)
  await awaitReady(runtime, awaited)
}

// Found by the run's prefix rather than remembered, so whatever step failed, every container and network the
// run created is removed. A failed removal is reported and never thrown: the command's verdict is what the run
// answers with.
const removeRun = (runtime: ServicesRuntime, prefix: string): void => {
  for (const failure of removeByPrefix(runtime.docker, prefix)) {
    console.error(`${PREFIX} ${failure}`)
  }
  runtime.report(`${PREFIX} Removed what ${prefix} started.`)
}

const servicesOf = (runtime: ServicesRuntime, plan: ServicesPlan, placement: RunPlacement): Services => ({
  port: (key: string, containerPort: string): number => {
    if (!plan.containers.some((container: ServiceContainer): boolean => container.key === key)) {
      throw new Error(`${PREFIX} ${plan.project} declares no service "${key}"`)
    }
    const name: string = `${placement.prefix}-${key}`
    const port: number | undefined = publishedPortOf(
      docked(runtime, ['port', name, containerPort], `docker port ${name}`),
    )
    if (port === undefined) {
      throw new Error(`${PREFIX} ${name} publishes no ${containerPort}`)
    }
    return port
  },
  network: placement.network,
  once: (
    image: string,
    commandArguments: readonly string[],
    environment: Readonly<Record<string, string>> = {},
  ): void => {
    docked(
      runtime,
      [
        'run',
        '--rm',
        ...(placement.network === undefined ? [] : [`--network=${placement.network}`]),
        ...environmentArguments(environment),
        image,
        ...commandArguments,
      ],
      `the one-off ${image}`,
    )
  },
})

const startAll = async (
  runtime: ServicesRuntime,
  plan: ServicesPlan,
  placement: RunPlacement,
  stop: StopSignal,
): Promise<Services> => {
  if (placement.network !== undefined) {
    docked(
      runtime,
      ['network', 'create', ...ownerLabelArguments(placement.owner), placement.network],
      `creating the network ${placement.network}`,
    )
  }
  for (const container of plan.containers) {
    docked(runtime, serviceRunArguments(container, placement), `starting ${placement.prefix}-${container.key}`)
  }
  const deadline: number = performance.now() + (plan.readyTimeoutMs ?? READY_TIMEOUT_MS)
  await Promise.all(
    plan.containers.map(async (container: ServiceContainer): Promise<void> => {
      await awaitReady(runtime, {
        name: `${placement.prefix}-${container.key}`,
        ready: container.ready,
        deadline,
        stop,
      })
    }),
  )
  return servicesOf(runtime, plan, placement)
}

/** A run with its command, its placement and the controller its stop signals abort. */
interface Started {
  readonly plan: ServicesPlan
  readonly command: readonly [string, readonly string[]]
  readonly placement: RunPlacement
  readonly stopping: AbortController
  /** Whether the image gate asked for the services as a fresh deployment finds them. */
  readonly isForImage: boolean
}

const containerPortOf = (port: string): number => Number(port.split('/', 1)[0])

/**
 * What a run started and handed over, for the image gate's container on the run's network.
 * @param plan the plan.
 * @param services the running services.
 * @param environment the variables the plan handed over.
 * @returns the description, naming the run's network, which a run for the image gate always has.
 */
const describeRun = (
  plan: ServicesPlan,
  services: Services,
  environment: Readonly<Record<string, string>>,
): ServicesDescription => {
  const prefix: string = services.network ?? ''
  return {
    prefix,
    network: prefix,
    variables: Object.keys(environment),
    published: plan.containers.flatMap((container: ServiceContainer): readonly PublishedPort[] =>
      container.ports.map(
        (port: string): PublishedPort => ({
          port: services.port(container.key, port),
          host: `${prefix}-${container.key}`,
          containerPort: containerPortOf(port),
        }),
      ),
    ),
  }
}

// The variables the command is handed: the plan's own, and for the image gate the description of the run beside
// them, once no preparation has run, since the image's services start as a deployment's do.
const handedOver = (
  started: Started,
  services: Services,
  environment: Readonly<Record<string, string>>,
): Readonly<Record<string, string>> => {
  if (!started.isForImage) {
    started.plan.prepare?.(services, environment)
    return environment
  }
  return {
    ...environment,
    [SERVICES_DESCRIPTION_VARIABLE]: JSON.stringify(describeRun(started.plan, services, environment)),
  }
}

const runAgainst = async (runtime: ServicesRuntime, started: Started): Promise<number> => {
  const { plan, command, placement, stopping } = started
  const stop: StopSignal = stopping.signal
  try {
    const services: Services = await startAll(runtime, plan, placement, stop)
    if (stop.reason !== undefined) {
      return signalled(stop.reason)
    }
    const environment: Readonly<Record<string, string>> = handedOver(started, services, plan.environment(services))
    const state: string = started.isForImage ? ' empty, as the image gate asks, with no preparation run' : ''
    runtime.report(`${PREFIX} the services of ${placement.prefix} answer${state}; running ${command[0]}.`)
    return await runtime.start({
      command: command[0],
      commandArguments: command[1],
      environment: { ...runtime.environment, ...environment },
      stop,
    })
  } catch (error: unknown) {
    if (stop.reason !== undefined) {
      return signalled(stop.reason)
    }
    console.error(error instanceof Error ? error.message : String(error))
    return 1
  }
}

/**
 * Start a plan's services, run the command after `--` against them, and remove them again.
 * @param plan what to start and what to hand the command.
 * @param argv the process arguments, the command following the script path or `--`.
 * @param runtime the machine, replaced only by tests.
 * @returns the command's exit code, 1 when the services could not be started, or 128 plus the number of the
 *   signal that stopped the run.
 */
export const withServices = async (
  plan: ServicesPlan,
  argv: readonly string[] = process.argv,
  runtime: ServicesRuntime = SYSTEM_RUNTIME,
): Promise<number> => {
  const command: readonly [string, readonly string[]] = commandAfterSeparator(argv)
  const claim: ContainerOwner | OwnershipFailure = runtime.claim()
  if (isOwnershipFailure(claim)) {
    console.error(`${PREFIX} ${claim.problem}\n${claim.details.join('\n')}`)
    return 1
  }
  const prefix: string = `${plan.project}-${randomBytes(RUN_ID_BYTES).toString('hex')}`
  const isForImage: boolean = runtime.environment[IMAGE_VERIFICATION_VARIABLE] === IMAGE_VERIFICATION_ON
  const placement: RunPlacement = {
    prefix,
    owner: claim,
    network: isForImage || plan.network === true ? prefix : undefined,
  }
  runtime.guard(prefix)
  const stopping: AbortController = new AbortController()
  const unsubscribe: () => void = runtime.onSignal((signal: NodeJS.Signals): void => {
    stopping.abort(signal)
  })
  try {
    return await runAgainst(runtime, { plan, command, placement, stopping, isForImage })
  } finally {
    unsubscribe()
    removeRun(runtime, prefix)
  }
}
