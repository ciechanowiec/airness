import type { Decorator, Meta, StoryObj } from '@storybook/nextjs-vite'

import type { PostSummary } from '@/lib/views'
import { PostCard } from './post-card'

const POST: PostSummary = {
  title: 'The art of the opening scene',
  slug: 'the-art-of-the-opening-scene',
  href: '/posts/the-art-of-the-opening-scene',
  excerpt: 'The first few minutes of a film teach you how to watch the rest of it. The best openings do it quietly.',
  readingMinutes: 4,
  publishedAt: '2026-03-04T09:00:00.000Z',
  cover: {
    url: '/seed/opening.webp',
    alt: "A film clapperboard beside a director's chair",
    width: 800,
    height: 450,
  },
  category: { title: 'Essays', slug: 'essays', href: '/categories/essays', description: '' },
}

// A card in a list is about a third of the page wide; a featured card spans it.
const narrow: Decorator = (Story) => (
  <div className="max-w-sm">
    <Story />
  </div>
)

const wide: Decorator = (Story) => (
  <div className="max-w-5xl">
    <Story />
  </div>
)

const meta: Meta<typeof PostCard> = {
  component: PostCard,
}

export default meta

type Story = StoryObj<typeof PostCard>

export const Standard: Story = { args: { post: POST }, decorators: [narrow] }

export const Featured: Story = {
  args: { post: POST, featured: true, headingLevel: 'h2' },
  decorators: [wide],
}

export const WithoutCoverOrCategory: Story = {
  args: {
    post: {
      title: POST.title,
      slug: POST.slug,
      href: POST.href,
      excerpt: POST.excerpt,
      readingMinutes: POST.readingMinutes,
    },
  },
  decorators: [narrow],
}

export const LongTitle: Story = {
  args: {
    post: {
      ...POST,
      title: 'A title long enough to wrap onto three lines, like a review of a very long film with a very long name',
      readingMinutes: 12,
    },
  },
  decorators: [narrow],
}
