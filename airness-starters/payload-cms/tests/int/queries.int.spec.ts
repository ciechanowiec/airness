import type { Payload, TypedUser } from 'payload'
import { describe, expect, it } from 'vitest'

import { POSTS_PER_PAGE } from '@/lib/pagination'
import type { CategorySummary, PostArticle, PostSummary, SiteIdentity } from '@/lib/views'
import type { Category, Media, Post, User } from '@/payload-types'
import { findCategories, findCategory } from '@/queries/categories'
import { findLeadingPosts, findPostDraft, findPublishedPost, findPublishedPosts, type PostPage } from '@/queries/posts'
import { findSignedInUser } from '@/queries/session'
import { findSiteIdentity } from '@/queries/site'
import { createAccount, sessionTokenOf } from '../helpers/accounts'
import { createCategory, createCover, createPost, uniqueSlug } from '../helpers/content'
import { payloadInstance } from '../helpers/payload'

const PURPOSE: string = 'queries'
const DAY: number = 86_400_000
const START: number = Date.parse('2030-01-01T00:00:00.000Z')
// Later than the clock the harness starts every test at, 2100-01-01, so no post a spec publishes now is newer.
const LATER_THAN_EVERY_CLOCK: string = '2300-01-01T00:00:00.000Z'

const dayAfterStart = (days: number): string => new Date(START + days * DAY).toISOString()

const slugsOf = (posts: readonly PostSummary[]): readonly string[] =>
  posts.map((post: PostSummary): string => post.slug)

// A category of its own holding one page and one post more of published posts, the newest made last.
const fullCategory = async (): Promise<{ category: Category; posts: readonly Post[] }> => {
  const [category, cover]: [Category, Media] = await Promise.all([createCategory(PURPOSE), createCover()])
  const posts: readonly Post[] = await Promise.all(
    Array.from(
      { length: POSTS_PER_PAGE + 1 },
      async (_unused: unknown, index: number) =>
        await createPost({ category, cover, status: 'published', publishedAt: dayAfterStart(index) }),
    ),
  )
  return { category, posts }
}

describe('findPublishedPosts', () => {
  it('lists the newest posts of a category first, a page at a time', async () => {
    const { category, posts } = await fullCategory()
    const first: PostPage = await findPublishedPosts({ page: 1, categorySlug: category.slug })
    expect(slugsOf(first.posts)).toEqual(
      posts
        .toReversed()
        .slice(0, POSTS_PER_PAGE)
        .map((post: Post): string => post.slug),
    )
  })

  it('counts the pages of a category and puts the oldest post on the last', async () => {
    const { category, posts } = await fullCategory()
    const last: PostPage = await findPublishedPosts({ page: 2, categorySlug: category.slug })
    expect([last.totalPages, slugsOf(last.posts)]).toEqual([2, [posts[0]?.slug]])
  })

  it('leaves the drafts of a category out', async () => {
    const [category, cover]: [Category, Media] = await Promise.all([createCategory(PURPOSE), createCover()])
    await createPost({ category, cover, status: 'draft' })
    const page: PostPage = await findPublishedPosts({ page: 1, categorySlug: category.slug })
    expect([page.posts, page.totalPages]).toEqual([[], 1])
  })

  it('summarizes each post with its cover and category', async () => {
    const [category, cover]: [Category, Media] = await Promise.all([createCategory(PURPOSE), createCover()])
    await createPost({ category, cover, status: 'published' })
    const page: PostPage = await findPublishedPosts({ page: 1, categorySlug: category.slug })
    const [summary]: readonly PostSummary[] = page.posts
    expect([summary?.category?.slug, summary?.cover?.alt]).toEqual([category.slug, 'A single pixel'])
  })

  it('lists posts of every category when it names none', async () => {
    const [category, cover]: [Category, Media] = await Promise.all([createCategory(PURPOSE), createCover()])
    const newest: Post = await createPost({ category, cover, status: 'published', publishedAt: LATER_THAN_EVERY_CLOCK })
    const page: PostPage = await findPublishedPosts({ page: 1 })
    expect(page.posts[0]?.slug).toBe(newest.slug)
  })
})

