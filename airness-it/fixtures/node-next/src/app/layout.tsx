import type { Metadata } from 'next'
import type { ReactNode } from 'react'
import './globals.css'

export const metadata: Metadata = { title: 'Fixture' }

/**
 * The document every page renders in.
 * @param props the page the layout wraps.
 * @param props.children the page.
 * @returns the document.
 */
export default function RootLayout({ children }: Readonly<{ children: ReactNode }>): ReactNode {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  )
}
