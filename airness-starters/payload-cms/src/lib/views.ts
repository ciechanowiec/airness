// The plain values the components render, made from the documents Payload returns. Components take these rather
// than Payload's types, so they render the same in a page, in a test and in Storybook, and a missing cover or
// category, which deleting one leaves behind, is decided here once.
import type { Category, Media, Post, SiteSetting } from '@/payload-types'

import { readingMinutes } from './reading-time'
import { DEFAULT_DESCRIPTION, DEFAULT_TAGLINE } from './site'

/** An image with the size it was stored at, which reserves its box before it loads. */
export interface CoverImage {
  readonly url: string
  readonly alt: string
  readonly width: number
  readonly height: number
}

/** A category as a link and a heading show it. */
export interface CategorySummary {
  readonly title: string
  readonly slug: string
  readonly href: string
  readonly description: string
}

/** A post as a card shows it. */
export interface PostSummary {
  readonly title: string
  readonly slug: string
  readonly href: string
  readonly excerpt: string
  readonly readingMinutes: number
  readonly publishedAt?: string
  readonly cover?: CoverImage
  readonly category?: CategorySummary
}

/** A post as its own page shows it: the summary and the rich text. */
export interface PostArticle {
  readonly summary: PostSummary
  readonly content: Post['content']
}

/** One link of the site's navigation. */
export interface NavigationLink {
  readonly label: string
  readonly href: string
}

/** What every page shows about the site. */
export interface SiteIdentity {
  readonly tagline: string
  readonly description: string
  readonly footerNote: string
  readonly navigation: readonly NavigationLink[]
}

/** The link every navigation starts with, to the list of all posts. */
const ALL_POSTS_LINK: NavigationLink = { label: 'All posts', href: '/posts' }

const isMedia = (value: unknown): value is Media => typeof value === 'object' && value !== null && 'alt' in value

const isCategory = (value: unknown): value is Category =>
  typeof value === 'object' && value !== null && 'slug' in value && 'title' in value

const isPositive = (value: number | null | undefined): value is number => typeof value === 'number' && value > 0

const textOr = (value: string | null | undefined, fallback: string): string =>
  typeof value === 'string' && value.trim() !== '' ? value : fallback

/**
 * The cover of a post, when the post has one populated with its size.
 * @param value the post's `cover`: a media document, or only its id when the read did not populate it.
 * @returns the image, or undefined when there is none to show.
 */
const coverImageOf = (value: unknown): CoverImage | undefined => {
  if (!isMedia(value) || typeof value.url !== 'string' || !isPositive(value.width) || !isPositive(value.height)) {
    return undefined
  }
  return { url: value.url, alt: value.alt, width: value.width, height: value.height }
}

/**
 * A category as a link shows it.
 * @param category the category.
 * @returns its summary, linking its page.
 */
export const toCategorySummary = (category: Category): CategorySummary => ({
  title: category.title,
  slug: category.slug,
  href: `/categories/${encodeURIComponent(category.slug)}`,
  description: category.description ?? '',
})

const categoryOf = (value: unknown): CategorySummary | undefined =>
  isCategory(value) ? toCategorySummary(value) : undefined

/**
 * A post as a card shows it.
 * @param post the post, read with its cover and category populated.
 * @returns its summary.
 */
export const toPostSummary = (post: Post): PostSummary => {
  const cover: CoverImage | undefined = coverImageOf(post.cover)
  const category: CategorySummary | undefined = categoryOf(post.category)
  return {
    title: post.title,
    slug: post.slug,
    href: `/posts/${encodeURIComponent(post.slug)}`,
    excerpt: post.excerpt,
    readingMinutes: readingMinutes(post.content),
    ...(typeof post.publishedAt === 'string' && { publishedAt: post.publishedAt }),
    ...(cover !== undefined && { cover }),
    ...(category !== undefined && { category }),
  }
}

/**
 * A post as its own page shows it.
 * @param post the post, read with its cover and category populated.
 * @returns its summary and its rich text.
 */
export const toPostArticle = (post: Post): PostArticle => ({ summary: toPostSummary(post), content: post.content })

/**
 * What every page shows about the site, the defaults standing in for anything the settings leave empty.
 * @param settings the site settings, read with their navigation populated.
 * @returns the site's identity.
 */
export const toSiteIdentity = (settings: SiteSetting): SiteIdentity => ({
  tagline: textOr(settings.tagline, DEFAULT_TAGLINE),
  description: textOr(settings.description, DEFAULT_DESCRIPTION),
  footerNote: textOr(settings.footerNote, ''),
  navigation: [
    ALL_POSTS_LINK,
    ...(settings.navigation ?? [])
      .filter((entry: number | Category): entry is Category => isCategory(entry))
      .map((category: Category): NavigationLink => ({ label: category.title, href: toCategorySummary(category).href })),
  ],
})
