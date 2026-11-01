import { describe, expect, it } from 'vitest'
import type { RunResult } from '@/exec.js'
import type { ContainerOwner, OwnershipFailure } from '@/owned-containers.js'
import {
  type CommandLaunch,
  commandAfterSeparator,
  publishedPortOf,
  randomSecret,
  type ServicesPlan,
  type ServicesRuntime,
  type StopSignal,
  SYSTEM_RUNTIME,
  serviceRunArguments,
  withServices,
} from '@/services.js'

const OWNER: ContainerOwner = {
  pid: 4321,
  started: 'Mon Oct 26 09:52:44 2026',
  host: 'builder',
}
const ARGV: readonly string[] = ['node', 'with-services.ts', '--', 'mvn', 'verify']

const answered = (stdout: string = '', code: number = 0): RunResult => ({
  code,
  output: stdout,
  stdout,
})

interface Recorded {
  readonly docker: (readonly string[])[]
  readonly launches: CommandLaunch[]
  readonly handlers: ((signal: NodeJS.Signals) => void)[]
  readonly unsubscribed: boolean[]
  /** Each guard started, with how many docker calls the run had made by then. */
  readonly guards: { readonly prefix: string; readonly dockerCalls: number }[]
}

interface Fake {
  readonly runtime: ServicesRuntime
  readonly recorded: Recorded
}

interface FakeOptions {
  readonly answer?: (commandArguments: readonly string[], recorded: Recorded) => RunResult | undefined
  readonly exit?: Promise<number>
  readonly claim?: ContainerOwner | OwnershipFailure
}

/* eslint-disable functional/immutable-data -- the stand-in machine records the docker calls, the command it starts and the signal subscription the helper makes, which is what the tests read */
// Everything the run started is listed by its prefix, as Docker lists a name filter.
const listing = (recorded: Recorded, filter: string, kind: 'container' | 'network'): RunResult => {
  const prefix: string = filter.slice('--filter=name='.length)
  const created: readonly string[] = recorded.docker.flatMap((call: readonly string[]): readonly string[] => {
    if (kind === 'container' && call[0] === 'run' && call[1] === '--detach') {
      return call.filter((part: string): boolean => part.startsWith('--name=')).map((part) => part.slice(7))
    }
    return kind === 'network' && call[1] === 'create' ? [call.at(-1) ?? ''] : []
  })
  return answered(created.filter((name: string): boolean => name.startsWith(prefix)).join('\n'))
}

// What Docker answers when a test does not say otherwise: a published port, or the run's own listing.
const machineAnswer = (commandArguments: readonly string[], recorded: Recorded): RunResult => {
  const filter: string = commandArguments.at(-1) ?? ''
  if (commandArguments[0] === 'port') {
    return answered('127.0.0.1:49153\n[::1]:49153')
  }
  if (commandArguments[0] === 'ps') {
    return listing(recorded, filter, 'container')
  }
  return commandArguments[1] === 'ls' ? listing(recorded, filter, 'network') : answered()
}

// A machine on which every docker call succeeds unless `answer` says otherwise, and the command exits with
// `exit`, so a test can deliver a signal while it runs.
const fake = (options: FakeOptions = {}): Fake => {
  const recorded: Recorded = {
    docker: [],
    launches: [],
    handlers: [],
    unsubscribed: [],
    guards: [],
  }
  const runtime: ServicesRuntime = {
    docker: (commandArguments: readonly string[]): RunResult => {
      recorded.docker.push(commandArguments)
      const override: RunResult | undefined = options.answer?.(commandArguments, recorded)
      if (override !== undefined) {
        return override
      }
      return machineAnswer(commandArguments, recorded)
    },
    start: async (launch: CommandLaunch): Promise<number> => {
      recorded.launches.push(launch)
      return await (options.exit ?? Promise.resolve(0))
    },
    claim: (): ContainerOwner | OwnershipFailure => options.claim ?? OWNER,
    pause: async (): Promise<void> => {
      await Promise.resolve()
    },
    onSignal: (handler: (signal: NodeJS.Signals) => void): (() => void) => {
      recorded.handlers.push(handler)
      return (): void => {
        recorded.unsubscribed.push(true)
      }
    },
    guard: (prefix: string): void => {
      recorded.guards.push({ prefix, dockerCalls: recorded.docker.length })
    },
    report: (line: string): void => {
      expect(line).toContain('[services]')
    },
  }
  return { runtime, recorded }
}

