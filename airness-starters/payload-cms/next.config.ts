import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { withPayload } from '@payloadcms/next/withPayload'
import type { NextConfig } from 'next'

const __filename: string = fileURLToPath(import.meta.url)
const dirname: string = path.dirname(__filename)

// The widths, in pixels, next/image renders a stored picture at: a card in the grid, a phone's screen at once and
// twice its pixels, a tablet, a laptop and a desktop, and the article cover at twice its 72rem.
const CARD: number = 384
const PHONE: number = 640
const PHONE_TWICE: number = 828
const TABLET: number = 1080
const LAPTOP: number = 1280
const DESKTOP: number = 1920
const COVER_TWICE: number = 2304

type HeaderRules = Awaited<ReturnType<NonNullable<NextConfig['headers']>>>

// Next.js declares the caching of its pages and its build's files, but sends a route handler's answer as the handler
// returns it, and Payload's REST handlers return theirs without Cache-Control: a CDN would then keep an answer that
// belongs to one editor for its own default lifetime. Every answer under /api is kept out of every cache; Next.js
// keeps this value over any a handler returns.
const API_CACHING: HeaderRules = [{ source: '/api/:path*', headers: [{ key: 'Cache-Control', value: 'no-store' }] }]

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
    deviceSizes: [PHONE, PHONE_TWICE, TABLET, LAPTOP, DESKTOP, COVER_TWICE],
    imageSizes: [CARD],
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
  headers: (): Promise<HeaderRules> => Promise.resolve(API_CACHING),
}

export default withPayload(nextConfig, { devBundleServerPackages: false })
