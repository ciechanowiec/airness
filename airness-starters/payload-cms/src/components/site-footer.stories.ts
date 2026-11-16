import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { SiteFooter } from './site-footer'

const meta: Meta<typeof SiteFooter> = {
  component: SiteFooter,
  args: {
    siteName: 'Starter',
    note: 'A small journal about the movies, written for the pleasure of watching them.',
  },
}

export default meta

type Story = StoryObj<typeof SiteFooter>

export const WithNote: Story = {}

export const WithoutNote: Story = { args: { note: '' } }
