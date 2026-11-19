import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { withPayload } from '@payloadcms/next/withPayload'
import type { NextConfig } from 'next'

const __filename: string = fileURLToPath(import.meta.url)
const dirname: string = path.dirname(__filename)

const nextConfig: NextConfig = {
  // `next dev` would otherwise write its agent rules into AGENTS.md, in typography the harness refuses;
  // AGENTS.md carries the same guidance in its project-owned part.
  agentRules: false,
  // Next.js and withPayload would otherwise name themselves in X-Powered-By, which the harness refuses.
  poweredByHeader: false,
  // The Dockerfile copies `.next/standalone`, which `next build` produces only under this setting.
  output: 'standalone',
  // sharp loads the binary of the platform it runs on by a name it computes at run time, which the tracing
  // cannot follow, so the standalone server would carry only the host's. The image runs on Alpine Linux, so
  // the musl binaries pnpm-workspace.yaml installs are traced into every server route as well.
  outputFileTracingIncludes: {
    '/**/*': [
      './node_modules/.pnpm/@img+sharp-linuxmusl-*/**/*',
      './node_modules/.pnpm/@img+sharp-libvips-linuxmusl-*/**/*',
    ],
  },
  // The site and the admin panel each have a root layout, so no layout can wrap a 404 page for an address neither
  // claims; src/app/global-not-found.tsx draws that page whole instead of Next.js's bare built-in one.
  experimental: {
    globalNotFound: true,
  },
  // next/image renders each stored picture at the widths below, on its first request, and keeps the result. The
  // widths are the ones the pages draw a picture at, from a card on a phone to the article cover on a wide screen at
  // twice its pixels, so each picture has few variants and a later visitor reuses them. AVIF comes first, since it
  // is the smaller of the two, and WebP serves a browser without it. A rendered picture is reused for a day, so a
  // file replaced under the same name appears within one.
  images: {
    deviceSizes: [640, 828, 1080, 1280, 1920, 2304],
    imageSizes: [384],
    formats: ['image/avif', 'image/webp'],
    minimumCacheTTL: 86_400,
    localPatterns: [
      {
        pathname: '/api/media/file/**',
      },
    ],
  },
  turbopack: {
    root: path.resolve(dirname),
  },
}

export default withPayload(nextConfig, { devBundleServerPackages: false })
