import type { CollectionConfig } from 'payload'

import { admins, adminsFieldLevel, adminsOrEditors, adminsOrSelf } from '@/access'
import { firstUserIsAdmin, keepAnAdmin, keepAnAdminOnDelete } from '@/hooks/admin-continuity'
import { limitAuthenticationAttempts } from '@/hooks/attempt-limit'
import {
  auditAccountChange,
  auditAccountDeletion,
  auditAccountRecovery,
  auditLogin,
  auditLogout,
  noteAuthenticationAttempt,
  notePasswordChange,
} from '@/hooks/audit'
import { equalizeLoginCost } from '@/hooks/login-concealment'
import { isDevelopment } from '@/lib/environment'
import { sessionCookie } from '@/lib/session-cookie'

export const Users: CollectionConfig = {
  slug: 'users',
  admin: {
    useAsTitle: 'email',
    defaultColumns: ['name', 'email', 'roles', 'createdAt'],
  },
  // The guard that keeps an admin judges one account at a time, and Payload runs the per-account hooks of a bulk
  // edit or delete concurrently: each would count the other selected admins as remaining, and together they could
  // remove every admin. Accounts are therefore changed and deleted one at a time.
  disableBulkEdit: true,
  disableBulkDelete: true,
  auth: {
    tokenExpiration: 7200, // 2 hours
    maxLoginAttempts: 5,
    lockTime: 600_000, // 10 minutes
    cookies: sessionCookie(isDevelopment()),
  },
  access: {
    create: admins,
    read: adminsOrSelf,
    update: adminsOrSelf,
    delete: admins,
    unlock: admins,
    // Only admins and editors may access the admin panel.
    admin: adminsOrEditors,
  },
  hooks: {
    beforeOperation: [limitAuthenticationAttempts, noteAuthenticationAttempt, equalizeLoginCost],
    beforeChange: [firstUserIsAdmin, keepAnAdmin, notePasswordChange],
    beforeDelete: [keepAnAdminOnDelete],
    afterChange: [auditAccountChange],
    afterDelete: [auditAccountDeletion],
    afterLogin: [auditLogin],
    afterLogout: [auditLogout],
    afterOperation: [auditAccountRecovery],
  },
  fields: [
    {
      name: 'name',
      type: 'text',
      required: true,
    },
    {
      name: 'roles',
      type: 'select',
      hasMany: true,
      required: true,
      defaultValue: ['user'],
      saveToJWT: true, // role checks without a DB lookup
      options: ['admin', 'editor', 'user'],
      access: {
        // Only admins may read or change roles - prevents privilege escalation.
        read: adminsFieldLevel,
        create: adminsFieldLevel,
        update: adminsFieldLevel,
      },
    },
  ],
}
