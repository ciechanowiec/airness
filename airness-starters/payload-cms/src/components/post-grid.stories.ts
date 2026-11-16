import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import type { CategorySummary, PostSummary } from '@/lib/views'
import { PostGrid } from './post-grid'

const ESSAYS: CategorySummary = { title: 'Essays', slug: 'essays', href: '/categories/essays', description: '' }
const HISTORY: CategorySummary = { title: 'History', slug: 'history', href: '/categories/history', description: '' }

const post = (slug: string, title: string, image: string, alt: string): PostSummary => ({
  title,
  slug,
  href: `/posts/${slug}`,
  excerpt: 'A sentence or two about the film or the idea, as the card shows it under the title.',
  readingMinutes: 3,
  publishedAt: '2026-03-04T09:00:00.000Z',
  cover: { url: `/seed/${image}.webp`, alt, width: 800, height: 450 },
  category: image === 'drive-in' ? HISTORY : ESSAYS,
})

const POSTS: readonly PostSummary[] = [
  post(
    'how-a-film-score-tells-you-what-to-feel',
    'How a film score tells you what to feel',
    'score',
    'A baton, sheet music and a strip of film',
  ),
  post(
    'the-art-of-the-opening-scene',
    'The art of the opening scene',
    'opening',
    "A clapperboard beside a director's chair",
  ),
  post(
    'drive-in-theatres-a-short-history',
    'Drive-in theatres: a short history',
    'drive-in',
    'Cars facing an outdoor screen at dusk',
  ),
]

const meta: Meta<typeof PostGrid> = {
  component: PostGrid,
  args: { emptyMessage: 'Nothing has been published yet. Published posts appear here, newest first.' },
}

export default meta

type Story = StoryObj<typeof PostGrid>

export const ThreePosts: Story = { args: { posts: POSTS } }

export const Empty: Story = { args: { posts: [] } }
