import { describe, expect, it } from 'vitest'
import { isRefusedBeforeHashing } from '@/lib/password-hash'

const NOW: Date = new Date('2026-11-11T12:00:00.000Z')
const MINUTE_MS: number = 60_000

const minutesFromNow = (minutes: number): string => new Date(NOW.getTime() + minutes * MINUTE_MS).toISOString()

describe('which sign-in Payload refuses before it hashes the password', () => {
  it('refuses an email no account holds', () => {
    expect(isRefusedBeforeHashing(undefined, NOW)).toBe(true)
  })

  it('refuses an account locked until a later moment', () => {
    expect(isRefusedBeforeHashing({ lockUntil: minutesFromNow(1) }, NOW)).toBe(true)
  })

  it.each([
    { lockUntil: minutesFromNow(-1), state: 'whose lock has run out' },
    { lockUntil: null, state: 'that was never locked' },
    { lockUntil: undefined, state: 'that carries no lock field' },
  ])('hashes for an account $state', ({ lockUntil }) => {
    expect(isRefusedBeforeHashing({ lockUntil }, NOW)).toBe(false)
  })
})
