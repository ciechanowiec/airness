import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { PageIntro } from './page-intro'

const meta: Meta<typeof PageIntro> = {
  component: PageIntro,
  args: {
    eyebrow: 'Category',
    title: 'Essays',
    description: 'Longer pieces on why films work on us the way they do.',
  },
}

export default meta

type Story = StoryObj<typeof PageIntro>

export const WithDescription: Story = {}

export const WithoutDescription: Story = { args: { eyebrow: 'Archive', title: 'All posts', description: '' } }
