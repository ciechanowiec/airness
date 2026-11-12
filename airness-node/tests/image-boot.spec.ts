import { createServer, type Server } from 'node:http'
import type { AddressInfo } from 'node:net'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import type { RunResult } from '@/exec.js'
import {
  type BootRequest,
  type BootRuntime,
  type BootVerdict,
  bootImage,
  parseBootRequest,
  probeOnce,
} from '@/image-boot.js'
import { PROCESS_TIME } from './helpers/process-time.js'

const REQUEST: BootRequest = {
  image: 'airness-image-ab12:verification',
  containerPort: 3000,
  serverUrl: 'http://localhost:3000',
  serverUrlVariables: ['NEXT_PUBLIC_SERVER_URL'],
  probes: ['/', '/admin'],
}

const DESCRIPTION: string = JSON.stringify({
  prefix: 'shop-ab12',
  network: 'shop-ab12',
  variables: ['DATABASE_URL', 'CACHE_URL', 'SIGNING_SECRET'],
  published: [
    { port: 50_001, host: 'shop-ab12-db', containerPort: 5432 },
    { port: 50_002, host: 'shop-ab12-cache', containerPort: 6379 },
  ],
})

const ENVIRONMENT: Readonly<Record<string, string>> = {
  AIRNESS_SERVICES: DESCRIPTION,
  DATABASE_URL: 'postgres://shop:secret@127.0.0.1:50001/shop',
  CACHE_URL: 'redis://localhost:50002',
  SIGNING_SECRET: 'signing-secret',
  HOME: '/home/builder',
}

type Probe = Awaited<ReturnType<BootRuntime['probe']>>

/** One docker call the stand-in machine saw, with the variables it was started with. */
interface DockerCall {
  readonly commandArguments: readonly string[]
  readonly environment: Readonly<Record<string, string>>
}

interface Machine {
  readonly runtime: BootRuntime
  readonly calls: readonly DockerCall[]
  readonly asked: readonly string[]
}

interface MachineOptions {
  /** What a probe of the path answers on the given attempt, counting from 0. */
  readonly answer?: (path: string, attempt: number) => Probe
  readonly running?: boolean
  readonly started?: RunResult
  readonly environment?: Readonly<Record<string, string>>
}

const answered = (stdout: string, code: number = 0): RunResult => ({ code, output: stdout, stdout })

const SERVED_PORT: number = 41_234

/* eslint-disable functional/immutable-data -- the stand-in machine records the docker calls and the requests the boot makes, which is what the tests read */
// A machine on which time moves one second a pause, so a boot that never answers reaches its deadline at once.
const machine = (options: MachineOptions = {}): Machine => {
  const calls: DockerCall[] = []
  const asked: string[] = []
  const attempts: Map<string, number> = new Map<string, number>()
  const clock: { now: number } = { now: 0 }
  // What docker answers each command with: the started container, its state, and the log it wrote.
  const answers: Readonly<Record<string, RunResult>> = {
    run: options.started ?? answered('container-id'),
    inspect: answered(options.running === false ? 'false 1\n' : 'true 0\n'),
    logs: answered('Error: relation "users" does not exist\n'),
  }
  const runtime: BootRuntime = {
    docker: (commandArguments: readonly string[], environment: Readonly<Record<string, string>> = {}): RunResult => {
      calls.push({ commandArguments, environment })
      return answers[commandArguments[0] ?? ''] ?? answered('')
    },
    probe: async (url: string): Promise<Probe> => {
      asked.push(url)
      const path: string = new URL(url).pathname
      const attempt: number = attempts.get(path) ?? 0
      attempts.set(path, attempt + 1)
      await Promise.resolve()
      return options.answer?.(path, attempt) ?? { status: 200 }
    },
    freePort: async (): Promise<number> => {
      await Promise.resolve()
      return SERVED_PORT
    },
    pause: async (milliseconds: number): Promise<void> => {
      clock.now += milliseconds
      await Promise.resolve()
    },
    now: (): number => clock.now,
    environment: options.environment ?? ENVIRONMENT,
  }
  return { runtime, calls, asked }
}
/* eslint-enable functional/immutable-data -- the tests below only read what the stand-in recorded */

