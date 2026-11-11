// What a page may not do while it loads and settles with nobody touching it (rule `unrequested-change`):
// open a window, or take the reader to another page. WCAG 3.2.5 asks that a change of context happens only
// on the reader's request, and 3.2.1 that arriving somewhere does not set one off. The browser side records
// every top-level navigation and every window the page opens while it is held still; everything that decides
// a verdict is here.
//
// WHY THE PATH. An HTTP redirect is answered before the first document exists, so the document the browser
// loads is already the destination, and nothing here sees it. Once that document has loaded, a change of hash
// or of query, or a history entry a router writes for the same path, leaves the reader on the page they asked
// for; only a different path - by `location`, by a router's `replace` or `push` after it mounted, by a link
// the page clicks for itself - takes them somewhere they did not ask to go. A refresh by `<meta>` is axe's
// `meta-refresh-no-exceptions`, so it is not judged twice.

/** What the browser recorded while a page loaded and settled. */
export interface PageChanges {
  /** The address the page was loaded at, after any HTTP redirect. */
  readonly loaded: string
  /** Every top-level address the document committed to, in order, the initial load among them. */
  readonly navigations: readonly string[]
  /** The address of every window the page opened, as it was when it opened. */
  readonly popups: readonly string[]
}

const parsed = (address: string): URL | undefined => {
  try {
    return new URL(address)
  } catch {
    return undefined
  }
}

// A trailing slash names the same page, so a router that adds or drops one has not moved the reader.
const pageOf = (address: string): string | undefined => {
  const url: URL | undefined = parsed(address)
  if (url === undefined) {
    return undefined
  }
  const path: string = url.pathname.replace(/\/$/u, '')
  return `${url.origin}${path === '' ? '/' : path}`
}

// Where the page went, named the way a reader would recognise it: the path on the page's own origin.
const destinationOf = (address: string, loaded: string): string => {
  const url: URL | undefined = parsed(address)
  return url !== undefined && url.origin === parsed(loaded)?.origin ? `${url.pathname}${url.search}` : address
}

const BLANK: string = 'about:blank'

// The first navigation to another page after the loaded one committed, which is the change the reader did not
// ask for. Navigations before the loaded page committed belong to the page that was there before it.
const unrequestedNavigation = (changes: PageChanges): string | undefined => {
  const loaded: string | undefined = pageOf(changes.loaded)
  const start: number = changes.navigations.findIndex((address: string): boolean => pageOf(address) === loaded)
  if (loaded === undefined || start === -1) {
    return undefined
  }
  return changes.navigations.slice(start + 1).find((address: string): boolean => {
    const page: string | undefined = pageOf(address)
    return page !== undefined && page !== loaded
  })
}

/**
 * What is wrong with a page that changed context on its own (rule `unrequested-change`).
 * @param changes what the browser recorded.
 * @returns one message per change: the first move to another page, and every window opened.
 */
export const unrequestedChangeMessages = (changes: PageChanges): readonly string[] => {
  const moved: string | undefined = unrequestedNavigation(changes)
  return [
    ...(moved === undefined
      ? []
      : [`the page moved to ${destinationOf(moved, changes.loaded)} on its own while it loaded, before any input`]),
    ...changes.popups.map((popup: string): string =>
      popup === '' || popup === BLANK
        ? 'the page opened a new window on its own while it loaded, before any input'
        : `the page opened a new window at ${destinationOf(popup, changes.loaded)} on its own while it loaded, ` +
          'before any input',
    ),
  ]
}
