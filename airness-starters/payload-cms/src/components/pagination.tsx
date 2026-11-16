import { ArrowLeft, ArrowRight } from 'lucide-react'
import Link from 'next/link'

import { type PageLink, type PageLinks, pageLinks } from '@/lib/pagination'

/** Which page of which list the control shows. */
export interface PaginationProperties {
  /** The list's path, such as `/posts`. */
  readonly basePath: string
  /** The page shown, from 1. */
  readonly current: number
  /** How many pages the list has. */
  readonly total: number
}

const STEP_CLASS: string = 'btn btn-ghost btn-sm min-h-10 gap-2'

/**
 * The links between the pages of a list: the previous and next pages and every page by number, the page shown
 * marked rather than linked. A list of one page needs no control, so none is drawn.
 * @param properties the list's path, the page shown and the number of pages.
 * @returns the control, or nothing for a list of one page.
 */
export function Pagination(properties: Readonly<PaginationProperties>) {
  const { basePath, current, total } = properties

  if (total <= 1) {
    return null
  }
  const links: PageLinks = pageLinks(basePath, current, total)
  return (
    <nav aria-label="Pages" className="mt-12 flex flex-wrap items-center justify-center gap-2">
      {links.previous === undefined ? null : (
        <Link className={STEP_CLASS} href={links.previous.href}>
          <ArrowLeft aria-hidden="true" className="size-4" />
          Previous page
        </Link>
      )}
      <ul className="flex flex-wrap items-center gap-1">
        {links.pages.map((page: PageLink) => (
          <li key={page.number}>
            {page.number === current ? (
              <span
                aria-current="page"
                className="inline-flex min-h-10 items-center rounded-field bg-primary px-3 font-semibold text-primary-content text-sm"
              >{`Page ${String(page.number)}`}</span>
            ) : (
              <Link className="btn btn-ghost btn-sm min-h-10 min-w-10" href={page.href}>
                {`Page ${String(page.number)}`}
              </Link>
            )}
          </li>
        ))}
      </ul>
      {links.next === undefined ? null : (
        <Link className={STEP_CLASS} href={links.next.href}>
          Next page
          <ArrowRight aria-hidden="true" className="size-4" />
        </Link>
      )}
    </nav>
  )
}
