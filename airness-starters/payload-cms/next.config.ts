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
  images: {
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
