import { randomUUID } from 'node:crypto'
import { describe, expect, it } from 'vitest'

import { derivePasswordHash } from '@/lib/password-hash'
import type { User } from '@/payload-types'
import { createAccount, credentials, LOGIN_PHRASE, lockedAccount, MAX_LOGIN_ATTEMPTS } from '../helpers/accounts'
import { callApi, callGraphqlLogin, storedAccount } from '../helpers/api'

const PURPOSE: string = 'login-concealment'

const WRONG_PASSWORD: string = 'not-the-password'

interface Refusal {
  readonly status: number
  readonly body: unknown
}

const attemptLogin = async (email: string, password: string): Promise<Refusal> => {
  const response: Response = await callApi({ method: 'POST', path: '/users/login', body: credentials(email, password) })
  return { status: response.status, body: await response.json() }
}

// One after another, as a person mistyping would, so each attempt counts against the account in turn.
const failLogins = async (email: string, times: number): Promise<void> => {
  if (times === 0) {
    return
  }
  await attemptLogin(email, WRONG_PASSWORD)
  await failLogins(email, times - 1)
}

const unknownEmail = (): string => `nobody-${randomUUID()}@example.test`

const graphqlLogin = async (email: string): Promise<unknown> => {
  const response: Response = await callGraphqlLogin(credentials(email, WRONG_PASSWORD))
  const { errors }: { readonly errors: readonly { readonly message: string }[] } = (await response.json()) as {
    readonly errors: readonly { readonly message: string }[]
  }
  return errors.map(({ message }) => message)
}

describe('a refused sign-in', () => {
  it('answers a locked account exactly as it answers an unknown email', async () => {
    const locked: User = await lockedAccount(PURPOSE)
    const forLocked: Refusal = await attemptLogin(locked.email, LOGIN_PHRASE)
    expect(forLocked).toEqual(await attemptLogin(unknownEmail(), LOGIN_PHRASE))
  })

  it('answers a locked account through GraphQL exactly as it answers an unknown email', async () => {
    const locked: User = await lockedAccount(PURPOSE)
    expect(await graphqlLogin(locked.email)).toEqual(await graphqlLogin(unknownEmail()))
  })

  it('locks the account after five failures, against its correct password too', async () => {
    const account: User = await createAccount('user', PURPOSE)
    await failLogins(account.email, MAX_LOGIN_ATTEMPTS)
    expect(await attemptLogin(account.email, LOGIN_PHRASE)).toEqual(await attemptLogin(unknownEmail(), LOGIN_PHRASE))
    const stored: User = await storedAccount(account.id)
    expect(stored.lockUntil).toBeTypeOf('string')
  })
})

const TIMED_DERIVATIONS: number = 3
const HALF: number = 0.5

// Derivations of the cost Payload pays for a wrong password, one after another, each timed.
const timedDerivationsMs = async (remaining: number): Promise<readonly number[]> => {
  if (remaining === 0) {
    return []
  }
  const startedAt: number = performance.now()
  await derivePasswordHash(LOGIN_PHRASE, randomUUID())
  const elapsedMs: number = performance.now() - startedAt
  return [elapsedMs, ...(await timedDerivationsMs(remaining - 1))]
}

// The fastest of a few derivations, since load can only slow one down.
const fastestDerivationMs = async (): Promise<number> => Math.min(...(await timedDerivationsMs(TIMED_DERIVATIONS)))

const refusalMs = async (email: string): Promise<number> => {
  const startedAt: number = performance.now()
  await attemptLogin(email, LOGIN_PHRASE)
  return performance.now() - startedAt
}

// Without the decoy derivation, a login refusal for an unknown email is a single lookup, a small fraction of a derivation.
// The bound is one-sided: load can only make the refusal slower, so it cannot fail a refusal that pays the cost.
describe('the time a refused login takes', () => {
  it('includes a password derivation for an unknown email', async () => {
    const derivationMs: number = await fastestDerivationMs()
    expect(await refusalMs(unknownEmail())).toBeGreaterThanOrEqual(derivationMs * HALF)
  })

  it('includes a password derivation for a locked account', async () => {
    const locked: User = await lockedAccount(PURPOSE)
    const derivationMs: number = await fastestDerivationMs()
    expect(await refusalMs(locked.email)).toBeGreaterThanOrEqual(derivationMs * HALF)
  })
})

// The hook that pays a refused sign-in's hashing cost for an unknown or locked account pays it with this derivation,
// so it has to be the one Payload uses: the same derivation of a stored account's salt yields its stored hash.
describe('the decoy password derivation', () => {
  it('derives the hash Payload stored for an account from its salt', async () => {
    const created: User = await createAccount('user', PURPOSE)
    const account: User = await storedAccount(created.id)
    if (typeof account.salt !== 'string') {
      throw new TypeError('Payload stored the account without a salt.')
    }
    expect(await derivePasswordHash(LOGIN_PHRASE, account.salt)).toBe(account.hash)
  })
})
