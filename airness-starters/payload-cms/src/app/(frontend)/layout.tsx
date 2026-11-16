import type { Metadata } from 'next'
import { connection } from 'next/server'
import type React from 'react'
import './styles.css'

import { SiteFooter } from '@/components/site-footer'
import { SiteHeader } from '@/components/site-header'
import { loadServerEnvironment } from '@/lib/environment'
import { DEFAULT_DESCRIPTION, SITE_NAME } from '@/lib/site'
import type { SiteIdentity } from '@/lib/views'
import { findSiteIdentity } from '@/queries/site'

// Read when a page renders rather than when the module loads, so the origin is the one the server runs at: the
// absolute address every Open Graph image and canonical link is resolved against.
export function generateMetadata(): Metadata {
  return {
    metadataBase: new URL(loadServerEnvironment().serverUrl),
    title: { default: SITE_NAME, template: `%s | ${SITE_NAME}` },
    description: DEFAULT_DESCRIPTION,
  }
}

/** The skip link's target: the page's own content, past the header and its navigation. */
const MAIN_CONTENT_ID: string = 'main'

export default async function RootLayout(properties: Readonly<{ children: React.ReactNode }>) {
  const { children } = properties
  // Every page of the site renders per request rather than at build time: the content security policy admits a
  // script only by the nonce of its own response, which a page rendered at build time cannot carry, and the
  // content lives in a database the build does not reach.
  await connection()
  const site: SiteIdentity = await findSiteIdentity()

  return (
    // suppressHydrationWarning: browser extensions (e.g. LanguageTool adds data-lt-installed) mutate
    // the <html> element before React hydrates. This suppresses the resulting attribute-mismatch noise
    // for this element only; real mismatches in <body> and below are still reported.
    <html lang="en" suppressHydrationWarning={true}>
      <body className="flex min-h-screen flex-col bg-base-100 font-sans text-base-content antialiased">
        <a
          className="sr-only focus:not-sr-only focus:absolute focus:top-2 focus:left-2 focus:z-50 focus:rounded-field focus:bg-base-100 focus:px-4 focus:py-2"
          href={`#${MAIN_CONTENT_ID}`}
        >
          Skip to the content
        </a>
        <SiteHeader navigation={site.navigation} siteName={SITE_NAME} />
        <main className="mx-auto w-full max-w-6xl flex-1 px-4 sm:px-6" id={MAIN_CONTENT_ID}>
          {children}
        </main>
        <SiteFooter note={site.footerNote} siteName={SITE_NAME} />
      </body>
    </html>
  )
}
