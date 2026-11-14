import type { CollectionConfig } from 'payload'

import { adminsOrEditors, anyone } from '@/access'
import { refuseUnlistedMediaType } from '@/hooks/media-type'

export const Media: CollectionConfig = {
  slug: 'media',
  admin: {
    useAsTitle: 'alt',
  },
  access: {
    read: anyone, // media is publicly readable
    create: adminsOrEditors,
    update: adminsOrEditors,
    delete: adminsOrEditors,
  },
  hooks: {
    beforeChange: [refuseUnlistedMediaType],
  },
  fields: [
    {
      name: 'alt',
      type: 'text',
      required: true,
    },
  ],
  // Listed one by one rather than as `image/*`, which would still admit `image/svg+xml`. An SVG is a
  // document that can carry script, and this collection is served from the application's own origin to
  // anyone, so accepting one would be stored XSS reachable without logging in. Payload checks the type it
  // detects from the content against this list, and `src/hooks/media-type.ts` holds the stored type to it too.
  upload: {
    mimeTypes: ['image/png', 'image/jpeg', 'image/webp', 'image/gif', 'image/avif'],
  },
}