// The call that started the container.
const runCall = (recorded: Machine): DockerCall | undefined =>
  recorded.calls.find((call: DockerCall): boolean => call.commandArguments[0] === 'run')

describe('a boot of the image', () => {
  it('starts the container on the run network with the services it reaches there and its served origin', async () => {
    const recorded: Machine = machine()
    expect(await bootImage(REQUEST, recorded.runtime)).toEqual({ ok: true, findings: [] })
    const run: DockerCall | undefined = runCall(recorded)
    expect(run?.commandArguments).toEqual([
      'run',
      '--detach',
      '--name=shop-ab12-image',
      '--network=shop-ab12',
      `--publish=127.0.0.1:${String(SERVED_PORT)}:3000`,
      '--env=DATABASE_URL',
      '--env=CACHE_URL',
      '--env=SIGNING_SECRET',
      '--env=NEXT_PUBLIC_SERVER_URL',
      'airness-image-ab12:verification',
    ])
    expect(run?.environment).toEqual({
      DATABASE_URL: 'postgres://shop:secret@shop-ab12-db:5432/shop',
      CACHE_URL: 'redis://shop-ab12-cache:6379',
      SIGNING_SECRET: 'signing-secret',
      NEXT_PUBLIC_SERVER_URL: `http://localhost:${String(SERVED_PORT)}`,
    })
    expect(recorded.asked).toEqual([
      `http://localhost:${String(SERVED_PORT)}/`,
      `http://localhost:${String(SERVED_PORT)}/admin`,
    ])
    expect(recorded.calls.at(-1)?.commandArguments).toEqual(['rm', '--force', 'shop-ab12-image'])
  })

  it('rewrites only an address of a published port, whatever loopback name it uses', async () => {
    const recorded: Machine = machine({
      environment: {
        ...ENVIRONMENT,
        DATABASE_URL: 'jdbc:postgresql://[::1]:50001/shop?fallback=127.0.0.1:500010',
        CACHE_URL: 'redis://127.0.0.1:50003',
      },
    })
    await bootImage(REQUEST, recorded.runtime)
    expect(runCall(recorded)?.environment).toMatchObject({
      DATABASE_URL: 'jdbc:postgresql://shop-ab12-db:5432/shop?fallback=127.0.0.1:500010',
      CACHE_URL: 'redis://127.0.0.1:50003',
    })
  })

  it('serves on the host of the declared origin and asks each path of the origin itself', async () => {
    const recorded: Machine = machine()
    await bootImage({ ...REQUEST, serverUrl: 'http://127.0.0.1:8080/shop/', probes: ['/shop/'] }, recorded.runtime)
    expect(recorded.asked).toEqual([`http://127.0.0.1:${String(SERVED_PORT)}/shop/`])
    expect(runCall(recorded)?.environment['NEXT_PUBLIC_SERVER_URL']).toBe(`http://127.0.0.1:${String(SERVED_PORT)}`)
  })

  it('waits while the application starts and passes once every path answers below 500', async () => {
    const recorded: Machine = machine({
      answer: (path: string, attempt: number): Probe =>
        attempt < 2 ? { problem: 'ECONNREFUSED' } : { status: path === '/admin' ? 302 : 404 },
    })
    const verdict: BootVerdict = await bootImage(REQUEST, recorded.runtime)
    expect(verdict.ok).toBe(true)
    expect(recorded.asked).toHaveLength(6)
  })

  it('fails an application that answers 500 until the deadline, naming the path and showing its log', async () => {
    const recorded: Machine = machine({
      answer: (path: string): Probe => ({ status: path === '/admin' ? 500 : 200 }),
    })
    const verdict: BootVerdict = await bootImage(REQUEST, recorded.runtime)
    expect(verdict.ok).toBe(false)
    expect(verdict.findings.slice(0, 2)).toEqual([
      'the application did not answer below 500 within 120 seconds of starting:',
      'GET /admin answered 500',
    ])
    expect(verdict.findings).toContain('| Error: relation "users" does not exist')
    expect(recorded.calls.at(-1)?.commandArguments).toEqual(['rm', '--force', 'shop-ab12-image'])
  })

  it('names a path that never answered at all', async () => {
    const recorded: Machine = machine({ answer: (): Probe => ({ problem: 'ECONNREFUSED' }) })
    const verdict: BootVerdict = await bootImage(REQUEST, recorded.runtime)
    expect(verdict.findings).toContain('GET / got no answer: ECONNREFUSED')
  })

  it('fails at once, with its log, a container that stopped before it answered', async () => {
    const recorded: Machine = machine({ running: false })
    const verdict: BootVerdict = await bootImage(REQUEST, recorded.runtime)
    expect(verdict.findings[0]).toBe('the container stopped with exit code 1 before it answered')
    expect(verdict.findings).toContain('| Error: relation "users" does not exist')
    expect(recorded.asked).toEqual([])
  })

  it('fails a container that could not be started, and still removes what may have been created', async () => {
    const recorded: Machine = machine({ started: answered('pull access denied', 125) })
    const verdict: BootVerdict = await bootImage(REQUEST, recorded.runtime)
    expect(verdict).toEqual({ ok: false, findings: ['the container could not be started: pull access denied'] })
    expect(recorded.calls.at(-1)?.commandArguments).toEqual(['rm', '--force', 'shop-ab12-image'])
  })

  it('refuses to start anything when the wrapper described no services', async () => {
    const recorded: Machine = machine({ environment: { DATABASE_URL: 'postgres://127.0.0.1:5432/shop' } })
    const verdict: BootVerdict = await bootImage(REQUEST, recorded.runtime)
    expect(verdict.ok).toBe(false)
    expect(verdict.findings[0]).toContain('start them with withServices from @airness/node/services')
    expect(recorded.calls).toEqual([])
  })
})

