import type { CollectionConfig } from 'payload'

import { adminsOrEditors, anyone } from '@/access'
import { refuseUnlistedMediaType } from '@/hooks/media-type'

// The most pixels an uploaded picture may decode to, well above a 48-megapixel phone photograph. A file of a few
// megabytes can claim billions of pixels, and decoding one would exhaust the server's memory.
const MAXIMUM_PIXELS: number = 100_000_000

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
  //
  // Payload stores an uploaded file byte for byte unless the collection gives sharp an option, and a phone writes
  // where and when it took the photograph into the file. This collection is public, so any option makes Payload
  // re-encode the original in its own format, turned upright, without that metadata. The pixel bound is the one
  // option the collection has a use for of its own.
  upload: {
    mimeTypes: ['image/png', 'image/jpeg', 'image/webp', 'image/gif', 'image/avif'],
    constructorOptions: { limitInputPixels: MAXIMUM_PIXELS },
  },
}
