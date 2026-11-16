import type { FieldHook, TypeWithID } from 'payload'

import { slugify } from '@/lib/slug'

/** A post or a category: any document with a title its slug can be made from. */
type TitledDocument = TypeWithID & { readonly title?: unknown }

/**
 * Fills an empty slug from the document's title, so an editor names a post once and may still choose its address.
 * A slug the editor wrote is kept as written, and the field's validation judges it.
 * @param args the field hook's arguments.
 * @param args.value the slug the editor wrote, if any.
 * @param args.data the document being saved.
 * @returns the slug to store, or undefined when there is neither a slug nor a title, which the field refuses.
 */
export const fillSlugFromTitle: FieldHook<TitledDocument, string | null | undefined> = ({ value, data }) => {
  if (typeof value === 'string' && value !== '') {
    return value
  }
  const title: unknown = data?.title
  return typeof title === 'string' ? slugify(title) : undefined
}
