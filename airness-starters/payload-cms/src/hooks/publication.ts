import type { CollectionBeforeChangeHook } from 'payload'

const BLANK: ReadonlySet<unknown> = new Set([undefined, null, ''])

const isBlank = (value: unknown): boolean => BLANK.has(value)

/**
 * Stamps the publication date the first time a post is published without one, so every published post states
 * when it appeared. A date an editor chose, a later one included, is kept.
 * @param args the hook's arguments.
 * @param args.data the post being saved.
 * @returns the post to store.
 */
export const stampPublicationDate: CollectionBeforeChangeHook = ({ data }) =>
  data['_status'] === 'published' && isBlank(data['publishedAt'])
    ? { ...data, publishedAt: new Date().toISOString() }
    : data