interface DeferredExit {
  readonly promise: Promise<number>
  readonly resolve: (code: number) => void
}

// An exit the test decides when to deliver, so a signal can arrive while the command runs.
const deferredExit = (): DeferredExit => {
  const holder: { resolve?: (code: number) => void } = {}
  const promise: Promise<number> = new Promise<number>((resolve): void => {
    holder.resolve = resolve
  })
  return {
    promise,
    resolve: (code: number): void => {
      holder.resolve?.(code)
    },
  }
}
// A list of what a callback was given, for a test of the machine itself.
const recorder = <T>(): { readonly seen: readonly T[]; readonly record: (value: T) => void } => {
  const seen: T[] = []
  return {
    seen,
    record: (value: T): void => {
      seen.push(value)
    },
  }
}
/* eslint-enable functional/immutable-data -- the tests below only read what the stand-in recorded */

const PLAN: ServicesPlan = {
  project: 'teron',
  containers: [
    {
      key: 'db',
      image: 'postgres:18-alpine',
      environment: { POSTGRES_DB: 'teron' },
      ports: ['5432/tcp'],
      ready: ['pg_isready'],
    },
    {
      key: 'mail',
      image: 'axllent/mailpit:v1.31',
      ports: ['1025/tcp'],
      ready: ['/mailpit', 'readyz'],
    },
  ],
  environment: (services) => ({
    DATABASE_PORT: String(services.port('db', '5432/tcp')),
  }),
}

const removals = (recorded: Recorded): readonly (readonly string[])[] =>
  recorded.docker.filter((call: readonly string[]): boolean => call[0] === 'rm' || call[1] === 'rm')

const signal = (recorded: Recorded, sent: NodeJS.Signals): void => {
  for (const handler of recorded.handlers) {
    handler(sent)
  }
}

describe('the command a wrapper runs', () => {
  it('follows the script path, or the separator after it', () => {
    expect(commandAfterSeparator(ARGV)).toEqual(['mvn', ['verify']])
    expect(commandAfterSeparator(['node', 'script.ts', 'pnpm', 'test'])).toEqual(['pnpm', ['test']])
    expect(() => commandAfterSeparator(['node', 'script.ts', '--'])).toThrow('a command is required')
  })
})

describe('a published port', () => {
  it('is read from the first binding Docker prints', () => {
    expect(publishedPortOf('127.0.0.1:49153\n[::1]:49153\n')).toBe(49_153)
    expect(publishedPortOf('[::]:49154')).toBe(49_154)
    expect(publishedPortOf('')).toBeUndefined()
    expect(publishedPortOf('no binding')).toBeUndefined()
  })
})

describe('a service container', () => {
  it('is owned by its run, published on loopback ports only, and removed when it stops', () => {
    expect(
      serviceRunArguments(
        {
          key: 'objects',
          image: 'rustfs/rustfs:1',
          alias: 'objects',
          ports: ['9000/tcp'],
          command: ['/data'],
          ready: [],
        },
        { prefix: 'dam-ab12', owner: OWNER, network: 'dam-ab12' },
      ),
    ).toEqual([
      'run',
      '--detach',
      '--rm',
      '--name=dam-ab12-objects',
      '--label=airness.owner.pid=4321',
      '--label=airness.owner.started=Mon Oct 26 09:52:44 2026',
      '--label=airness.owner.host=builder',
      '--network=dam-ab12',
      '--network-alias=objects',
      '--publish=127.0.0.1::9000/tcp',
      'rustfs/rustfs:1',
      '/data',
    ])
  })

  it('takes no alias without a network to be reached on', () => {
    const plain: readonly string[] = serviceRunArguments(
      { key: 'db', image: 'postgres', alias: 'db', ports: [], ready: [] },
      { prefix: 'x', owner: OWNER, network: undefined },
    )
    expect(plain.some((part: string): boolean => part.startsWith('--network'))).toBe(false)
  })
})

