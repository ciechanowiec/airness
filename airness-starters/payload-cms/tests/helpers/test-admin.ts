import { DEVELOPMENT_ADMIN_IDENTITY, type DevelopmentAdmin } from '@scripts/development-admin'
import { type BasePayload, getPayload } from 'payload'
import config from '@/payload.config'

// Reuses the canonical seed identity (scripts/development-admin.ts); only the password is test-specific.
/** The admin identity the end-to-end specs sign in as. */
export const testAdmin: DevelopmentAdmin = {
  ...DEVELOPMENT_ADMIN_IDENTITY,
  password: 'test',
}

/**
 * Makes the admin the end-to-end specs sign in as exist with the credentials above, unlocked.
 *
 * Created once and brought back to that state on every later call rather than deleted and recreated: the account
 * is the run's only admin, and the application refuses to delete its last admin. Nothing removes it afterwards,
 * because the run's database is removed with it.
 */
export async function seedTestAdmin(): Promise<void> {
  const payload: BasePayload = await getPayload({ config })
  const existing: { readonly docs: { readonly id: number }[] } = await payload.find({
    collection: 'users',
    where: { email: { equals: testAdmin.email } },
    depth: 0,
    limit: 1,
  })
  const account: { readonly id: number } | undefined = existing.docs[0]
  if (account === undefined) {
    await payload.create({ collection: 'users', data: { ...testAdmin, roles: [...testAdmin.roles] } })
    return
  }
  await payload.update({
    collection: 'users',
    id: account.id,
    data: { ...testAdmin, roles: [...testAdmin.roles], loginAttempts: 0, lockUntil: null },
  })
}
