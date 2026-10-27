import type { Metadata } from 'next'
import { headers } from 'next/headers'
import type { ReactNode } from 'react'
import './globals.css'

export const metadata: Metadata = { title: 'Fixture' }

/**
 * The document every page renders in, generated per request to carry the proxy's script nonce.
 * @param props the page the layout wraps.
 * @param props.children the page.
 * @returns the document.
 */
export default async function RootLayout({ children }: Readonly<{ children: ReactNode }>): Promise<ReactNode> {
  await headers()
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  )
}
