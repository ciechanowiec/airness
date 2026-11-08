// How a gate stops a command that started commands of its own. Ctrl-C in a terminal reaches only the
// foreground process group, and a package manager runs each script in a group of its own, so what a browser
// suite starts through a test wrapper is not reached by it: the package manager ends, and the wrapper, the
// test runner and its workers are left without a signal, ended outright with their group or running on. So a
// gate that is asked to stop sends the signal to every process under the command itself, waits for them to
// finish what their own handlers do, such as removing the containers a run started, and ends whatever is
// still running when the grace period is over. A process that leaves the tree on purpose, as a run's services
// guard does, is not under the command and is left alone.
//
// The tree is not only what is under the command when the stop arrives. The package manager that runs the
// gate passes the same Ctrl-C to the package manager under it, which passes it down at once, and a process
// in between that ends without waiting leaves the test runner, its workers and the application server to
// the system before the gate has read the table. So while the command runs, the gate looks at its tree every
// few seconds and remembers each process it saw there with its process group, which a process keeps when it
// is left to the system, and a stop also reaches every remembered process that still runs in the same group,
// and what runs under it.

/** What running `ps` answered, as much of it as the table is read from. */
export interface ProcessListing {
  readonly code: number
  readonly stdout: string
}

/** One process of the machine's process table. */
export interface ProcessRow {
  readonly pid: number
  readonly ppid: number
  /** The process group, which stays the same when the process is left to the system. */
  readonly pgid: number
  /** Whether it has exited and only waits for its parent to collect it. */
  readonly isZombie: boolean
}

/** What stopping a tree does to the machine, replaceable so its decisions can be measured without processes. */
export interface TreeMachine {
  readonly table: () => readonly ProcessRow[]
  /** Send a signal, where a process that is already gone is no failure. */
  readonly signal: (pid: number, signal: NodeJS.Signals) => void
  readonly pause: (milliseconds: number) => Promise<void>
  readonly now: () => number
}

/** How long the processes of a stopped tree may take to finish what they do on a stop signal. */
export const TREE_GRACE_MS: number = 10_000

/** How often a stopping tree is looked at. */
export const TREE_POLL_MS: number = 100

/** How often the tree of a running command is looked at, so that what leaves it before a stop is remembered. */
export const TREE_WATCH_MS: number = 2000

const ROW_FIELDS: number = 4
const MILLISECONDS_PER_SECOND: number = 1000

/**
 * Read the table `ps -A -o pid=,ppid=,pgid=,stat=` prints.
 * @param text what it printed.
 * @returns a row for each process.
 */
export const parseProcessTable = (text: string): readonly ProcessRow[] =>
  text.split('\n').flatMap((line: string): readonly ProcessRow[] => {
    const fields: readonly string[] = line.trim().split(/\s+/u)
    const [pid = '', ppid = '', pgid = '', state = '']: readonly (string | undefined)[] = fields
    return fields.length === ROW_FIELDS && [pid, ppid, pgid].every((field: string): boolean => /^\d+$/u.test(field))
      ? [{ pid: Number(pid), ppid: Number(ppid), pgid: Number(pgid), isZombie: state.startsWith('Z') }]
      : []
  })

/**
 * The table of the machine's processes, empty when it cannot be read.
 * @param execute how `ps` runs.
 * @returns a row for each process.
 */
export const readProcessTable = (
  execute: (command: string, commandArguments: readonly string[]) => ProcessListing,
): readonly ProcessRow[] => {
  const result: ProcessListing = execute('ps', ['-A', '-o', 'pid=,ppid=,pgid=,stat='])
  return result.code === 0 ? parseProcessTable(result.stdout) : []
}

/**
 * A process and every process under it, parents before their children.
 * @param root the process.
 * @param rows the process table.
 * @returns the processes, the root first.
 */
export const treeOf = (root: number, rows: readonly ProcessRow[]): readonly number[] => {
  const walk = (parents: readonly number[], found: readonly number[]): readonly number[] => {
    const children: readonly number[] = rows
      .filter((row: ProcessRow): boolean => parents.includes(row.ppid) && !found.includes(row.pid))
      .map((row: ProcessRow): number => row.pid)
    return children.length === 0 ? found : walk(children, [...found, ...children])
  }
  return walk([root], [root])
}

/**
 * Every process a stop reaches: the tree under the root, and each remembered process that still runs in the
 * group it was seen in, with the tree under it. The group tells a remembered process from an unrelated one
 * that was given its number after it ended.
 * @param root the command's process.
 * @param known the processes seen under it before.
 * @param rows the process table.
 * @returns the processes, the root first, each once.
 */
export const reachable = (
  root: number,
  known: readonly ProcessRow[],
  rows: readonly ProcessRow[],
): readonly number[] => {
  const kept: readonly number[] = known
    .filter((seen: ProcessRow): boolean =>
      rows.some((row: ProcessRow): boolean => row.pid === seen.pid && row.pgid === seen.pgid && !row.isZombie),
    )
    .map((seen: ProcessRow): number => seen.pid)
  return [...new Set([root, ...kept].flatMap((start: number): readonly number[] => treeOf(start, rows)))]
}

/** How a tree is stopped, beyond the signal it is sent. */
export interface TreeStop {
  /** How long its processes may take, ten seconds unless said otherwise. */
  readonly graceMs?: number
  /** The processes seen under the root before, which the stop also reaches while they still run. */
  readonly known?: readonly ProcessRow[]
}

