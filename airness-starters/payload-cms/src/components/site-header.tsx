import { Feather } from 'lucide-react'
import Link from 'next/link'

import type { NavigationLink } from '@/lib/views'
import { SiteNavigation } from './site-navigation'

/** What the header shows. */
export interface SiteHeaderProperties {
  readonly siteName: string
  readonly navigation: readonly NavigationLink[]
}

/**
 * The band at the top of every page: the site's name, linking the home page, and the main navigation.
 * @param properties the site's name and its navigation.
 * @returns the header.
 */
export function SiteHeader(properties: Readonly<SiteHeaderProperties>) {
  const { siteName, navigation } = properties

  return (
    <header className="border-base-300 border-b">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-x-6 gap-y-2 px-4 py-3 sm:px-6">
        <Link className="inline-flex min-h-10 items-center gap-2 font-semibold font-serif text-xl" href="/">
          <Feather aria-hidden="true" className="size-5 text-primary" />
          {siteName}
        </Link>
        <SiteNavigation label="Main" links={navigation} />
      </div>
    </header>
  )
}
