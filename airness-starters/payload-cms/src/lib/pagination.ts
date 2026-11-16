// Page numbers of a paginated list, read from the `page` query parameter and written back into links.

/** How many posts one page of a list shows. */
export const POSTS_PER_PAGE: number = 6

const FIRST_PAGE: number = 1
const PAGE_NUMBER: RegExp = /^[1-9]\d{0,5}$/u

/**
 * The page a `page` query parameter asks for.
 *
 * Anything but one positive whole number, such as a missing, repeated or malformed parameter, asks for the first
 * page, so a hand-edited address still shows a list rather than an error.
 * @param parameter the parameter as Next.js hands it to a page.
 * @returns the page number, from 1.
 */
export const pageNumberFrom = (parameter: string | readonly string[] | undefined): number =>
  typeof parameter === 'string' && PAGE_NUMBER.test(parameter) ? Number(parameter) : FIRST_PAGE

/**
 * The address of one page of a list. The first page has no parameter, so it has one address only.
 * @param basePath the list's path, such as `/posts`.
 * @param page the page number, from 1.
 * @returns the address.
 */
const pageHref = (basePath: string, page: number): string =>
  page === FIRST_PAGE ? basePath : `${basePath}?page=${String(page)}`

/** One page of a list, as the pagination control links it. */
export interface PageLink {
  readonly number: number
  readonly href: string
}

/** The links of a pagination control. */
export interface PageLinks {
  readonly previous?: PageLink
  readonly next?: PageLink
  readonly pages: readonly PageLink[]
}

const pageLink = (basePath: string, page: number): PageLink => ({ number: page, href: pageHref(basePath, page) })

/**
 * Every link a pagination control shows for a list of a known length.
 * @param basePath the list's path.
 * @param current the page shown, from 1.
 * @param total how many pages the list has.
 * @returns the link to each page, and to the previous and next ones where they exist.
 */
export const pageLinks = (basePath: string, current: number, total: number): PageLinks => ({
  ...(current > FIRST_PAGE && { previous: pageLink(basePath, current - 1) }),
  ...(current < total && { next: pageLink(basePath, current + 1) }),
  pages: Array.from({ length: total }, (_unused: unknown, index: number): PageLink => pageLink(basePath, index + 1)),
})
