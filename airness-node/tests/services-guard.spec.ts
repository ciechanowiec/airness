import { existsSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import type { RunResult } from '@/exec.js'
import { startGuard } from '@/services.js'
import { type GuardMachine, guardArguments, guardRun, isRunning, removeByPrefix } from '@/services-guard.js'
import { PROCESS_TIME } from './helpers/process-time.js'

const answered = (stdout: string = '', code: number = 0): RunResult => ({ code, output: stdout, stdout })

/* eslint-disable functional/immutable-data -- the stand-in machine records the docker calls and pauses the guard makes, which is what the tests read */
interface Machine {
  readonly machine: GuardMachine
  readonly docker: (readonly string[])[]
  readonly pauses: number[]
}

// A machine whose wrapper answers alive `alive` times, on which `listing` is what Docker lists and every
// removal answers with `removal`.
const machineOf = (alive: number, listing: (call: readonly string[]) => RunResult, removal?: RunResult): Machine => {
  const docker: (readonly string[])[] = []
  const pauses: number[] = []
  const left: { count: number } = { count: alive }
  return {
    docker,
    pauses,
    machine: {
      isAlive: (): boolean => {
        left.count -= 1
        return left.count >= 0
      },
      docker: (commandArguments: readonly string[]): RunResult => {
        docker.push(commandArguments)
        return commandArguments[0] === 'ps' || commandArguments[1] === 'ls'
          ? listing(commandArguments)
          : (removal ?? answered())
      },
      pause: async (milliseconds: number): Promise<void> => {
        pauses.push(milliseconds)
        await Promise.resolve()
      },
    },
  }
}
/* eslint-enable functional/immutable-data -- the tests below only read what the stand-in recorded */

const RUN_LISTING = (call: readonly string[]): RunResult => answered(call[0] === 'ps' ? 'c0ffee\nbeef00\n' : 'net123\n')

describe('the removal of what a run started', () => {
  it('removes the containers before the networks they are attached to', () => {
    const { machine, docker }: Machine = machineOf(0, RUN_LISTING)
    expect(removeByPrefix(machine.docker, 'teron-0a1b')).toEqual([])
    expect(docker).toEqual([
      ['ps', '--all', '--quiet', '--filter=name=teron-0a1b-'],
      ['network', 'ls', '--quiet', '--filter=name=teron-0a1b'],
      ['rm', '--force', 'c0ffee', 'beef00'],
      ['network', 'rm', 'net123'],
    ])
  })

  it('removes nothing when nothing carries the prefix, or nothing could be listed', () => {
    const empty: Machine = machineOf(0, () => answered())
    expect(removeByPrefix(empty.machine.docker, 'teron-0a1b')).toEqual([])
    const unlisted: Machine = machineOf(0, () => answered('c0ffee', 1))
    expect(removeByPrefix(unlisted.machine.docker, 'teron-0a1b')).toEqual([])
    expect([...empty.docker, ...unlisted.docker].filter((call) => call.includes('rm'))).toEqual([])
  })

  it('names each removal Docker refused, with its answer', () => {
    const { machine }: Machine = machineOf(0, RUN_LISTING, answered('in use', 1))
    expect(removeByPrefix(machine.docker, 'teron-0a1b')).toEqual([
      'docker rm --force c0ffee beef00 failed: in use',
      'docker network rm net123 failed: in use',
    ])
  })
})

describe('a running process', () => {
  it('is this one', () => {
    expect(isRunning(process.pid)).toBe(true)
  })

  it('is one of another user, which refuses the signal', () => {
    expect(
      isRunning(1, () => {
        throw Object.assign(new Error('operation not permitted'), { code: 'EPERM' })
      }),
    ).toBe(true)
  })

  it('is not one that no longer exists, nor one the probe cannot reason about', () => {
    expect(
      isRunning(4321, () => {
        throw Object.assign(new Error('no such process'), { code: 'ESRCH' })
      }),
    ).toBe(false)
    expect(
      isRunning(4321, () => {
        throw Object.assign(new Error('invalid argument'), { code: 'EINVAL' })
      }),
    ).toBe(false)
  })
})

// How often a guard asks after the wrapper, and how long it waits before its second sweep.
const POLL_MS: number = 500
const SETTLE_MS: number = 2000

describe('a guard', () => {
  it('waits while the wrapper runs, then sweeps twice, the second time after a pause', async () => {
    const { machine, docker, pauses }: Machine = machineOf(3, RUN_LISTING)
    expect(await guardRun(machine, 'teron-0a1b')).toEqual([])
    expect(pauses).toEqual([POLL_MS, POLL_MS, POLL_MS, SETTLE_MS])
    expect(docker.filter((call: readonly string[]): boolean => call[0] === 'rm')).toHaveLength(2)
  })

  it('answers with the removals that failed in either sweep', async () => {
    const { machine }: Machine = machineOf(0, RUN_LISTING, answered('in use', 1))
    expect(await guardRun(machine, 'teron-0a1b')).toHaveLength(4)
  })

  it('is started with the script, the process it watches and the prefix', () => {
    expect(guardArguments('/opt/guard.js', 4321, 'teron-0a1b')).toEqual(['/opt/guard.js', '4321', 'teron-0a1b'])
  })
})

const waitFor = async (file: string): Promise<void> => {
  const deadline: number = performance.now() + 10_000
  while (!existsSync(file) && performance.now() < deadline) {
    await new Promise<void>((resolve): void => {
      setTimeout(resolve, 50)
    })
  }
}

describe('the guard process', PROCESS_TIME, () => {
  it('runs outside the process tree and group of its wrapper, with the process it watches and the prefix', async () => {
    const directory: string = mkdtempSync(path.join(tmpdir(), 'airness-guard-'))
    const script: string = path.join(directory, 'guard.mjs')
    const report: string = path.join(directory, 'report.json')
    // The stand-in guard waits for the shell that started it to leave it behind, and reports what it was given
    // and where it runs.
    writeFileSync(
      script,
      [
        "import { execFileSync } from 'node:child_process'",
        "import { writeFileSync } from 'node:fs'",
        "const field = (name, pid) => execFileSync('ps', ['-o', name + '=', '-p', String(pid)], { encoding: 'utf8' }).trim()",
        "const underShell = () => field('comm', process.ppid).endsWith('sh')",
        'const deadline = Date.now() + 5000',
        'const report = () => {',
        '  if (underShell() && Date.now() < deadline) {',
        '    setTimeout(report, 20)',
        '    return',
        '  }',
        `  writeFileSync(${JSON.stringify(report)}, JSON.stringify({`,
        '    argv: process.argv.slice(2),',
        `    separate: field('pgid', process.pid) !== field('pgid', ${String(process.pid)}),`,
        `    leftBehind: !underShell() && process.ppid !== ${String(process.pid)},`,
        '  }))',
        '}',
        'setTimeout(report, 20)',
      ].join('\n'),
    )
    startGuard('teron-0a1b', { script })
    await waitFor(report)
    const written: unknown = JSON.parse(readFileSync(report, 'utf8'))
    expect(written).toEqual({ argv: [String(process.pid), 'teron-0a1b'], separate: true, leftBehind: true })
  })

  it('says so when it cannot be started, leaving the removal to the wrapper', async () => {
    const complaint: string = await new Promise<string>((resolve): void => {
      startGuard('teron-0a1b', { shell: '/nonexistent/airness-shell', script: 'guard.js', complain: resolve })
    })
    expect(complaint).toContain('the guard of teron-0a1b could not be started')
  })
})
