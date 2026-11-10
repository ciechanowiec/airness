import { describe, expect, it } from 'vitest'
import { type RunResult, runStreaming, SYSTEM_STOPS } from '@/exec.js'
import {
  type ForwardedStops,
  forwardStops,
  type ProcessRow,
  readProcessTable,
  STOP_SIGNALS,
  type StopWiring,
  subscribeToStops,
} from '@/process-tree.js'
import { PROCESS_TIME } from './helpers/process-time.js'

const row = (pid: number, ppid: number, isZombie: boolean = false, pgid: number = 1): ProcessRow => ({
  pid,
  ppid,
  pgid,
  isZombie,
})

/** What stopping a tree does to the machine. */
type TreeMachine = StopWiring['machine']

// How long a stopped tree's processes may take, and how often the tree of a running command is looked at.
const GRACE_MS: number = 10_000
const WATCH_MS: number = 2000

// 10 runs 11 and 12; 11 runs 13; 20 is not under 10.
const TABLE: readonly ProcessRow[] = [row(1, 0), row(10, 1), row(11, 10), row(12, 10), row(13, 11), row(20, 1)]

/* eslint-disable functional/immutable-data -- the stand-in machine records what was sent and moves its clock, which is what the tests read */
interface Machine {
  readonly machine: TreeMachine
  readonly sent: (readonly [number, NodeJS.Signals])[]
}

// A machine on which each process in `ending` ends as soon as it is sent a stop signal, and every other
// process ignores everything but SIGKILL. The table may change between readings.
const machineOf = (
  table: readonly ProcessRow[] | (() => readonly ProcessRow[]),
  ending: readonly number[],
): Machine => {
  const rows = (): readonly ProcessRow[] => (typeof table === 'function' ? table() : table)
  const sent: (readonly [number, NodeJS.Signals])[] = []
  const clock: { now: number } = { now: 0 }
  const isGone = (pid: number): boolean =>
    sent.some(
      ([target, signal]: readonly [number, NodeJS.Signals]): boolean =>
        target === pid && (signal === 'SIGKILL' || ending.includes(pid)),
    )
  return {
    sent,
    machine: {
      table: (): readonly ProcessRow[] => rows().filter((candidate: ProcessRow): boolean => !isGone(candidate.pid)),
      signal: (pid: number, signal: NodeJS.Signals): void => {
        sent.push([pid, signal])
      },
      pause: async (milliseconds: number): Promise<void> => {
        clock.now += milliseconds
        await Promise.resolve()
      },
      now: (): number => clock.now,
    },
  }
}
/* eslint-enable functional/immutable-data -- the tests below only read what the stand-in recorded */

describe('the process table', () => {
  it('reads each process with its parent and whether it only waits to be collected', () => {
    const stdout: string = '    1     0     1 Ss\n  10     1    10 S+\n  11    10    10 Z\n\nPID PPID PGID STAT\n'
    expect(readProcessTable(() => ({ code: 0, stdout }))).toEqual([
      row(1, 0),
      row(10, 1, false, 10),
      row(11, 10, true, 10),
    ])
  })

  it('is empty when ps cannot be run', () => {
    expect(readProcessTable(() => ({ code: 1, output: 'ps: not found', stdout: '' }))).toEqual([])
  })

  it('is read from ps on this machine, with this process in it', () => {
    const rows: readonly ProcessRow[] = SYSTEM_STOPS.machine.table()
    expect(rows.find((candidate: ProcessRow): boolean => candidate.pid === process.pid)?.ppid).toBe(process.ppid)
  })
})

const ignoreStop = (): void => undefined

describe('the stop signals of this process', () => {
  it('are Ctrl-C, a request to terminate and the end of the terminal', () => {
    expect(STOP_SIGNALS).toEqual(['SIGINT', 'SIGTERM', 'SIGHUP'])
  })

  it('reach a subscribed handler on every one of them until it unsubscribes', () => {
    const subscribed = (): readonly boolean[] =>
      STOP_SIGNALS.map((signal: NodeJS.Signals): boolean => process.listeners(signal).includes(ignoreStop))
    const unsubscribe: () => void = subscribeToStops(ignoreStop)
    expect(subscribed()).toEqual([true, true, true])
    unsubscribe()
    expect(subscribed()).toEqual([false, false, false])
  })

  it('are what a streamed command forwards', () => {
    expect(SYSTEM_STOPS.subscribe).toBe(subscribeToStops)
  })
})

/* eslint-disable functional/immutable-data -- the stand-in wiring records the handler it was given and what it raised */
interface Wiring {
  readonly wiring: StopWiring
  readonly handlers: ((signal: NodeJS.Signals) => void)[]
  readonly raised: NodeJS.Signals[]
  readonly reported: string[]
  readonly ticks: (() => void)[]
}

