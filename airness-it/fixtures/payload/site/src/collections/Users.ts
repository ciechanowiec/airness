// The accounts that sign in to the admin panel, locked after repeated failed attempts.
import type { CollectionConfig } from 'payload'
import { nobody } from '@/access'

/** The admin accounts. */
export const Users: CollectionConfig = {
  slug: 'users',
  auth: {
    maxLoginAttempts: 5,
    lockTime: 600_000,
    tokenExpiration: 7200,
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
