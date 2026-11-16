import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { Pagination } from './pagination'

const meta: Meta<typeof Pagination> = {
  component: Pagination,
  args: { basePath: '/posts', total: 3 },
}

export default meta

type Story = StoryObj<typeof Pagination>

export const FirstPage: Story = { args: { current: 1 } }

export const MiddlePage: Story = { args: { current: 2 } }

export const LastPage: Story = { args: { current: 3 } }