const wiringOf = (machine: TreeMachine): Wiring => {
  const handlers: ((signal: NodeJS.Signals) => void)[] = []
  const raised: NodeJS.Signals[] = []
  const reported: string[] = []
  const ticks: (() => void)[] = []
  return {
    handlers,
    raised,
    reported,
    ticks,
    wiring: {
      machine,
      subscribe: (handler: (signal: NodeJS.Signals) => void): (() => void) => {
        handlers.push(handler)
        return (): void => {
          handlers.splice(handlers.indexOf(handler), 1)
        }
      },
      raise: (signal: NodeJS.Signals): void => {
        raised.push(signal)
      },
      report: (line: string): void => {
        reported.push(line)
      },
      watch: (tick: () => void, periodMs: number): (() => void) => {
        expect(periodMs).toBe(WATCH_MS)
        ticks.push(tick)
        return (): void => {
          ticks.splice(ticks.indexOf(tick), 1)
        }
      },
    },
  }
}
/* eslint-enable functional/immutable-data -- the tests below only read what the stand-in recorded */

const send = (wiring: Wiring, signal: NodeJS.Signals): void => {
  for (const handler of wiring.handlers) {
    handler(signal)
  }
}

describe('forwarding the stops of a running command', () => {
  it('stops the tree once, however often the signal comes, and then ends this process with it', async () => {
    const { machine, sent }: Machine = machineOf(TABLE, [10, 11, 12, 13])
    const wiring: Wiring = wiringOf(machine)
    const stops: ForwardedStops = forwardStops(10, wiring.wiring)
    expect(stops.stopping()).toBeUndefined()
    send(wiring, 'SIGINT')
    send(wiring, 'SIGINT')
    await stops.stopping()
    // Every process under the root, parents before their children, and nothing beside them.
    expect(sent).toEqual([
      [10, 'SIGINT'],
      [11, 'SIGINT'],
      [12, 'SIGINT'],
      [13, 'SIGINT'],
    ])
    expect(wiring.raised).toEqual(['SIGINT'])
    expect(wiring.handlers).toEqual([])
    expect(wiring.reported).toEqual([])
  })

  it('stops only the process itself when nothing runs under it', async () => {
    const { machine, sent }: Machine = machineOf(TABLE, [13])
    const wiring: Wiring = wiringOf(machine)
    const stops: ForwardedStops = forwardStops(13, wiring.wiring)
    send(wiring, 'SIGINT')
    await stops.stopping()
    expect(sent).toEqual([[13, 'SIGINT']])
  })

  it('kills what still runs when the grace period is over, after waiting for it, and names it', async () => {
    const { machine, sent }: Machine = machineOf(TABLE, [10, 11])
    const wiring: Wiring = wiringOf(machine)
    const started: number = machine.now()
    const stops: ForwardedStops = forwardStops(10, wiring.wiring)
    send(wiring, 'SIGTERM')
    await stops.stopping()
    expect(machine.now() - started).toBeGreaterThanOrEqual(GRACE_MS)
    expect(sent.slice(-2)).toEqual([
      [12, 'SIGKILL'],
      [13, 'SIGKILL'],
    ])
    expect(wiring.reported).toEqual(['processes 12, 13 were still running 10s after SIGTERM and were ended outright'])
  })

  it('counts a process that only waits to be collected as ended', async () => {
    const { machine, sent }: Machine = machineOf([row(10, 1), row(11, 10, true)], [10])
    const wiring: Wiring = wiringOf(machine)
    const stops: ForwardedStops = forwardStops(10, wiring.wiring)
    send(wiring, 'SIGINT')
    await stops.stopping()
    expect(sent).toEqual([
      [10, 'SIGINT'],
      [11, 'SIGINT'],
    ])
    expect(machine.now()).toBe(0)
    expect(wiring.reported).toEqual([])
  })

  it('stops what it saw under the command, though it was left to the system before the stop', async () => {
    const table: { rows: readonly ProcessRow[] } = {
      rows: [...TABLE, row(15, 10)].map((entry: ProcessRow) => ({ ...entry, pgid: 10 })),
    }
    const { machine, sent }: Machine = machineOf((): readonly ProcessRow[] => table.rows, [10, 11, 12, 13, 14])
    const wiring: Wiring = wiringOf(machine)
    const stops: ForwardedStops = forwardStops(10, wiring.wiring)
    for (const tick of wiring.ticks) {
      tick()
    }
    // 11 ended by itself and 13, which ran under it, was left to the system, still in its group, and now runs
    // 14. 15 ended, and its number was given to an unrelated process in a group of its own, which is left alone.
    // eslint-disable-next-line functional/immutable-data -- the table changes as the machine's processes do
    table.rows = [
      row(10, 1, false, 10),
      row(12, 10, false, 10),
      row(13, 1, false, 10),
      row(14, 13, false, 10),
      row(15, 1, false, 21),
    ]
    send(wiring, 'SIGTERM')
    await stops.stopping()
    expect(sent).toEqual([
      [10, 'SIGTERM'],
      [12, 'SIGTERM'],
      [13, 'SIGTERM'],
      [14, 'SIGTERM'],
    ])
    expect(wiring.ticks).toEqual([])
  })

  it('stops listening and watching once the command ended by itself', () => {
    const wiring: Wiring = wiringOf(machineOf(TABLE, []).machine)
    forwardStops(10, wiring.wiring).release()
    expect(wiring.handlers).toEqual([])
    expect(wiring.ticks).toEqual([])
  })
})

