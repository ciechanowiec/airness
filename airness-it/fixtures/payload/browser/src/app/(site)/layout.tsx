import { headers } from 'next/headers'
import type { Metadata } from 'next'
import type { ReactNode } from 'react'

export const metadata: Metadata = { title: 'Payload browser contract' }

export default async function Layout({ children }: { readonly children: ReactNode }): Promise<React.JSX.Element> {
  await headers()
  return (
    <html lang="en">
      <body style={{ fontFamily: 'sans-serif', color: '#111', background: '#fff' }}>{children}</body>
    </html>
  )
}
