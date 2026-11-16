'use client'

import { usePathname } from 'next/navigation'

import type { NavigationLink } from '@/lib/views'
import { NavigationList } from './navigation-list'

/** What the site's navigation shows. */
export interface SiteNavigationProperties {
  readonly label: string
  readonly links: readonly NavigationLink[]
}

/**
 * The site's main navigation, in the header. A layout renders it once for every page beneath it and never
 * learns which page that is, so this client component reads the path from the browser's address.
 * @param properties the label and the links.
 * @returns the navigation, its link to the page shown marked as current.
 */
export function SiteNavigation(properties: Readonly<SiteNavigationProperties>) {
  const { label, links } = properties
  const currentPath: string = usePathname()

  return <NavigationList currentPath={currentPath} label={label} links={links} />
}
