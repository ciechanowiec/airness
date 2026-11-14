import type { CollectionConfig } from 'payload'

import { admins, nobody } from '@/access'
import { AUDIT_ACTIONS, AUDIT_OUTCOMES } from '@/lib/audit-event'

// Written only by the hooks in src/hooks/audit.ts. Nobody, an admin included, creates, changes or deletes an
// event, so an event states what happened and nobody can rewrite it afterwards.
export const AuditEvents: CollectionConfig = {
  slug: 'audit-events',
  admin: {
    useAsTitle: 'action',
    defaultColumns: ['createdAt', 'action', 'outcome', 'actor', 'target'],
  },
  access: {
    create: nobody,
    read: admins,
    update: nobody,
    delete: nobody,
  },
  fields: [
    {
      name: 'action',
      type: 'select',
      required: true,
      options: [...AUDIT_ACTIONS],
    },
    {
      name: 'outcome',
      type: 'select',
      required: true,
      options: [...AUDIT_OUTCOMES],
    },
    {
      name: 'actor',
      type: 'text',
      required: true,
    },
    {
      name: 'target',
      type: 'text',
      required: true,
    },
    {
      name: 'detail',
      type: 'text',
    },
  ],
}