const running = (members: readonly number[], rows: readonly ProcessRow[]): readonly number[] =>
  members.filter((pid: number): boolean => rows.some((row: ProcessRow): boolean => row.pid === pid && !row.isZombie))

/**
 * Send a stop signal to a process and every process under it, wait for them to end, and end outright those
 * that are still running when the grace period is over.
 * @param machine the machine.
 * @param root the process the tree starts at.
 * @param signal the stop signal.
 * @param stopping how long they may take and what was seen under the root before.
 * @returns the processes that had to be ended outright.
 */
export const stopTree = async (
  machine: TreeMachine,
  root: number,
  signal: NodeJS.Signals,
  stopping: TreeStop = {},
): Promise<readonly number[]> => {
  const { graceMs = TREE_GRACE_MS, known = [] }: TreeStop = stopping
  const members: readonly number[] = reachable(root, known, machine.table())
  for (const pid of members) {
    machine.signal(pid, signal)
  }
  const deadline: number = machine.now() + graceMs
  const settle = async (): Promise<readonly number[]> => {
    const left: readonly number[] = running(members, machine.table())
    if (left.length === 0 || machine.now() >= deadline) {
      return left
    }
    await machine.pause(TREE_POLL_MS)
    return await settle()
  }
  const survivors: readonly number[] = await settle()
  for (const pid of survivors) {
    machine.signal(pid, 'SIGKILL')
  }
  return survivors
}

/** The signals that ask a run to stop: Ctrl-C, a request to terminate, and the end of its terminal. */
export const STOP_SIGNALS: readonly NodeJS.Signals[] = ['SIGINT', 'SIGTERM', 'SIGHUP']

/**
 * Hand every stop signal this process is sent to one handler.
 * @param handler what each stop signal runs.
 * @returns the function that unsubscribes the handler again.
 */
export const subscribeToStops = (handler: (signal: NodeJS.Signals) => void): (() => void) => {
  for (const signal of STOP_SIGNALS) {
    process.on(signal, handler)
  }
  return (): void => {
    for (const signal of STOP_SIGNALS) {
      process.off(signal, handler)
    }
  }
}

/**
 * Wait without blocking the process.
 * @param milliseconds how long.
 */
export const pause = async (milliseconds: number): Promise<void> => {
  await new Promise<void>((resolve): void => {
    setTimeout(resolve, milliseconds)
  })
}

/** How a process hears its stop signals and ends with one, replaceable so the forwarding can be measured. */
export interface StopWiring {
  readonly machine: TreeMachine
  /** Subscribe to the stop signals; the returned function unsubscribes. */
  readonly subscribe: (handler: (signal: NodeJS.Signals) => void) => () => void
  /** End this process with the signal it was sent, once nothing of the tree runs any more. */
  readonly raise: (signal: NodeJS.Signals) => void
  readonly report: (line: string) => void
  /** Call the tick again and again, a period apart, until the returned function is called. */
  readonly watch: (tick: () => void, periodMs: number) => () => void
}

/** The stops a running command's tree is sent. */
export interface ForwardedStops {
  /** The stop under way, once a stop signal arrived. */
  readonly stopping: () => Promise<void> | undefined
  /** Stop listening, once the command ended by itself. */
  readonly release: () => void
}

/**
 * While a command runs, remember the processes of its tree and pass each stop signal this process is sent on
 * to that whole tree, and end this process with that signal once the tree is gone. A second signal, such as
 * one another handler sends this process again, finds the stop under way and changes nothing.
 * @param root the command's process.
 * @param wiring how signals arrive and the machine the tree runs on.
 * @returns the stop under way and how to stop listening.
 */
export const forwardStops = (root: number, wiring: StopWiring): ForwardedStops => {
  const stops: Promise<void>[] = []
  const known: Map<number, ProcessRow> = new Map<number, ProcessRow>()
  const remember = (): void => {
    const rows: readonly ProcessRow[] = wiring.machine.table()
    const seen: readonly number[] = reachable(root, [...known.values()], rows)
    for (const pid of seen) {
      const row: ProcessRow | undefined = rows.find((candidate: ProcessRow): boolean => candidate.pid === pid)
      if (row !== undefined) {
        // eslint-disable-next-line functional/immutable-data -- what was seen under the command is kept until it stops
        known.set(pid, row)
      }
    }
  }
  const unwatch: () => void = wiring.watch(remember, TREE_WATCH_MS)
  const stop = async (signal: NodeJS.Signals): Promise<void> => {
    unwatch()
    const survivors: readonly number[] = await stopTree(wiring.machine, root, signal, { known: [...known.values()] })
    if (survivors.length > 0) {
      wiring.report(
        `processes ${survivors.join(', ')} were still running ${String(TREE_GRACE_MS / MILLISECONDS_PER_SECOND)}s after ${signal} and were ended outright`,
      )
    }
    unsubscribe()
    wiring.raise(signal)
  }
  const unsubscribe: () => void = wiring.subscribe((signal: NodeJS.Signals): void => {
    if (stops.length > 0) {
      return
    }
    // eslint-disable-next-line functional/immutable-data -- the first stop signal is recorded so a second one does not start another stop
    stops.push(stop(signal))
  })
  return {
    stopping: (): Promise<void> | undefined => stops[0],
    release: (): void => {
      unwatch()
      unsubscribe()
    },
  }
}
