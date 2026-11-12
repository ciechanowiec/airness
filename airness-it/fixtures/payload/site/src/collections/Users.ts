// The accounts that sign in to the admin panel, locked after repeated failed attempts, whose session cookie
// travels over HTTPS alone in production.
import type { CollectionConfig } from 'payload'
import { nobody } from '@/access'
import { isProduction } from '@/lib/environment'

/** The admin accounts. */
export const Users: CollectionConfig = {
  slug: 'users',
  auth: {
    maxLoginAttempts: 5,
    lockTime: 600_000,
    tokenExpiration: 7200,
    cookies: { secure: isProduction(), sameSite: 'Lax' },
  },
  access: {
    read: nobody,
    create: nobody,
    update: nobody,
    delete: nobody,
    unlock: nobody,
  },
  fields: [{ name: 'name', type: 'text', required: true }],
}
