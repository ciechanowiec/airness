import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { CategoryList } from './category-list'

const meta: Meta<typeof CategoryList> = {
  component: CategoryList,
  args: {
    categories: [
      {
        title: 'Essays',
        slug: 'essays',
        href: '/categories/essays',
        description: 'Longer pieces on why films work on us the way they do.',
      },
      {
        title: 'History',
        slug: 'history',
        href: '/categories/history',
        description: 'Where the movies came from, and the places and habits that grew up around them.',
      },
      { title: 'Lists', slug: 'lists', href: '/categories/lists', description: '' },
    ],
  },
}

export default meta

type Story = StoryObj<typeof CategoryList>

export const ThreeCategories: Story = {}
