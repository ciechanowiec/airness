// The plain values the component specs render, shaped as the view models in `src/lib/views.ts` make them.
import type { CategorySummary, NavigationLink, PostSummary } from '@/lib/views'

/** A category with a page and a description. */
export const GUIDES: CategorySummary = {
  title: 'Guides',
  slug: 'guides',
  href: '/categories/guides',
  description: 'Step-by-step help.',
}

/** A published post with every field a card shows. */
export const POST: PostSummary = {
  title: 'Writing and publishing a post',
  slug: 'writing-and-publishing-a-post',
  href: '/posts/writing-and-publishing-a-post',
  excerpt: 'The fields a post has and what each does.',
  readingMinutes: 4,
  publishedAt: '2026-03-04T09:00:00.000Z',
  cover: { url: '/api/media/file/writing.webp', alt: 'A typewriter', width: 2400, height: 1350 },
  category: GUIDES,
}

/** A draft that was never published and lost its cover and category. */
export const BARE_POST: PostSummary = {
  title: 'A bare draft',
  slug: 'a-bare-draft',
  href: '/posts/a-bare-draft',
  excerpt: 'Nothing but words.',
  readingMinutes: 1,
}

/** The navigation the site settings give. */
export const NAVIGATION: readonly NavigationLink[] = [
  { label: 'All posts', href: '/posts' },
  { label: 'Guides', href: '/categories/guides' },
]
