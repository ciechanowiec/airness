import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { SiteHeader } from './site-header'

const meta: Meta<typeof SiteHeader> = {
  component: SiteHeader,
  args: {
    siteName: 'Starter',
    navigation: [
      { label: 'All posts', href: '/posts' },
      { label: 'Essays', href: '/categories/essays' },
      { label: 'Lists', href: '/categories/lists' },
      { label: 'History', href: '/categories/history' },
    ],
  },
}

export default meta

type Story = StoryObj<typeof SiteHeader>

export const OnACategoryPage: Story = {
  parameters: { nextjs: { navigation: { pathname: '/categories/essays' } } },
}

export const WithoutCategories: Story = {
  args: { navigation: [{ label: 'All posts', href: '/posts' }] },
}
