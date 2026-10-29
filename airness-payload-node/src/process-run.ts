// Running a Node.js tool as a child process for a Payload gate: the Payload CLI, and the probe that imports a
// project's built configuration. Every tool runs through the Node that runs the gate, which is the Node the
// harness pinned, so no gate depends on what else is installed on the machine.
import { type SpawnSyncReturns, spawnSync } from 'node:child_process'
import path from 'node:path'

/** Where and how a tool runs. */
export interface RunOptions {
  readonly cwd: string
  /** Variables added to the gate's own environment. */
  readonly env?: Readonly<Record<string, string>>
  /** Kill the tool after this many milliseconds, so a hung import ends as a verdict. */
  readonly timeoutMs?: number
}

/** The raw outcome of a child process. */
export interface RunResult {
  readonly code: number
  /** Both streams together, which is what a finding shows the user. */
  readonly output: string
  /** Standard output alone, for a caller that parses it: pnpm and Node write warnings to stderr. */
  readonly stdout: string
}

const BYTES_PER_KIB: number = 1024
const KIB_PER_MIB: number = 1024
// Large enough that no tool's output is truncated before it can be reported.
const MAX_OUTPUT_MIB: number = 64
const MAX_OUTPUT_BYTES: number = MAX_OUTPUT_MIB * KIB_PER_MIB * BYTES_PER_KIB

/** The exit status reported for a tool killed for exceeding its deadline. */
const TIMED_OUT_CODE: number = 124
/** The exit status reported for a tool that could not start. */
const NOT_STARTED_CODE: number = 127

const startupFailure = (error: Error): RunResult =>
  error.message.includes('ETIMEDOUT')
    ? { code: TIMED_OUT_CODE, output: `the tool did not finish in time and was stopped: ${error.message}`, stdout: '' }
    : { code: NOT_STARTED_CODE, output: `the tool could not run: ${error.message}`, stdout: '' }

// A process killed by a signal reports no status, and reading that as an ordinary failure would hide the
// one fact that explains it.
const signalNote = (signal: NodeJS.Signals | null): string =>
  signal === null ? '' : `\nthe process was killed by ${signal}`

// The project's own binaries first, so a tool the child starts through PATH is the project's copy.
const environmentFor = (options: RunOptions): Readonly<Record<string, string | undefined>> => ({
  ...process.env,
  ...options.env,
  PATH: [path.join(options.cwd, 'node_modules', '.bin'), process.env['PATH'] ?? ''].join(path.delimiter),
})

/**
 * Run a JavaScript entry point on the Node that runs this gate.
 * @param script the absolute path of the entry point.
 * @param scriptArguments the arguments after it.
 * @param options where and how it runs.
 * @returns its exit status and output.
 */
export const runNode = (script: string, scriptArguments: readonly string[], options: RunOptions): RunResult => {
  const result: SpawnSyncReturns<string> = spawnSync(process.execPath, [script, ...scriptArguments], {
    cwd: options.cwd,
    encoding: 'utf8',
    maxBuffer: MAX_OUTPUT_BYTES,
    env: environmentFor(options),
    ...(options.timeoutMs !== undefined && { timeout: options.timeoutMs }),
  })
  if (result.error !== undefined) {
    return startupFailure(result.error)
  }
  const stdout: string = result.stdout
  return {
    code: result.status ?? 1,
    output: `${stdout}${result.stderr}${signalNote(result.signal)}`,
    stdout,
  }
}
