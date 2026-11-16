import Link from 'next/link'

import type { NavigationLink } from '@/lib/views'

/** What a navigation shows. */
export interface NavigationListProperties {
  /** The landmark's name, which tells two navigations of one page apart, such as `Main` and `Footer`. */
  readonly label: string
  readonly links: readonly NavigationLink[]
  /** The path of the page shown, whose link is marked as the current page. */
  readonly currentPath: string
}

/**
 * A navigation landmark: a list of links, the one to the page shown marked as current, as a screen reader
 * announces it and the harness checks it.
 * @param properties the label, the links and the current path.
 * @returns the navigation.
 */
export function NavigationList(properties: Readonly<NavigationListProperties>) {
  const { label, links, currentPath } = properties

  return (
    <nav aria-label={label}>
      <ul className="flex flex-wrap items-center gap-1">
        {links.map((link: NavigationLink) => (
          <li key={link.href}>
            <Link
              aria-current={link.href === currentPath ? 'page' : undefined}
              className="inline-flex min-h-10 items-center rounded-field px-3 font-medium text-sm hover:bg-base-200 aria-[current=page]:bg-base-200 aria-[current=page]:text-primary"
              href={link.href}
            >
              {link.label}
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  )
}