describe('a run against disposable services', () => {
  it('hands the command the published ports and removes every container afterwards', async () => {
    const { runtime, recorded }: Fake = fake()
    expect(await withServices(PLAN, ARGV, runtime)).toBe(0)
    const [launch] = recorded.launches
    expect(launch?.command).toBe('mvn')
    expect(launch?.commandArguments).toEqual(['verify'])
    expect(launch?.environment['DATABASE_PORT']).toBe('49153')
    const [removal] = removals(recorded)
    expect(removal?.slice(0, 2)).toEqual(['rm', '--force'])
    expect(removal).toHaveLength(4)
    expect(removal?.slice(2).every((name: string): boolean => name.startsWith('teron-'))).toBe(true)
    expect(recorded.unsubscribed).toEqual([true])
  })

  it('starts a guard for its prefix before it starts anything', async () => {
    const { runtime, recorded }: Fake = fake()
    expect(await withServices(PLAN, ARGV, runtime)).toBe(0)
    const started: string =
      recorded.docker
        .flat()
        .find((part: string): boolean => part.startsWith('--name='))
        ?.slice('--name='.length) ?? ''
    expect(recorded.guards).toEqual([{ prefix: started.slice(0, -'-db'.length), dockerCalls: 0 }])
  })

  it('gives two runs of one plan different names', async () => {
    const first: Fake = fake()
    const second: Fake = fake()
    await withServices(PLAN, ARGV, first.runtime)
    await withServices(PLAN, ARGV, second.runtime)
    expect(removals(first.recorded)[0]).not.toEqual(removals(second.recorded)[0])
  })

  it("answers with the command's verdict even when a container cannot be removed", async () => {
    const { runtime }: Fake = fake({
      answer: (call: readonly string[]): RunResult | undefined => (call[0] === 'rm' ? answered('busy', 1) : undefined),
      exit: Promise.resolve(3),
    })
    expect(await withServices(PLAN, ARGV, runtime)).toBe(3)
  })

  it('fails with the service log, and removes what it started, when a service never answers', async () => {
    const { runtime, recorded }: Fake = fake({
      answer: (call: readonly string[]): RunResult | undefined =>
        call[0] === 'exec' && call.includes('readyz') ? answered('', 1) : undefined,
    })
    expect(await withServices({ ...PLAN, readyTimeoutMs: 0 }, ARGV, runtime)).toBe(1)
    expect(recorded.launches).toEqual([])
    expect(recorded.docker.some((call: readonly string[]): boolean => call[0] === 'logs')).toBe(true)
    expect(removals(recorded)[0]).toHaveLength(4)
  })

  it('removes the containers started before one that could not start', async () => {
    const { runtime, recorded }: Fake = fake({
      answer: (call: readonly string[]): RunResult | undefined =>
        call[0] === 'run' && call.includes('axllent/mailpit:v1.31') ? answered('no such image', 1) : undefined,
    })
    expect(await withServices(PLAN, ARGV, runtime)).toBe(1)
    const [removal] = removals(recorded)
    expect(removal?.slice(0, 2)).toEqual(['rm', '--force'])
    expect(removal?.some((name: string): boolean => /^teron-[0-9a-f]{12}-db$/u.test(name))).toBe(true)
  })

  it('passes a stop signal on to the command and removes the containers once it exits', async () => {
    const exit: DeferredExit = deferredExit()
    const { runtime, recorded }: Fake = fake({ exit: exit.promise })
    const outcome: Promise<number> = withServices(PLAN, ARGV, runtime)
    await new Promise<void>((resolve): void => {
      setTimeout(resolve, 0)
    })
    signal(recorded, 'SIGTERM')
    expect(recorded.launches[0]?.stop.aborted).toBe(true)
    expect(recorded.launches[0]?.stop.reason).toBe('SIGTERM')
    expect(removals(recorded)).toEqual([])
    exit.resolve(143)
    expect(await outcome).toBe(143)
    expect(removals(recorded)).toHaveLength(1)
  })

  it('runs no command when it is stopped while the services start', async () => {
    const { runtime, recorded }: Fake = fake({
      answer: (call: readonly string[], seen: Recorded): RunResult | undefined => {
        if (call[0] !== 'exec') {
          return undefined
        }
        signal(seen, 'SIGINT')
        return answered('', 1)
      },
    })
    expect(await withServices(PLAN, ARGV, runtime)).toBe(130)
    expect(recorded.launches).toEqual([])
    expect(removals(recorded)).toHaveLength(1)
  })

  it('creates a private network for one-off containers and removes it last', async () => {
    const { runtime, recorded }: Fake = fake()
    const networked: ServicesPlan = {
      ...PLAN,
      network: true,
      prepare: (services) => {
        services.once('amazon/aws-cli:2', ['s3api', 'create-bucket'], { AWS_DEFAULT_REGION: 'us-east-1' })
      },
    }
    expect(await withServices(networked, ARGV, runtime)).toBe(0)
    const network: string | undefined = recorded.docker.find((call) => call[1] === 'create')?.at(-1)
    const once: readonly string[] | undefined = recorded.docker.find((call) => call.includes('amazon/aws-cli:2'))
    expect(once).toContain(`--network=${String(network)}`)
    expect(once?.indexOf('--env=AWS_DEFAULT_REGION=us-east-1')).toBeLessThan(once?.indexOf('amazon/aws-cli:2') ?? 0)
    expect(recorded.docker.at(-1)).toEqual(['network', 'rm', network])
  })

  it('refuses a port of a service the plan does not declare', async () => {
    const { runtime, recorded }: Fake = fake()
    const misnamed: ServicesPlan = {
      ...PLAN,
      environment: (services) => ({ X: String(services.port('cache', '1')) }),
    }
    expect(await withServices(misnamed, ARGV, runtime)).toBe(1)
    expect(recorded.launches).toEqual([])
  })

  it('starts nothing when the run cannot claim its containers', async () => {
    const { runtime, recorded }: Fake = fake({
      claim: { problem: 'no ps', details: [] },
    })
    expect(await withServices(PLAN, ARGV, runtime)).toBe(1)
    expect(recorded.docker).toEqual([])
  })

  it('makes credentials that need no quoting', () => {
    expect(randomSecret()).toMatch(/^[0-9a-f]{36}$/u)
    expect(randomSecret()).not.toBe(randomSecret())
  })
})

