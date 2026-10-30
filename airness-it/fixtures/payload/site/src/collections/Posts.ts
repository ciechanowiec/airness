// The published posts, readable by anyone and written only through the admin panel.
import type { CollectionConfig } from 'payload'
import { anyone, nobody } from '@/access'

/** The blog posts. */
export const Posts: CollectionConfig = {
  slug: 'posts',
  versions: true,
  access: {
    read: anyone,
    create: nobody,
    update: nobody,
    delete: nobody,
    readVersions: nobody,
  },
  fields: [{ name: 'title', type: 'text', required: true }],
}
