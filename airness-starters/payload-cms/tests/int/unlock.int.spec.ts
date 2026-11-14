import { describe, expect, it } from 'vitest'

import type { User } from '@/payload-types'
import { lockedAccount, MAX_LOGIN_ATTEMPTS } from '../helpers/accounts'
import { callApi, FORBIDDEN, OK, sessionCookieFor, storedAccount } from '../helpers/api'

const PURPOSE: string = 'unlock'

// Exercise the framework's real request pipeline and database. Origin makes Payload authenticate
// the session cookie, so a denied editor is tested as an editor rather than as an anonymous caller.
describe('account unlock authorization', () => {
  it.each([
    { role: 'anonymous' as const, status: FORBIDDEN, attempts: MAX_LOGIN_ATTEMPTS },
    { role: 'user' as const, status: FORBIDDEN, attempts: MAX_LOGIN_ATTEMPTS },
    { role: 'editor' as const, status: FORBIDDEN, attempts: MAX_LOGIN_ATTEMPTS },
    { role: 'admin' as const, status: OK, attempts: 0 },
  ])('a $role receives $status when unlocking another account', async ({ role, status, attempts }) => {
    const target: User = await lockedAccount(PURPOSE)
    const response: Response = await callApi({
      method: 'POST',
      path: '/users/unlock',
      cookie: await sessionCookieFor(role, PURPOSE),
      body: { email: target.email },
    })
    expect(response.status).toBe(status)
    await expect(storedAccount(target.id)).resolves.toEqual(expect.objectContaining({ loginAttempts: attempts }))
  })
})
