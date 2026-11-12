// The machine the image gate and the boot under the wrapper run on: Docker, the member's wrapper and the stop
// signals. The decisions they take are measured in gate-image.ts and image-boot.ts against stand-ins; what these
// bindings do is proven by the integration suite against a real daemon.

import { randomBytes } from 'node:crypto'
import { mkdtempSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { streamApp } from './app-process.js'
import { freePorts } from './browser-origin.js'
import { type RunResult, run, runStreaming } from './exec.js'
import { type BootRequest, type BootRuntime, type BootVerdict, probeOnce } from './image-boot.js'
import { asRecord, isArray } from './json-shapes.js'
import { pause, subscribeToStops } from './process-tree.js'
import type { Member } from './project.js'
import { IMAGE_VERIFICATION_ON, IMAGE_VERIFICATION_VARIABLE } from './services-description.js'

/** What one boot under the wrapper came back with. */
export interface BootOutcome {
  /** The verdict the boot wrote, undefined when it never wrote one because the wrapper did not complete it. */
  readonly verdict: BootVerdict | undefined
  readonly result: RunResult
}

/** What the gate does to the machine, replaceable so its decisions can be measured without Docker. */
export interface ImageMachine {
  /** A tag of this run's own for the image of one Dockerfile. */
  readonly tag: () => string
  /** Build the image of one Dockerfile from the member's directory. */
  readonly build: (member: Member, dockerfile: string, tag: string) => Promise<RunResult>
  /** What `docker image inspect` prints for the image's exposed ports, as JSON. */
  readonly exposedPorts: (tag: string) => RunResult
  readonly remove: (tag: string) => void
  /** Boot the image inside the member's wrapper, or inside an empty services run of the harness's own. */
  readonly boot: (member: Member, request: BootRequest) => Promise<BootOutcome>
  /**
   * Run the body while holding every stop signal back until it is done, so the images it built are removed before
   * the process ends with the signal; the body is told once one arrived, so it starts nothing further.
   */
  readonly holdStops: <T>(body: (isStopped: () => boolean) => Promise<T>) => Promise<T>
}

const TAG_BYTES: number = 6
const PREFIX: string = '[image]'

// The harness's own entry points, each named by its literal specifier, which the dead-code gate follows.
const bootScript = (): string => fileURLToPath(import.meta.resolve('#harness/image-boot-main'))
const emptyServicesScript = (): string => fileURLToPath(import.meta.resolve('#harness/image-services-main'))

// The member as its boot runs: under its own wrapper, or, when it declares none, under a services run of the
// harness's own that starts nothing, so the container is named, networked and removed the same way.
const bootMember = (member: Member): Member =>
  member.settings.testWrapper.length > 0
    ? member
    : {
        ...member,
        settings: { ...member.settings, testWrapper: [process.execPath, emptyServicesScript()] },
      }

const verdictAt = (file: string): BootVerdict | undefined => {
  try {
    const record: Record<string, unknown> = asRecord(JSON.parse(readFileSync(file, 'utf8')))
    const { ok, findings } = record
    return typeof ok === 'boolean' && isArray(findings) && findings.every((line) => typeof line === 'string')
      ? { ok, findings }
      : undefined
  } catch {
    return undefined
  }
}

// Each line Docker and the wrapper print as they work, without the blank ones a plain build progress separates its
// steps with.
const report = (line: string): void => {
  if (line.trim().length > 0) {
    console.info(`${PREFIX} ${line}`)
  }
}

/** The machine itself. */
export const SYSTEM_IMAGE_MACHINE: ImageMachine = {
  tag: (): string => `airness-image-${randomBytes(TAG_BYTES).toString('hex')}:verification`,
  build: async (member: Member, dockerfile: string, tag: string): Promise<RunResult> =>
    await runStreaming('docker', ['build', '--progress=plain', '--file', dockerfile, '--tag', tag, '.'], {
      cwd: member.root,
      onLine: report,
    }),
  exposedPorts: (tag: string): RunResult =>
    run('docker', ['image', 'inspect', '--format', '{{json .Config.ExposedPorts}}', tag], { cwd: process.cwd() }),
  remove: (tag: string): void => {
    const removed: RunResult = run('docker', ['image', 'rm', '--force', tag], { cwd: process.cwd() })
    if (removed.code !== 0) {
      console.error(`${PREFIX} the image ${tag} could not be removed: ${removed.output.trim()}`)
    }
  },
  boot: async (member: Member, request: BootRequest): Promise<BootOutcome> => {
    const directory: string = mkdtempSync(path.join(tmpdir(), 'airness-image-'))
    try {
      const verdictFile: string = path.join(directory, 'verdict.json')
      const result: RunResult = await streamApp(
        bootMember(member),
        [bootScript(), verdictFile, JSON.stringify(request)],
        { cwd: member.root, env: { [IMAGE_VERIFICATION_VARIABLE]: IMAGE_VERIFICATION_ON }, onLine: report },
      )
      return { verdict: verdictAt(verdictFile), result }
    } finally {
      rmSync(directory, { recursive: true, force: true })
    }
  },
  holdStops: async <T>(body: (isStopped: () => boolean) => Promise<T>): Promise<T> => {
    const received: NodeJS.Signals[] = []
    const unsubscribe: () => void = subscribeToStops((signal: NodeJS.Signals): void => {
      // eslint-disable-next-line functional/immutable-data -- the first stop is kept until the images are removed
      received.push(signal)
    })
    try {
      return await body((): boolean => received.length > 0)
    } finally {
      unsubscribe()
      const [signal] = received
      if (signal !== undefined) {
        process.kill(process.pid, signal)
      }
    }
  },
}

/** The machine a boot under the wrapper runs on. */
export const SYSTEM_BOOT_RUNTIME: BootRuntime = {
  docker: (commandArguments: readonly string[], environment: Readonly<Record<string, string>> = {}): RunResult =>
    run('docker', commandArguments, { cwd: process.cwd(), env: environment }),
  probe: probeOnce,
  freePort: async (): Promise<number> => {
    const [port = 0] = await freePorts(1)
    return port
  },
  pause,
  now: (): number => performance.now(),
  environment: process.env,
}
