import type { Payload } from 'payload'
import { describe, expect, it } from 'vitest'

import type { AuditEvent, User } from '@/payload-types'
import { createAccount, credentials, LOGIN_PHRASE } from '../helpers/accounts'
import { callApi, callGraphqlLogin, FORBIDDEN, OK, sessionCookieFor, sessionCookieOf } from '../helpers/api'
import { payloadInstance } from '../helpers/payload'

const PURPOSE: string = 'audit-events'

const EVENT_LIMIT: number = 20
const EXPIRED_MS: number = 60_000

// Every account a spec makes has an address of its own, so the events that name it are this spec's alone.
const eventsNaming = async (account: User): Promise<AuditEvent[]> => {
  const payload: Payload = await payloadInstance
  const found: { readonly docs: AuditEvent[] } = await payload.find({
    collection: 'audit-events',
    where: { target: { contains: account.email } },
    sort: 'createdAt',
    depth: 0,
    limit: EVENT_LIMIT,
  })
  return found.docs
}

const lastEventNaming = async (account: User): Promise<AuditEvent | undefined> => {
  const events: AuditEvent[] = await eventsNaming(account)
  return events.at(-1)
}

const adminCall = async (method: 'DELETE' | 'PATCH' | 'POST', path: string, body?: object): Promise<string> => {
  const admin: User = await createAccount('admin', PURPOSE)
  await callApi({ method, path, cookie: await sessionCookieOf(admin), ...(body !== undefined && { body }) })
  return admin.email
}

