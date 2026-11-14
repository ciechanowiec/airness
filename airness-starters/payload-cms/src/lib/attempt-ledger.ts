// How many authentication attempts each client made in its current window, and whether it may make another.
//
// Payload locks an account after repeated failures, which limits guesses at an account that exists and nothing
// else. An attempt that names no account pays a full password derivation all the same, so a refusal reveals no
// account by its timing, and without a limit of its own a single client could keep the server's thread pool busy
// with them. The ledger counts every attempt a client makes, whatever account it names, so the limit itself reveals
// no account either.
//
// The counts live in the memory of one server process: each instance of a horizontally scaled deployment keeps
// its own. The ledger is bounded: once it tracks the most clients it may, it forgets the client that made no attempt
// for the longest time, so a client still making attempts stays counted.

/** The limit a ledger applies. */
export interface AttemptPolicy {
  /** The attempts a client may make in one window. */
  readonly maxAttempts: number
  /** How long a window lasts, in milliseconds, counted from a client's first attempt in it. */
  readonly windowMs: number
  /** The most clients the ledger tracks at once. */
  readonly maxTrackedClients: number
}

/** Whether an attempt is within the limit. */
type AttemptVerdict = 'admitted' | 'refused'

/** The attempts one client made in its current window. */
interface AttemptWindow {
  readonly startedAt: number
  readonly attempts: number
}

/** The ledger: the one place attempt counts change. */
export interface AttemptLedger {
  /**
   * Count one attempt of a client and judge it against the limit.
   * @param client the client's key.
   * @param now the moment of the attempt, in milliseconds since the epoch.
   * @returns `admitted` when the attempt is within the limit, `refused` when the client has used up its window.
   */
  readonly recordAttempt: (client: string, now: number) => AttemptVerdict
}

/**
 * A ledger that applies one policy.
 * @param policy the limit.
 * @returns the ledger.
 */
export const attemptLedger = (policy: AttemptPolicy): AttemptLedger => {
  // Kept in the order of each client's latest attempt, so the first entry is the client idle for the longest time.
  const windows: Map<string, AttemptWindow> = new Map<string, AttemptWindow>()

  // The only two places the counts change. A limit has to remember the attempts of earlier requests, so this state
  // cannot be a new value per request; it is confined to this closure and changes only here.
  const forget = (client: string): void => {
    // eslint-disable-next-line functional/immutable-data -- a limit's counts must outlive the request that made them
    windows.delete(client)
  }

  // Inserted after any earlier entry is removed, so the client moves to the end, the order eviction reads.
  const remember = (client: string, window: AttemptWindow): void => {
    forget(client)
    // eslint-disable-next-line functional/immutable-data -- a limit's counts must outlive the request that made them
    windows.set(client, window)
  }

  const forgetIdlest = (): void => {
    const idlest: string | undefined = windows.keys().next().value
    if (idlest === undefined || windows.size <= policy.maxTrackedClients) {
      return
    }
    forget(idlest)
    forgetIdlest()
  }

  const nextWindow = (current: AttemptWindow | undefined, now: number): AttemptWindow =>
    current === undefined || now - current.startedAt >= policy.windowMs
      ? { startedAt: now, attempts: 1 }
      : { ...current, attempts: current.attempts + 1 }

  const recordAttempt = (client: string, now: number): AttemptVerdict => {
    const next: AttemptWindow = nextWindow(windows.get(client), now)
    remember(client, next)
    forgetIdlest()
    return next.attempts <= policy.maxAttempts ? 'admitted' : 'refused'
  }

  return { recordAttempt }
}
