import type { Metadata } from 'next'
import type React from 'react'
import './styles.css'

export const metadata: Metadata = {
  description: 'Starter, a Payload CMS application on Next.js.',
  title: 'Starter',
}

export default function RootLayout(properties: Readonly<{ children: React.ReactNode }>) {
  const { children } = properties

  return (
    // suppressHydrationWarning: browser extensions (e.g. LanguageTool adds data-lt-installed) mutate
    // the <html> element before React hydrates. This suppresses the resulting attribute-mismatch noise
    // for this element only; real mismatches in <body> and below are still reported.
    <html lang="en" suppressHydrationWarning={true}>
      <body>
        <main>{children}</main>
      </body>
    </html>
  )
}