const waitFor = async (found: () => number | undefined): Promise<number> => {
  const deadline: number = performance.now() + 10_000
  while (found() === undefined && performance.now() < deadline) {
    await new Promise<void>((resolve): void => {
      setTimeout(resolve, 50)
    })
  }
  return found() ?? 0
}

describe('a streamed command that is stopped', PROCESS_TIME, () => {
  it('passes the stop to a process it started in a group of its own, and waits for it', async () => {
    const lines: string[] = []
    const wiring: Wiring = wiringOf(SYSTEM_STOPS.machine)
    // The command starts a sleeper in a process group of its own, as a package manager starts a script, and
    // says which process that is.
    const finished: Promise<RunResult> = runStreaming(
      process.execPath,
      [
        '-e',
        [
          "const { spawn } = require('node:child_process')",
          "const sleeper = spawn(process.execPath, ['-e', 'setInterval(() => {}, 1000)'], { detached: true, stdio: 'ignore' })",
          'console.log("sleeper " + sleeper.pid)',
          'setInterval(() => {}, 1000)',
        ].join('\n'),
      ],
      {
        cwd: process.cwd(),
        onLine: (line: string): void => {
          // eslint-disable-next-line functional/immutable-data -- the test reads the lines the command wrote
          lines.push(line)
        },
      },
      wiring.wiring,
    )
    const sleeper: number = await waitFor((): number | undefined => {
      const said: string | undefined = lines.find((line: string): boolean => line.startsWith('sleeper '))
      return said === undefined ? undefined : Number(said.slice('sleeper '.length))
    })
    send(wiring, 'SIGTERM')
    const result: RunResult = await finished
    expect(result.output).toContain('the process was killed by SIGTERM')
    expect(
      SYSTEM_STOPS.machine.table().some((entry: ProcessRow): boolean => entry.pid === sleeper && !entry.isZombie),
    ).toBe(false)
    expect(wiring.raised).toEqual(['SIGTERM'])
  })

  it('passes the stop to a process it saw under the command before that process was left to the system', async () => {
    const lines: string[] = []
    const stand: Wiring = wiringOf(SYSTEM_STOPS.machine)
    // The command starts a process that starts a sleeper and ends a few seconds later without stopping it,
    // as a launcher without a handler of its own ends on Ctrl-C, so the sleeper is left to the system.
    const sleeperScript: string = 'setInterval(() => {}, 1000)'
    const middleScript: string = [
      "const { spawn } = require('node:child_process')",
      `const sleeper = spawn(process.execPath, ['-e', ${JSON.stringify(sleeperScript)}], { stdio: 'ignore' })`,
      'console.log("sleeper " + sleeper.pid)',
      `setTimeout(() => process.exit(0), ${String(WATCH_MS + 1000)})`,
    ].join('\n')
    const finished: Promise<RunResult> = runStreaming(
      process.execPath,
      [
        '-e',
        [
          "const { spawn } = require('node:child_process')",
          `spawn(process.execPath, ['-e', ${JSON.stringify(middleScript)}], { stdio: 'inherit' })`,
          'setInterval(() => {}, 1000)',
        ].join('\n'),
      ],
      {
        cwd: process.cwd(),
        onLine: (line: string): void => {
          // eslint-disable-next-line functional/immutable-data -- the test reads the lines the command wrote
          lines.push(line)
        },
      },
      { ...stand.wiring, watch: SYSTEM_STOPS.watch },
    )
    const sleeper: number = await waitFor((): number | undefined => {
      const said: string | undefined = lines.find((line: string): boolean => line.startsWith('sleeper '))
      return said === undefined ? undefined : Number(said.slice('sleeper '.length))
    })
    try {
      await waitFor(
        (): number | undefined =>
          SYSTEM_STOPS.machine.table().find((entry: ProcessRow): boolean => entry.pid === sleeper && entry.ppid === 1)
            ?.pid,
      )
      send(stand, 'SIGTERM')
      await finished
      expect(
        SYSTEM_STOPS.machine.table().some((entry: ProcessRow): boolean => entry.pid === sleeper && !entry.isZombie),
      ).toBe(false)
    } finally {
      SYSTEM_STOPS.machine.signal(sleeper, 'SIGKILL')
    }
  })
})
