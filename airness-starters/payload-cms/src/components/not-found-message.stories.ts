import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { NotFoundMessage } from './not-found-message'

const meta: Meta<typeof NotFoundMessage> = {
  component: NotFoundMessage,
}

export default meta

type Story = StoryObj<typeof NotFoundMessage>

export const PageNotFound: Story = {}