describe('the audit trail', () => {
  it('records a successful and a failed sign-in', async () => {
    const account: User = await createAccount('user', PURPOSE)
    await callApi({ method: 'POST', path: '/users/login', body: credentials(account.email, LOGIN_PHRASE) })
    await callApi({ method: 'POST', path: '/users/login', body: credentials(account.email, 'wrong-secret') })
    const events: AuditEvent[] = await eventsNaming(account)
    const logins: Pick<AuditEvent, 'action' | 'outcome'>[] = events
      .filter(({ action }) => action.startsWith('login'))
      .map(({ action, outcome }) => ({ action, outcome }))
    expect(logins).toEqual([
      { action: 'login', outcome: 'success' },
      { action: 'login-failed', outcome: 'failure' },
    ])
  })

  it('records the account a failed GraphQL login was aimed at', async () => {
    const account: User = await createAccount('user', PURPOSE)
    await callGraphqlLogin(credentials(account.email, 'wrong-secret'))
    expect(await lastEventNaming(account)).toEqual(
      expect.objectContaining({ action: 'login-failed', outcome: 'failure', actor: 'anonymous' }),
    )
  })

  it('records a refused reset with the account its expired link was for', async () => {
    const account: User = await createAccount('user', PURPOSE)
    const payload: Payload = await payloadInstance
    const token: string = await payload.forgotPassword({
      collection: 'users',
      data: { email: account.email },
      disableEmail: true,
    })
    await payload.update({
      collection: 'users',
      id: account.id,
      data: { resetPasswordExpiration: new Date(Date.now() - EXPIRED_MS).toISOString() },
    })
    await callApi({ method: 'POST', path: '/users/reset-password', body: { token, password: 'a-reset-secret' } })
    expect(await lastEventNaming(account)).toEqual(
      expect.objectContaining({ action: 'password-reset', outcome: 'failure', actor: 'anonymous' }),
    )
  })

  it('records who changed an account and how its roles changed', async () => {
    const account: User = await createAccount('user', PURPOSE)
    const actor: string = await adminCall('PATCH', `/users/${String(account.id)}`, { roles: ['editor'] })
    expect(await lastEventNaming(account)).toEqual(
      expect.objectContaining({ action: 'roles-changed', actor, detail: 'roles: user -> editor', outcome: 'success' }),
    )
  })

  it('records a password change without the password', async () => {
    const account: User = await createAccount('user', PURPOSE)
    const actor: string = await adminCall('PATCH', `/users/${String(account.id)}`, { password: 'a-new-password' })
    expect(await lastEventNaming(account)).toEqual(
      expect.objectContaining({ action: 'account-updated', actor, detail: 'password changed' }),
    )
  })

  it('records every changed field, the roles in order', async () => {
    const account: User = await createAccount('user', PURPOSE)
    const renamed: string = `renamed-${account.email}`
    const actor: string = await adminCall('PATCH', `/users/${String(account.id)}`, {
      email: renamed,
      name: 'Renamed account',
      roles: ['user', 'editor'],
    })
    expect(await lastEventNaming({ ...account, email: renamed })).toEqual(
      expect.objectContaining({
        action: 'roles-changed',
        actor,
        detail: `roles: user -> editor, user; email: ${account.email} -> ${renamed}; name changed`,
      }),
    )
  })

  it('records a reset request and the reset it leads to', async () => {
    const account: User = await createAccount('user', PURPOSE)
    const payload: Payload = await payloadInstance
    const token: string = await payload.forgotPassword({
      collection: 'users',
      data: { email: account.email },
      disableEmail: true,
    })
    await callApi({ method: 'POST', path: '/users/reset-password', body: { token, password: 'a-reset-secret' } })
    const events: AuditEvent[] = await eventsNaming(account)
    const recovery: Pick<AuditEvent, 'action' | 'actor'>[] = events
      .filter(({ action }) => action.startsWith('password-reset'))
      .map(({ action, actor }) => ({ action, actor }))
    expect(recovery).toEqual([
      { action: 'password-reset-requested', actor: 'anonymous' },
      { action: 'password-reset', actor: account.email },
    ])
  })

  it('records who deleted an account', async () => {
    const account: User = await createAccount('user', PURPOSE)
    const actor: string = await adminCall('DELETE', `/users/${String(account.id)}`)
    expect(await lastEventNaming(account)).toEqual(expect.objectContaining({ action: 'account-deleted', actor }))
  })

  it('records who unlocked an account', async () => {
    const account: User = await createAccount('user', PURPOSE)
    const actor: string = await adminCall('POST', '/users/unlock', { email: account.email })
    expect(await lastEventNaming(account)).toEqual(expect.objectContaining({ action: 'account-unlocked', actor }))
  })

  it('records a logout', async () => {
    const account: User = await createAccount('user', PURPOSE)
    await callApi({ method: 'POST', path: '/users/logout', cookie: await sessionCookieOf(account) })
    expect(await lastEventNaming(account)).toEqual(expect.objectContaining({ action: 'logout', actor: account.email }))
  })
})

describe('access to the audit trail', () => {
  it.each([
    { role: 'anonymous' as const, status: FORBIDDEN },
    { role: 'user' as const, status: FORBIDDEN },
    { role: 'editor' as const, status: FORBIDDEN },
    { role: 'admin' as const, status: OK },
  ])('a $role reading it receives $status', async ({ role, status }) => {
    const response: Response = await callApi({
      method: 'GET',
      path: '/audit-events',
      cookie: await sessionCookieFor(role, PURPOSE),
    })
    expect(response.status).toBe(status)
  })

  it('lets not even an admin write, change or remove an event', async () => {
    const account: User = await createAccount('user', PURPOSE)
    const cookie: string = await sessionCookieFor('admin', PURPOSE)
    const event: AuditEvent | undefined = await lastEventNaming(account)
    const path: string = `/audit-events/${String(event?.id)}`
    const responses: Response[] = await Promise.all([
      callApi({ method: 'POST', path: '/audit-events', cookie, body: { action: 'login', outcome: 'success' } }),
      callApi({ method: 'PATCH', path, cookie, body: { actor: 'someone else' } }),
      callApi({ method: 'DELETE', path, cookie }),
    ])
    const statuses: number[] = responses.map(({ status }) => status)
    expect(statuses).toEqual([FORBIDDEN, FORBIDDEN, FORBIDDEN])
    expect(await lastEventNaming(account)).toEqual(event)
  })
})
