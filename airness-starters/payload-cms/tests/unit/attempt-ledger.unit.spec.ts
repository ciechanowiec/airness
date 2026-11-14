import { describe, expect, it } from 'vitest'
import { type AttemptLedger, type AttemptPolicy, attemptLedger } from '@/lib/attempt-ledger'

const POLICY: AttemptPolicy = { maxAttempts: 3, windowMs: 1000, maxTrackedClients: 2 }
const START: number = 1_000_000
const WITHIN_WINDOW: number = START + POLICY.windowMs - 1
const NEXT_WINDOW: number = START + POLICY.windowMs

const CLIENT: string = '203.0.113.9'
const OTHER_CLIENT: string = '198.51.100.7'
const THIRD_CLIENT: string = '192.0.2.1'

// The verdict of each of the client's attempts in turn, all made at the start.
const verdictsInTurn = (ledger: AttemptLedger, times: number): string[] =>
  Array.from({ length: times }, (): string => ledger.recordAttempt(CLIENT, START))

// A ledger whose client has used up its window.
const exhaustedLedger = (): AttemptLedger => {
  const ledger: AttemptLedger = attemptLedger(POLICY)
  verdictsInTurn(ledger, POLICY.maxAttempts)
  return ledger
}

describe('the attempt ledger', () => {
  it('admits a client up to the limit and refuses it beyond', () => {
    expect(verdictsInTurn(attemptLedger(POLICY), POLICY.maxAttempts + 1)).toEqual([
      'admitted',
      'admitted',
      'admitted',
      'refused',
    ])
  })

  it('keeps refusing a client until its window ends', () => {
    expect(exhaustedLedger().recordAttempt(CLIENT, WITHIN_WINDOW)).toBe('refused')
  })

  it('admits a client again once its window ends', () => {
    expect(exhaustedLedger().recordAttempt(CLIENT, NEXT_WINDOW)).toBe('admitted')
  })

  it('counts each client on its own', () => {
    expect(exhaustedLedger().recordAttempt(OTHER_CLIENT, START)).toBe('admitted')
  })

  it('keeps every client while it tracks no more clients than it may', () => {
    const ledger: AttemptLedger = exhaustedLedger()
    ledger.recordAttempt(OTHER_CLIENT, START)
    expect(ledger.recordAttempt(CLIENT, START)).toBe('refused')
  })

  it('forgets the client idle for the longest time once it tracks too many', () => {
    const ledger: AttemptLedger = exhaustedLedger()
    ledger.recordAttempt(OTHER_CLIENT, START)
    ledger.recordAttempt(THIRD_CLIENT, START)
    expect(ledger.recordAttempt(CLIENT, START)).toBe('admitted')
  })

  it('keeps counting a client that is still making attempts when it forgets another', () => {
    const ledger: AttemptLedger = exhaustedLedger()
    ledger.recordAttempt(OTHER_CLIENT, START)
    ledger.recordAttempt(CLIENT, START)
    ledger.recordAttempt(THIRD_CLIENT, START)
    expect(ledger.recordAttempt(CLIENT, START)).toBe('refused')
  })
})
