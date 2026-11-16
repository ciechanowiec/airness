import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { ArticleHeader } from './article-header'

const meta: Meta<typeof ArticleHeader> = {
  component: ArticleHeader,
  args: {
    post: {
      title: 'Why the cinema still feels like magic',
      slug: 'why-the-cinema-still-feels-like-magic',
      href: '/posts/why-the-cinema-still-feels-like-magic',
      excerpt: 'A dark room, a beam of light and a crowd of strangers holding their breath together.',
      readingMinutes: 3,
      publishedAt: '2026-03-01T09:00:00.000Z',
      // A page-wide copy: wide enough for the header's full width, and under a megapixel, so no viewport upscales
      // it or decodes more of it than it shows.
      cover: {
        url: '/seed/cinema-wide.webp',
        alt: 'An empty cinema with a beam of projector light',
        width: 1280,
        height: 720,
      },
      category: { title: 'Essays', slug: 'essays', href: '/categories/essays', description: '' },
    },
  },
}

export default meta

type Story = StoryObj<typeof ArticleHeader>

export const Published: Story = {}

export const UnpublishedDraft: Story = {
  args: {
    post: {
      title: 'A review still being written',
      slug: 'a-draft',
      href: '/posts/a-draft',
      excerpt: 'A preview shows a draft like this one to editors before anyone else can read it.',
      readingMinutes: 1,
    },
  },
}