const NODE: string = process.execPath

describe('the machine the helper runs on', () => {
  it("starts the command with the run's environment and answers with its exit code", async () => {
    const stop: StopSignal = new AbortController().signal
    const code: number = await SYSTEM_RUNTIME.start({
      command: NODE,
      commandArguments: ['-e', "process.exit(process.env['SERVICE_CODE'] === 'seven' ? 7 : 1)"],
      environment: { ...process.env, SERVICE_CODE: 'seven' },
      stop,
    })
    expect(code).toBe(7)
  })

  it('sends the command the stop signal and answers with 128 plus its number', async () => {
    const stopping: AbortController = new AbortController()
    const exited: Promise<number> = SYSTEM_RUNTIME.start({
      command: NODE,
      commandArguments: ['-e', 'setTimeout(() => {}, 60_000)'],
      environment: process.env,
      stop: stopping.signal,
    })
    await SYSTEM_RUNTIME.pause(200)
    stopping.abort('SIGTERM')
    expect(await exited).toBe(143)
  })

  it('answers 1 for a command that cannot be started', async () => {
    const code: number = await SYSTEM_RUNTIME.start({
      command: '/nonexistent/command',
      commandArguments: [],
      environment: process.env,
      stop: new AbortController().signal,
    })
    expect(code).toBe(1)
  })

  it('hears a stop signal until it unsubscribes', () => {
    const heard: { readonly seen: readonly NodeJS.Signals[]; readonly record: (value: NodeJS.Signals) => void } =
      recorder<NodeJS.Signals>()
    const unsubscribe: () => void = SYSTEM_RUNTIME.onSignal(heard.record)
    process.emit('SIGHUP', 'SIGHUP')
    unsubscribe()
    process.emit('SIGHUP', 'SIGHUP')
    expect(heard.seen).toEqual(['SIGHUP'])
  })
})
