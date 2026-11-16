// Content for the integration specs: categories, covers and posts made in the run's own database, each under a
// slug of its own, so a spec reads back what it made whatever else the database holds, the seeded demo included.
import { randomUUID } from 'node:crypto'
import path from 'node:path'
import type { Payload, RequiredDataFromCollectionSlug } from 'payload'

import type { Category, Media, Post } from '@/payload-types'
import { PIXEL } from './api'
import { payloadInstance } from './payload'

/**
 * A slug no other spec uses.
 * @param purpose - the spec's subject, which leads the slug so a failure shows where it came from.
 * @returns the slug.
 */
export const uniqueSlug = (purpose: string): string => `${purpose}-${randomUUID()}`

/** Rich text of one paragraph, as the editor stores it. */
export const paragraphContent = (text: string): Post['content'] => ({
  root: {
    type: 'root',
    version: 1,
    direction: 'ltr',
    format: '',
    indent: 0,
    children: [
      {
        type: 'paragraph',
        version: 1,
        direction: 'ltr',
        format: '',
        indent: 0,
        textFormat: 0,
        children: [{ type: 'text', version: 1, text, format: 0, detail: 0, mode: 'normal', style: '' }],
      },
    ],
  },
})

/**
 * A category of its own.
 * @param purpose - the spec's subject.
 * @returns the stored category.
 */
export const createCategory = async (purpose: string): Promise<Category> => {
  const payload: Payload = await payloadInstance
  return await payload.create({ collection: 'categories', data: { title: purpose, slug: uniqueSlug(purpose) } })
}

/**
 * A one-pixel cover of its own.
 * @returns the stored image.
 */
export const createCover = async (): Promise<Media> => {
  const payload: Payload = await payloadInstance
  return await payload.create({
    collection: 'media',
    data: { alt: 'A single pixel' },
    file: { data: Buffer.from(PIXEL), mimetype: 'image/png', name: `cover-${randomUUID()}.png`, size: PIXEL.length },
  })
}

/**
 * A full-size cover of its own, one of the seed's illustrations, for a page a browser specification judges as a
 * reader sees it.
 * @returns the stored image.
 */
export const createPhotoCover = async (): Promise<Media> => {
  const payload: Payload = await payloadInstance
  return await payload.create({
    collection: 'media',
    data: { alt: 'An empty cinema with a beam of projector light' },
    filePath: path.resolve('scripts/seed/images/cinema.webp'),
  })
}

/** What a spec chooses about the post it makes; the rest is filled in. */
export interface PostChoice {
  readonly category: Category
  readonly cover: Media
  readonly status: 'draft' | 'published'
  readonly slug?: string
  readonly title?: string
  readonly publishedAt?: string
  readonly featured?: boolean
}

// A post given a title of its own is saved with an empty slug, which the hook makes from the title; any other
// takes a slug no other spec uses.
const slugOf = (choice: PostChoice): string => choice.slug ?? (choice.title === undefined ? uniqueSlug('post') : '')

/**
 * A post of its own, stored through the Local API with access overridden, as an editor's save would store it.
 * @param choice - its category, cover and status, and anything else the spec decides.
 * @returns the stored post.
 */
export const createPost = async (choice: PostChoice): Promise<Post> => {
  const payload: Payload = await payloadInstance
  const data: RequiredDataFromCollectionSlug<'posts'> = {
    title: choice.title ?? 'A post of a spec',
    excerpt: 'Written by a spec.',
    content: paragraphContent('Some words of a spec.'),
    cover: choice.cover.id,
    category: choice.category.id,
    featured: choice.featured ?? false,
    _status: choice.status,
    slug: slugOf(choice),
    ...(choice.publishedAt !== undefined && { publishedAt: choice.publishedAt }),
  }
  return choice.status === 'draft'
    ? await payload.create({ collection: 'posts', draft: true, data })
    : await payload.create({ collection: 'posts', data })
}
