import type { Payload } from 'payload'
import { describe, expect, it } from 'vitest'

import type { Category, Media, Post, User } from '@/payload-types'
import { createAccount } from '../helpers/accounts'
import { callApi, FORBIDDEN, sessionCookieFor } from '../helpers/api'
import { createCategory, createCover, createPost, paragraphContent, uniqueSlug } from '../helpers/content'
import { payloadInstance } from '../helpers/payload'

const PURPOSE: string = 'posts'

const draftAndPublished = async (): Promise<{ draft: Post; published: Post }> => {
  const [category, cover]: [Category, Media] = await Promise.all([createCategory(PURPOSE), createCover()])
  const [draft, published]: [Post, Post] = await Promise.all([
    createPost({ category, cover, status: 'draft' }),
    createPost({ category, cover, status: 'published' }),
  ])
  return { draft, published }
}

const slugsAnonymousReads = async (posts: readonly Post[]): Promise<readonly string[]> => {
  const payload: Payload = await payloadInstance
  const found: { readonly docs: readonly Post[] } = await payload.find({
    collection: 'posts',
    where: { slug: { in: posts.map((post: Post): string => post.slug) } },
    depth: 0,
    limit: posts.length,
    overrideAccess: false,
  })
  return found.docs.map((post: Post): string => post.slug)
}

describe('post access', () => {
  it('shows a visitor the published post and never the draft', async () => {
    const { draft, published } = await draftAndPublished()
    expect(await slugsAnonymousReads([draft, published])).toEqual([published.slug])
  })

  it('shows an editor the draft', async () => {
    const { draft } = await draftAndPublished()
    const editor: User = await createAccount('editor', PURPOSE)
    const payload: Payload = await payloadInstance
    const read: Post = await payload.findByID({
      collection: 'posts',
      id: draft.id,
      draft: true,
      depth: 0,
      user: editor,
      overrideAccess: false,
    })
    expect(read.slug).toBe(draft.slug)
  })

  it('keeps the version history from a signed-in reader without an editing role', async () => {
    const { published } = await draftAndPublished()
    const reader: User = await createAccount('user', PURPOSE)
    const payload: Payload = await payloadInstance
    await expect(
      payload.findVersions({
        collection: 'posts',
        where: { parent: { equals: published.id } },
        depth: 0,
        limit: 1,
        user: reader,
        overrideAccess: false,
      }),
    ).rejects.toThrow(/not allowed/u)
  })

  it('refuses a post from a signed-in reader without an editing role, and stores nothing', async () => {
    const slug: string = uniqueSlug(PURPOSE)
    const response: Response = await callApi({
      method: 'POST',
      path: '/posts',
      cookie: await sessionCookieFor('user', PURPOSE),
      body: { title: 'Not allowed', slug, excerpt: 'No.', content: paragraphContent('No.') },
    })
    const payload: Payload = await payloadInstance
    const { totalDocs } = await payload.count({ collection: 'posts', where: { slug: { equals: slug } } })
    expect([response.status, totalDocs]).toEqual([FORBIDDEN, 0])
  })
})

describe('post slugs', () => {
  it('makes the slug from the title when the editor leaves it empty', async () => {
    const [category, cover]: [Category, Media] = await Promise.all([createCategory(PURPOSE), createCover()])
    const title: string = `Hello, Wörld ${uniqueSlug('x')}`
    const post: Post = await createPost({ category, cover, status: 'draft', title })
    expect(post.slug).toMatch(/^hello-world-x-[\da-f-]+$/u)
  })

  // Payload validates a document as it is published; a draft is saved however unfinished it is.
  it('refuses to publish a post at a slug that is not one', async () => {
    const [category, cover]: [Category, Media] = await Promise.all([createCategory(PURPOSE), createCover()])
    await expect(createPost({ category, cover, status: 'published', slug: 'Not A Slug' })).rejects.toThrow(/slug/iu)
  })

  it('refuses a second post at a slug already taken', async () => {
    const [category, cover]: [Category, Media] = await Promise.all([createCategory(PURPOSE), createCover()])
    const slug: string = uniqueSlug(PURPOSE)
    await createPost({ category, cover, status: 'draft', slug })
    await expect(createPost({ category, cover, status: 'draft', slug })).rejects.toThrow(/slug/iu)
  })
})

describe('publication dates', () => {
  it('stamps the date a post is first published', async () => {
    const [category, cover]: [Category, Media] = await Promise.all([createCategory(PURPOSE), createCover()])
    const before: number = Date.now()
    const post: Post = await createPost({ category, cover, status: 'published' })
    expect(Date.parse(post.publishedAt ?? '')).toBeGreaterThanOrEqual(before)
  })

  it('keeps the date an editor chose', async () => {
    const [category, cover]: [Category, Media] = await Promise.all([createCategory(PURPOSE), createCover()])
    const chosen: string = '2024-05-06T07:08:09.000Z'
    const post: Post = await createPost({ category, cover, status: 'published', publishedAt: chosen })
    expect(post.publishedAt).toBe(chosen)
  })

  it('leaves a draft without a date', async () => {
    const [category, cover]: [Category, Media] = await Promise.all([createCategory(PURPOSE), createCover()])
    const post: Post = await createPost({ category, cover, status: 'draft' })
    expect(post.publishedAt ?? undefined).toBeUndefined()
  })
})
