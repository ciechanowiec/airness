import { randomUUID } from 'node:crypto'
import type { Payload } from 'payload'
import { describe, expect, it } from 'vitest'

import type { User } from '@/payload-types'
import { createAccount, credentials } from '../helpers/accounts'
import { BAD_REQUEST, callApi, FORBIDDEN, OK, sessionCookieOf, storedAccount } from '../helpers/api'
import { payloadInstance } from '../helpers/payload'

const PURPOSE: string = 'admin-continuity'

// The query a bulk operation selects the given accounts by, as the Users list view writes it.
const everyAccountOf = (accounts: readonly User[]): string =>
  accounts.map(({ id }, index: number): string => `where[id][in][${String(index)}]=${String(id)}`).join('&')

const rolesOf = async (accounts: readonly User[]): Promise<User['roles'][]> =>
  await Promise.all(
    accounts.map(async ({ id }): Promise<User['roles']> => {
      const { roles }: User = await storedAccount(id)
      return roles
    }),
  )

// First-user registration and the last admin exist only while the application has no other account, so each case
// starts from an empty `users` table. It is emptied through the database adapter, which runs no collection hook,
// because the hooks under test would refuse to delete the last admin. Every other spec makes the accounts it uses
// inside its own test, so none of them depends on an account this removes.
const removeEveryAccount = async (): Promise<void> => {
  const payload: Payload = await payloadInstance
  await payload.db.deleteMany({ collection: 'users', where: {} })
}

describe('the first account', () => {
  it('is registered as an admin whatever role the registration names', async () => {
    await removeEveryAccount()
    const email: string = `first-${randomUUID()}@example.test`
    const response: Response = await callApi({
      method: 'POST',
      path: '/users/first-register',
      body: { ...credentials(email, 'first-account-secret'), name: 'First account', roles: ['user'] },
    })
    expect(response.status).toBe(OK)
    const { user }: { readonly user: User } = (await response.json()) as { readonly user: User }
    await expect(storedAccount(user.id)).resolves.toEqual(expect.objectContaining({ roles: ['admin'] }))
  })
})

describe('the last admin', () => {
  it('cannot give up the admin role', async () => {
    await removeEveryAccount()
    const admin: User = await createAccount('admin', PURPOSE)
    const response: Response = await callApi({
      method: 'PATCH',
      path: `/users/${String(admin.id)}`,
      cookie: await sessionCookieOf(admin),
      body: { roles: ['user'] },
    })
    expect(response.status).toBe(BAD_REQUEST)
    await expect(storedAccount(admin.id)).resolves.toEqual(expect.objectContaining({ roles: ['admin'] }))
  })

  it('cannot delete their own account', async () => {
    await removeEveryAccount()
    const admin: User = await createAccount('admin', PURPOSE)
    const response: Response = await callApi({
      method: 'DELETE',
      path: `/users/${String(admin.id)}`,
      cookie: await sessionCookieOf(admin),
    })
    expect(response.status).toBe(BAD_REQUEST)
    await expect(storedAccount(admin.id)).resolves.toEqual(expect.objectContaining({ email: admin.email }))
  })

  it('cannot be demoted together with every other admin in one bulk edit', async () => {
    await removeEveryAccount()
    const actor: User = await createAccount('admin', PURPOSE)
    const admins: readonly User[] = [actor, await createAccount('admin', PURPOSE)]
    const response: Response = await callApi({
      method: 'PATCH',
      path: `/users?${everyAccountOf(admins)}`,
      cookie: await sessionCookieOf(actor),
      body: { roles: ['user'] },
    })
    expect(response.status).toBe(FORBIDDEN)
    await expect(rolesOf(admins)).resolves.toEqual([['admin'], ['admin']])
  })

  it('cannot be deleted together with every other admin in one bulk delete', async () => {
    await removeEveryAccount()
    const actor: User = await createAccount('admin', PURPOSE)
    const admins: readonly User[] = [actor, await createAccount('admin', PURPOSE)]
    const response: Response = await callApi({
      method: 'DELETE',
      path: `/users?${everyAccountOf(admins)}`,
      cookie: await sessionCookieOf(actor),
    })
    expect(response.status).toBe(FORBIDDEN)
    await expect(rolesOf(admins)).resolves.toEqual([['admin'], ['admin']])
  })

  it('may give up the role once another admin exists', async () => {
    await removeEveryAccount()
    const admin: User = await createAccount('admin', PURPOSE)
    await createAccount('admin', PURPOSE)
    const response: Response = await callApi({
      method: 'PATCH',
      path: `/users/${String(admin.id)}`,
      cookie: await sessionCookieOf(admin),
      body: { roles: ['user'] },
    })
    expect(response.status).toBe(OK)
    await expect(storedAccount(admin.id)).resolves.toEqual(expect.objectContaining({ roles: ['user'] }))
  })
})
