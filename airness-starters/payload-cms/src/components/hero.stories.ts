import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { Hero } from './hero'

const meta: Meta<typeof Hero> = {
  component: Hero,
  args: {
    eyebrow: 'Starter',
    tagline: 'Films worth watching, and the reasons why.',
    description: 'Essays, lists and short histories for people who love going to the movies.',
  },
}

export default meta

type Story = StoryObj<typeof Hero>

export const Default: Story = {}

export const LongTagline: Story = {
  args: { tagline: 'A tagline that runs long enough to wrap onto a second and a third line on a phone' },
}
