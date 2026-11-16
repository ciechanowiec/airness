import { describe, expect, it } from 'vitest'

import { DEFAULT_DESCRIPTION, DEFAULT_TAGLINE } from '@/lib/site'
import {
  type CategorySummary,
  type PostSummary,
  type SiteIdentity,
  toCategorySummary,
  toPostArticle,
  toPostSummary,
  toSiteIdentity,
} from '@/lib/views'
import type { Category, Media, Post } from '@/payload-types'

const ALL_POSTS_LINK: { readonly label: string; readonly href: string } = { label: 'All posts', href: '/posts' }

const STAMP: string = '2026-03-04T09:00:00.000Z'

const CATEGORY: Category = {
  id: 3,
  title: 'Guides',
  slug: 'guides',
  description: 'How to write here.',
  createdAt: STAMP,
  updatedAt: STAMP,
}

const MEDIA: Media = {
  id: 7,
  alt: 'A typewriter',
  url: '/api/media/file/writing.webp',
  width: 2400,
  height: 1350,
  createdAt: STAMP,
  updatedAt: STAMP,
}

const CONTENT: Post['content'] = {
  root: {
    type: 'root',
    version: 1,
    direction: 'ltr',
    format: '',
    indent: 0,
    children: [{ type: 'paragraph', version: 1, children: [{ type: 'text', version: 1, text: 'Hello there' }] }],
  },
}

const postWith = (fields: Partial<Post>): Post => ({
  id: 1,
  title: 'Writing a post',
  slug: 'writing a post',
  excerpt: 'How it works.',
  cover: MEDIA,
  category: CATEGORY,
  content: CONTENT,
  publishedAt: STAMP,
  createdAt: STAMP,
  updatedAt: STAMP,
  ...fields,
})

describe('toCategorySummary', () => {
  it('links the category page by its slug', () => {
    expect(toCategorySummary(CATEGORY)).toEqual({
      title: 'Guides',
      slug: 'guides',
      href: '/categories/guides',
      description: 'How to write here.',
    })
  })

  it('gives a category without a description an empty one', () => {
    expect(toCategorySummary({ ...CATEGORY, description: null }).description).toBe('')
  })
})

describe('the cover of a post', () => {
  it('keeps a populated image with its size', () => {
    expect(toPostSummary(postWith({ cover: MEDIA })).cover).toEqual({
      url: MEDIA.url,
      alt: 'A typewriter',
      width: 2400,
      height: 1350,
    })
  })

  it.each([
    ['an id the read did not populate', 7],
    ['an image without a URL', { ...MEDIA, url: null }],
    ['an image without a size', { ...MEDIA, width: null }],
    ['an image of no width', { ...MEDIA, width: 0 }],
    ['an image of no height', { ...MEDIA, height: 0 }],
  ])('shows no cover for %s', (_case: string, cover: Post['cover']) => {
    expect(toPostSummary(postWith({ cover })).cover).toBeUndefined()
  })
})

describe('toPostSummary', () => {
  it('summarizes a post with its cover, category, date and reading time', () => {
    const summary: PostSummary = toPostSummary(postWith({}))
    expect(summary).toEqual({
      title: 'Writing a post',
      slug: 'writing a post',
      href: '/posts/writing%20a%20post',
      excerpt: 'How it works.',
      readingMinutes: 1,
      publishedAt: STAMP,
      cover: { url: MEDIA.url, alt: 'A typewriter', width: 2400, height: 1350 },
      category: toCategorySummary(CATEGORY),
    })
  })

  it('leaves out what a draft or a deleted reference lacks', () => {
    const summary: PostSummary = toPostSummary(postWith({ cover: 7, category: 3, publishedAt: null }))
    expect(Object.keys(summary)).toEqual(['title', 'slug', 'href', 'excerpt', 'readingMinutes'])
  })

  it('carries the rich text into the article', () => {
    expect(toPostArticle(postWith({})).content).toBe(CONTENT)
  })
})

describe('toSiteIdentity', () => {
  it('stands the defaults in for empty settings', () => {
    expect(toSiteIdentity({ id: 1, tagline: ' ', description: null })).toEqual({
      tagline: DEFAULT_TAGLINE,
      description: DEFAULT_DESCRIPTION,
      footerNote: '',
      navigation: [ALL_POSTS_LINK],
    })
  })

  it('links all posts and then each populated category, in the order the settings give', () => {
    const identity: SiteIdentity = toSiteIdentity({
      id: 1,
      tagline: 'Our notes',
      footerNote: 'Made here.',
      navigation: [{ ...CATEGORY, title: 'Design', slug: 'design' }, 9, CATEGORY],
    })
    expect(identity.navigation).toEqual([
      ALL_POSTS_LINK,
      { label: 'Design', href: '/categories/design' },
      { label: 'Guides', href: '/categories/guides' },
    ])
    expect([identity.tagline, identity.footerNote]).toEqual(['Our notes', 'Made here.'])
  })

  it('keeps categories as summaries a link can name', () => {
    const summary: CategorySummary = toCategorySummary(CATEGORY)
    expect(toSiteIdentity({ id: 1, navigation: [CATEGORY] }).navigation[1]).toEqual({
      label: summary.title,
      href: summary.href,
    })
  })
})
