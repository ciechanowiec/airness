// The posts the public site shows. Every read runs with the access rules of the caller, an anonymous visitor
// unless a user is named, so a draft never reaches a page that does not ask for it as an editor.
import type { PaginatedDocs, Payload, TypedUser, Where } from 'payload'

import { POSTS_PER_PAGE } from '@/lib/pagination'
import { type PostArticle, type PostSummary, toPostArticle, toPostSummary } from '@/lib/views'
import type { Post } from '@/payload-types'
import { payloadClient } from './payload'

/** One depth populates a post's cover and category, which every card and article shows. */
const POST_DEPTH: number = 1
const NEWEST_FIRST: string = '-publishedAt'
const ONE: number = 1

/** One page of a list of posts. */
export interface PostPage {
  readonly posts: readonly PostSummary[]
  readonly page: number
  readonly totalPages: number
}

/** Which page of which list to read. */
export interface PostQuery {
  /** The page, from 1. */
  readonly page: number
  /** The slug of the category to list, or every category when absent. */
  readonly categorySlug?: string
}

const inCategory = (categorySlug: string | undefined): Where =>
  categorySlug === undefined ? {} : { 'category.slug': { equals: categorySlug } }

/**
 * One page of the published posts, newest first.
 * @param query the page, and the category it lists.
 * @returns the page's posts and the number of pages the list has, at least one.
 */
export const findPublishedPosts = async (query: PostQuery): Promise<PostPage> => {
  const payload: Payload = await payloadClient()
  const result: PaginatedDocs<Post> = await payload.find({
    collection: 'posts',
    where: inCategory(query.categorySlug),
    sort: NEWEST_FIRST,
    page: query.page,
    limit: POSTS_PER_PAGE,
    depth: POST_DEPTH,
    overrideAccess: false,
  })
  return {
    posts: result.docs.map((post: Post): PostSummary => toPostSummary(post)),
    page: query.page,
    totalPages: Math.max(ONE, result.totalPages),
  }
}

/**
 * The newest published posts, the featured one first when there is one.
 * @param count how many posts to return.
 * @returns the posts.
 */
export const findLeadingPosts = async (count: number): Promise<readonly PostSummary[]> => {
  const payload: Payload = await payloadClient()
  const result: PaginatedDocs<Post> = await payload.find({
    collection: 'posts',
    sort: ['-featured', NEWEST_FIRST],
    limit: count,
    depth: POST_DEPTH,
    overrideAccess: false,
  })
  return result.docs.map((post: Post): PostSummary => toPostSummary(post))
}

/**
 * The published post a slug names.
 * @param slug the post's slug.
 * @returns the post, or undefined when no published post has the slug.
 */
export const findPublishedPost = async (slug: string): Promise<PostArticle | undefined> => {
  const payload: Payload = await payloadClient()
  const result: PaginatedDocs<Post> = await payload.find({
    collection: 'posts',
    where: { slug: { equals: slug } },
    limit: ONE,
    depth: POST_DEPTH,
    overrideAccess: false,
  })
  const post: Post | undefined = result.docs[0]
  return post === undefined ? undefined : toPostArticle(post)
}

/**
 * The newest draft of the post a slug names, as the given user may read it: every draft for an editor, and only
 * a published post for anyone else.
 * @param slug the post's slug.
 * @param user the signed-in user asking for the preview.
 * @returns the post, or undefined when the user may read no post with the slug.
 */
export const findPostDraft = async (slug: string, user: TypedUser): Promise<PostArticle | undefined> => {
  const payload: Payload = await payloadClient()
  const result: PaginatedDocs<Post> = await payload.find({
    collection: 'posts',
    where: { slug: { equals: slug } },
    limit: ONE,
    depth: POST_DEPTH,
    draft: true,
    user,
    overrideAccess: false,
  })
  const post: Post | undefined = result.docs[0]
  return post === undefined ? undefined : toPostArticle(post)
}
