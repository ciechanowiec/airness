// Seeds the demo content: the categories, the posts with their covers, and the site settings. Idempotent: what
// already exists, matched by slug, is left as it is, so a re-run never duplicates content or overwrites an
// editor's changes.
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  convertMarkdownToLexical,
  editorConfigFactory,
  type SanitizedServerEditorConfig,
} from '@payloadcms/richtext-lexical'
import type { PaginatedDocs, Payload } from 'payload'

import type { Category, Media, Post } from '@/payload-types'
import { SEED_CATEGORIES, SEED_POSTS, type SeedCategory, type SeedPost } from './posts'

const IMAGES: string = path.join(path.dirname(fileURLToPath(import.meta.url)), 'images')
const ONE: number = 1

/** How many documents the seed created, by kind. */
export interface SeedReport {
  readonly categories: number
  readonly posts: number
}

const findCategory = async (payload: Payload, slug: string): Promise<Category | undefined> => {
  const found: PaginatedDocs<Category> = await payload.find({
    collection: 'categories',
    where: { slug: { equals: slug } },
    limit: ONE,
    depth: 0,
  })
  return found.docs[0]
}

const ensureCategory = async (payload: Payload, seed: SeedCategory): Promise<{ category: Category; made: boolean }> => {
  const existing: Category | undefined = await findCategory(payload, seed.slug)
  if (existing !== undefined) {
    return { category: existing, made: false }
  }
  const category: Category = await payload.create({ collection: 'categories', data: { ...seed } })
  return { category, made: true }
}

const hasPost = async (payload: Payload, slug: string): Promise<boolean> => {
  const found: PaginatedDocs<Post> = await payload.find({
    collection: 'posts',
    where: { slug: { equals: slug } },
    limit: ONE,
    depth: 0,
    draft: true,
  })
  return found.docs.length > 0
}

const createPost = async (
  payload: Payload,
  seed: SeedPost,
  categoryId: number,
  editorConfig: SanitizedServerEditorConfig,
): Promise<void> => {
  const cover: Media = await payload.create({
    collection: 'media',
    data: { alt: seed.imageAlt },
    filePath: path.join(IMAGES, `${seed.image}.webp`),
  })
  await payload.create({
    collection: 'posts',
    data: {
      title: seed.title,
      slug: seed.slug,
      excerpt: seed.excerpt,
      cover: cover.id,
      category: categoryId,
      content: convertMarkdownToLexical({ editorConfig, markdown: seed.markdown }),
      publishedAt: seed.publishedAt,
      featured: seed.featured,
      _status: 'published',
    },
  })
}

// One post at a time, each uploading its cover first: the reduce chains them, so the output stays in order.
const seedPosts = async (payload: Payload, categoryIds: ReadonlyMap<string, number>): Promise<number> => {
  const editorConfig: SanitizedServerEditorConfig = await editorConfigFactory.default({ config: payload.config })
  return await SEED_POSTS.reduce(async (previous: Promise<number>, seed: SeedPost): Promise<number> => {
    const created: number = await previous
    const categoryId: number | undefined = categoryIds.get(seed.category)
    if (categoryId === undefined || (await hasPost(payload, seed.slug))) {
      return created
    }
    await createPost(payload, seed, categoryId, editorConfig)
    return created + ONE
  }, Promise.resolve(0))
}

/**
 * Seeds the categories, the posts and, when they are still empty, the site settings.
 * @param payload a Payload instance against the database to seed.
 * @returns how many categories and posts the seed created.
 */
export const seedDemoContent = async (payload: Payload): Promise<SeedReport> => {
  const categories: readonly { category: Category; made: boolean }[] = await Promise.all(
    SEED_CATEGORIES.map(async (seed: SeedCategory) => await ensureCategory(payload, seed)),
  )
  const categoryIds: ReadonlyMap<string, number> = new Map(
    categories.map(({ category }): [string, number] => [category.slug, category.id]),
  )
  const posts: number = await seedPosts(payload, categoryIds)
  const settings: { readonly tagline?: string | null } = await payload.findGlobal({ slug: 'site-settings', depth: 0 })
  if ((settings.tagline ?? '') === '') {
    await payload.updateGlobal({
      slug: 'site-settings',
      data: {
        tagline: 'Films worth watching, and the reasons why.',
        description:
          'Essays, lists and short histories for people who love going to the movies: what to watch, how to watch it, and why it stays with us.',
        navigation: categories.map(({ category }) => category.id),
        footerNote: 'A small journal about the movies, written for the pleasure of watching them.',
      },
    })
  }
  return { categories: categories.filter(({ made }) => made).length, posts }
}
