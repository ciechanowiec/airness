import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { EmptyState } from './empty-state'

const meta: Meta<typeof EmptyState> = {
  component: EmptyState,
  args: {
    title: 'No posts yet',
    message: 'Sign in to the admin panel and publish a post to see it here.',
  },
}

export default meta

type Story = StoryObj<typeof EmptyState>

export const NoPosts: Story = {}
