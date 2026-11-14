import { randomUUID } from 'node:crypto'
import { describe, expect, it } from 'vitest'

import type { User } from '@/payload-types'
import { createAccount, credentials, LOGIN_PHRASE } from '../helpers/accounts'
import { BAD_REQUEST, callApi, newClientAddress, OK, TOO_MANY_REQUESTS, UNAUTHENTICATED } from '../helpers/api'

const PURPOSE: string = 'attempt-limit'

/** The attempts one client may make in a minute, as `src/hooks/attempt-limit.ts` sets them. */
const MAX_ATTEMPTS: number = 20

const unknownEmail = (): string => `nobody-${randomUUID()}@example.test`

const attemptLoginFrom = async (client: string, email: string, secret: string): Promise<number> => {
  const response: Response = await callApi({
    method: 'POST',
    path: '/users/login',
    body: credentials(email, secret),
    client,
  })
  return response.status
}

// One after another, as a script trying address after address would.
const failLoginsFrom = async (client: string, times: number): Promise<void> => {
  if (times === 0) {
    return
  }
  await attemptLoginFrom(client, unknownEmail(), LOGIN_PHRASE)
  await failLoginsFrom(client, times - 1)
}

describe('the limit on authentication attempts', () => {
  it('refuses a client beyond the limit even the correct password of an existing account', async () => {
    const account: User = await createAccount('user', PURPOSE)
    const client: string = newClientAddress()
    await failLoginsFrom(client, MAX_ATTEMPTS)
    expect(await attemptLoginFrom(client, account.email, LOGIN_PHRASE)).toBe(TOO_MANY_REQUESTS)
  })

  it('counts password-reset requests against the same limit', async () => {
    const client: string = newClientAddress()
    await failLoginsFrom(client, MAX_ATTEMPTS)
    const response: Response = await callApi({
      method: 'POST',
      path: '/users/forgot-password',
      body: { email: unknownEmail() },
      client,
    })
    expect(response.status).toBe(TOO_MANY_REQUESTS)
  })

  it('still admits another client once one has used up its limit', async () => {
    const account: User = await createAccount('user', PURPOSE)
    await failLoginsFrom(newClientAddress(), MAX_ATTEMPTS + 1)
    expect(await attemptLoginFrom(newClientAddress(), account.email, LOGIN_PHRASE)).toBe(OK)
  })

  it('admits a client within the limit to an ordinary refusal', async () => {
    expect(await attemptLoginFrom(newClientAddress(), unknownEmail(), LOGIN_PHRASE)).toBe(UNAUTHENTICATED)
  })

  it('refuses an attempt that names no client address', async () => {
    expect(await attemptLoginFrom('', unknownEmail(), LOGIN_PHRASE)).toBe(BAD_REQUEST)
  })
})