describe('findLeadingPosts', () => {
  it('leads with a featured post before newer ones that are not', async () => {
    const [category, cover]: [Category, Media] = await Promise.all([createCategory(PURPOSE), createCover()])
    const featured: Post = await createPost({
      category,
      cover,
      status: 'published',
      featured: true,
      publishedAt: dayAfterStart(2),
    })
    await createPost({ category, cover, status: 'published', publishedAt: dayAfterStart(3) })
    const [lead]: readonly PostSummary[] = await findLeadingPosts(1)
    expect(lead?.slug).toBe(featured.slug)
  })
})

describe('findPublishedPost', () => {
  it('reads a published post with its rich text', async () => {
    const [category, cover]: [Category, Media] = await Promise.all([createCategory(PURPOSE), createCover()])
    const post: Post = await createPost({ category, cover, status: 'published' })
    const article: PostArticle | undefined = await findPublishedPost(post.slug)
    expect([article?.summary.title, article?.content]).toEqual([post.title, post.content])
  })

  it('finds no post for a draft', async () => {
    const [category, cover]: [Category, Media] = await Promise.all([createCategory(PURPOSE), createCover()])
    const draft: Post = await createPost({ category, cover, status: 'draft' })
    expect(await findPublishedPost(draft.slug)).toBeUndefined()
  })
})

describe('findPostDraft', () => {
  it('shows an editor the newest draft of a published post', async () => {
    const [category, cover]: [Category, Media] = await Promise.all([createCategory(PURPOSE), createCover()])
    const post: Post = await createPost({ category, cover, status: 'published', title: 'Published title' })
    const payload: Payload = await payloadInstance
    await payload.update({ collection: 'posts', id: post.id, draft: true, data: { title: 'Draft title' } })
    const editor: User = await createAccount('editor', PURPOSE)
    const preview: PostArticle | undefined = await findPostDraft(post.slug, editor)
    expect(preview?.summary.title).toBe('Draft title')
  })

  it('shows a reader without an editing role no unpublished draft', async () => {
    const [category, cover]: [Category, Media] = await Promise.all([createCategory(PURPOSE), createCover()])
    const draft: Post = await createPost({ category, cover, status: 'draft' })
    const reader: User = await createAccount('user', PURPOSE)
    expect(await findPostDraft(draft.slug, reader)).toBeUndefined()
  })
})

describe('categories', () => {
  it('lists every category by title', async () => {
    const category: Category = await createCategory(PURPOSE)
    const categories: readonly CategorySummary[] = await findCategories()
    const slugs: readonly string[] = categories.map((summary: CategorySummary): string => summary.slug)
    expect(slugs).toContain(category.slug)
  })

  it('reads a category by its slug', async () => {
    const category: Category = await createCategory(PURPOSE)
    const found: CategorySummary | undefined = await findCategory(category.slug)
    expect(found?.href).toBe(`/categories/${category.slug}`)
  })

  it('finds no category for an unknown slug', async () => {
    expect(await findCategory(uniqueSlug('missing'))).toBeUndefined()
  })
})

describe('findSiteIdentity', () => {
  it('links the categories the settings name after all posts', async () => {
    const category: Category = await createCategory(PURPOSE)
    const payload: Payload = await payloadInstance
    await payload.updateGlobal({ slug: 'site-settings', data: { navigation: [category.id] } })
    const identity: SiteIdentity = await findSiteIdentity()
    expect(identity.navigation.map((link) => link.href)).toEqual(['/posts', `/categories/${category.slug}`])
  })
})

describe('findSignedInUser', () => {
  it('names the user a session token belongs to', async () => {
    const account: User = await createAccount('editor', PURPOSE)
    const headers: Headers = new Headers({ Authorization: `JWT ${await sessionTokenOf(account)}` })
    const user: TypedUser | undefined = await findSignedInUser(headers)
    expect(user?.id).toBe(account.id)
  })

  it('names nobody for an anonymous request', async () => {
    expect(await findSignedInUser(new Headers())).toBeUndefined()
  })
})