describe('a boot request', () => {
  it('is read from the JSON the gate passes, and refused when a field is missing or malformed', () => {
    expect(parseBootRequest(JSON.stringify(REQUEST))).toEqual(REQUEST)
    expect(parseBootRequest(undefined)).toBeUndefined()
    expect(parseBootRequest(JSON.stringify({ ...REQUEST, containerPort: '3000' }))).toBeUndefined()
    expect(parseBootRequest(JSON.stringify({ ...REQUEST, serverUrl: 'not a url' }))).toBeUndefined()
    expect(parseBootRequest(JSON.stringify({ ...REQUEST, probes: [1] }))).toBeUndefined()
  })
})

describe('one request to a served application', PROCESS_TIME, () => {
  const server: Server = createServer((request, response): void => {
    if (request.url === '/moved') {
      response.writeHead(302, { location: '/elsewhere' })
    } else {
      response.writeHead(request.url === '/broken' ? 500 : 200)
    }
    response.end('body')
  })
  const origin = (): string => {
    const address: AddressInfo | string | null = server.address()
    return `http://127.0.0.1:${String(typeof address === 'object' && address !== null ? address.port : 0)}`
  }

  beforeAll(async () => {
    await new Promise<void>((resolve): void => {
      server.listen(0, '127.0.0.1', resolve)
    })
  })

  afterAll(async () => {
    await new Promise<void>((resolve): void => {
      server.close((): void => {
        resolve()
      })
    })
  })

  it('reads the status, without following a redirect', async () => {
    expect(await probeOnce(`${origin()}/`)).toEqual({ status: 200 })
    expect(await probeOnce(`${origin()}/broken`)).toEqual({ status: 500 })
    expect(await probeOnce(`${origin()}/moved`)).toEqual({ status: 302 })
  })

  it('names the error of a connection nothing accepts', async () => {
    const closed: Server = createServer()
    await new Promise<void>((resolve): void => {
      closed.listen(0, '127.0.0.1', resolve)
    })
    const address: AddressInfo | string | null = closed.address()
    const port: number = typeof address === 'object' && address !== null ? address.port : 0
    await new Promise<void>((resolve): void => {
      closed.close((): void => {
        resolve()
      })
    })
    expect(await probeOnce(`http://127.0.0.1:${String(port)}/`)).toEqual({ problem: 'ECONNREFUSED' })
  })
})
