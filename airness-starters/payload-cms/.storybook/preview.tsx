import type { Decorator, Preview } from '@storybook/nextjs-vite'
import '@/app/(frontend)/styles.css'

// Draws every story on the site's own ground, in its one light theme, as a page of the site draws it.
const onSiteGround: Decorator = (Story) => (
  <div className="min-h-screen bg-base-100 p-6 font-sans text-base-content antialiased">
    <Story />
  </div>
)

const preview: Preview = {
  parameters: {
    layout: 'fullscreen',
    nextjs: { appDirectory: true },
  },
  decorators: [onSiteGround],
}

export default preview
