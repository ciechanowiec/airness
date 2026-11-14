import type { CollectionBeforeChangeHook } from 'payload'
import { ValidationError } from 'payload'
import type { Media } from '@/payload-types'

// Payload checks the type it detects from a file's content against the collection's `mimeTypes`, but when it
// detects none it falls back to the file name and stores the type the uploader declared, which its file route then
// serves: a page of HTML named `promo.png` and declared `text/html` would be served as HTML from this origin. A file
// fetched from a URL arrives with the type its server declared, and an update can name a type outright. So the type
// about to be stored is held to the same list, read from the collection itself so the list has one home, whichever
// way the type arrived. A storage adapter adds its own `beforeChange` hook after this one, so a refused file is never
// written to the bucket.
export const refuseUnlistedMediaType: CollectionBeforeChangeHook<Media> = ({ collection, data }): void => {
  const stored: string | null | undefined = data.mimeType
  const accepted: readonly string[] = collection.upload.mimeTypes ?? []
  if (typeof stored === 'string' && !accepted.includes(stored)) {
    throw new ValidationError({
      collection: collection.slug,
      errors: [{ message: `Files of type ${stored} are not accepted.`, path: 'file' }],
    })
  }
}
