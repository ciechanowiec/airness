import type { StorybookConfig } from '@storybook/nextjs-vite'

// The component workbench: every presentational component of the site has stories beside it, which render it in
// each state a reader can meet. `pnpm run storybook` serves it while developing, and Extended verification serves
// it too and sweeps every story for accessibility and layout, as it sweeps the site's pages.
const config: StorybookConfig = {
  stories: ['../src/**/*.stories.@(ts|tsx)'],
  framework: { name: '@storybook/nextjs-vite', options: {} },
  // Small copies of the seed's cover images, so a story shows the pictures a seeded site does without a running
  // server. Storybook serves an image as it is, at no other size, so a card-sized copy keeps it no larger than it shows.
  staticDirs: [{ from: './images', to: '/seed' }],
  core: {
    disableTelemetry: true,
    disableWhatsNewNotifications: true,
  },
}

export default config
