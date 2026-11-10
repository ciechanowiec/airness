// What removes a run's services when the wrapper that started them cannot. A wrapper removes its containers
// itself once its command exits, also when it is sent SIGINT, SIGTERM or SIGHUP, but nothing in a process
// survives SIGKILL, and a stop does not always arrive as a signal the wrapper hears: a package manager that
// runs the wrapper as a script places it in a process group of its own, which Ctrl-C in the terminal does not
// reach, and it ends that group outright when it is stopped itself. So each run is also watched from outside.
// A guard, a process of its own in a session of its own that writes nowhere, waits for the wrapper to be gone,
// however it went, and then removes whatever carries the run's prefix. The next run's removal of the
// containers of runs that are gone stays as the last resort, for a machine that went down with both.
import type { RunResult } from './exec.js'

/** How often the guard asks whether the wrapper is still there. */
const GUARD_INTERVAL_MS: number = 500

/**
 * How long the guard waits before its second sweep: a `docker run` the wrapper had sent when it went can still
 * create its container after the first one.
 */
const GUARD_SETTLE_MS: number = 2000

/** What a guard does to the machine, replaceable so its decisions can be measured without Docker. */
export interface GuardMachine {
  /** Whether the wrapper the guard watches is still running. */
  readonly isAlive: () => boolean
  readonly docker: (commandArguments: readonly string[]) => RunResult
  readonly pause: (milliseconds: number) => Promise<void>
}

const listed = (result: RunResult): readonly string[] =>
  result.code === 0
    ? result.stdout
        .split('\n')
        .map((line: string): string => line.trim())
        .filter((line: string): boolean => line.length > 0)
    : []

/**
 * Remove every container and network whose name carries a run's prefix: the containers first, since a network
 * cannot be removed while one is attached to it.
 * @param docker how `docker` runs.
 * @param prefix the run's prefix.
 * @returns the removals that failed, each as the command and what Docker answered.
 */
export const removeByPrefix = (
  docker: (commandArguments: readonly string[]) => RunResult,
  prefix: string,
): readonly string[] => {
  const containers: readonly string[] = listed(docker(['ps', '--all', '--quiet', `--filter=name=${prefix}-`]))
  const networks: readonly string[] = listed(docker(['network', 'ls', '--quiet', `--filter=name=${prefix}`]))
  const removals: readonly (readonly string[])[] = [
    ...(containers.length > 0 ? [['rm', '--force', ...containers]] : []),
    ...networks.map((network: string): readonly string[] => ['network', 'rm', network]),
  ]
  return removals.flatMap((removal: readonly string[]): readonly string[] => {
    const result: RunResult = docker(removal)
    return result.code === 0 ? [] : [`docker ${removal.join(' ')} failed: ${result.output.trim()}`]
  })
}

/**
 * Whether a process is running, asked with signal 0, which delivers nothing. A process of another user answers
 * EPERM, and is running all the same.
 * @param pid the process.
 * @param probe how the signal is sent, replaced only by tests.
 * @returns false once no process has that number.
 */
export const isRunning = (pid: number, probe: (pid: number) => void = (target) => process.kill(target, 0)): boolean => {
  try {
    probe(pid)
    return true
  } catch (error: unknown) {
    return error instanceof Error && 'code' in error && error.code === 'EPERM'
  }
}

/**
 * Wait for the wrapper to be gone, then remove what its run started, twice, the second time after a pause.
 * @param machine the machine.
 * @param prefix the run's prefix.
 * @returns the removals that failed.
 */
export const guardRun = async (machine: GuardMachine, prefix: string): Promise<readonly string[]> => {
  while (machine.isAlive()) {
    await machine.pause(GUARD_INTERVAL_MS)
  }
  const first: readonly string[] = removeByPrefix(machine.docker, prefix)
  await machine.pause(GUARD_SETTLE_MS)
  return [...first, ...removeByPrefix(machine.docker, prefix)]
}

/**
 * The arguments that start a guard for a run, after the Node executable.
 * @param script the guard's entry point.
 * @param pid the wrapper the guard watches.
 * @param prefix the run's prefix.
 * @returns the arguments.
 */
export const guardArguments = (script: string, pid: number, prefix: string): readonly string[] => [
  script,
  String(pid),
  prefix,
]
