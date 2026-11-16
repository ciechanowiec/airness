import type { Metadata } from 'next'
import { connection } from 'next/server'
import './(frontend)/styles.css'

import { NotFoundMessage } from '@/components/not-found-message'
import { SITE_NAME } from '@/lib/site'

export const metadata: Metadata = {
  title: `Page not found | ${SITE_NAME}`,
}

// The answer to an address no route of the application claims. The site and the admin panel each have a root
// layout of their own, so no layout wraps this page: it draws the whole document itself, with the site's styles.
// It renders per request, as every page of the site does, so its scripts carry the nonce of their response.
export default async function GlobalNotFound() {
  await connection()
  return (
    <html lang="en">
      <body className="bg-base-100 font-sans text-base-content antialiased">
        <main className="mx-auto w-full max-w-6xl px-4 sm:px-6">
          <NotFoundMessage />
        </main>
      </body>
    </html>
  )
}
