import type { PaginatedDocs, Payload } from 'payload'
import type { User } from '@/payload-types'

// A dev admin account the seed can create. `password` is a secret supplied per
// environment (see seed.ts); the rest is stable identity.
export interface DevelopmentAdmin {
  readonly email: string
  readonly password: string
  readonly name: string
  readonly roles: readonly User['roles'][number][]
}

// Canonical identity for the local admin, shared by the `pnpm seed` runner and
// the e2e login helper (tests/helpers/test-admin.ts) so the credentials are
// defined in exactly one place.
export const DEVELOPMENT_ADMIN_IDENTITY: Omit<DevelopmentAdmin, 'password'> = {
  email: 'dev@payloadcms.com',
  name: 'Dev User',
  roles: ['admin'],
}

// Idempotently ensure `admin` exists. The user is looked up by its natural key
// (email) and created only when absent, so re-running `pnpm seed` is a safe
// no-op rather than a duplicate-or-throw. The Local API runs with full
// privilege by default, so no banned `overrideAccess: true` flag is needed to
// set the access-protected `roles` field.
export async function seedDevelopmentAdmin(payload: Payload, admin: DevelopmentAdmin): Promise<'created' | 'exists'> {
  const existing: PaginatedDocs<User> = await payload.find({
    collection: 'users',
    where: { email: { equals: admin.email } },
    depth: 0,
    limit: 1,
  })

  if (existing.docs.length > 0) {
    return 'exists'
  }

  await payload.create({
    collection: 'users',
    data: {
      email: admin.email,
      password: admin.password,
      name: admin.name,
      roles: [...admin.roles],
    },
  })

  return 'created'
}
