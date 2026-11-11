// The sweep's check that a page does not change context on its own while it loads (rule `unrequested-change`).
// A watch is started as the page is loaded and stopped once it has been settled, and judged, with no input at
// all; every top-level navigation and every window the page opened in between is handed to
// `unrequested-change`, which decides. Discovery runs it on every crawled route while it settles, where a page
// that moves is still named by the address it was linked by, since discovery records a page by where it
// settles; the resting pass runs it on every location at every viewport, which holds a page still for long
// enough to see a move a router makes late.
import type { Frame, Page } from '@playwright/test'
import { type BrowserFinding, type FindingPlace, findingAt } from './browser-findings.js'
import { withinLimit } from './deadline.js'
import { engineOf } from './scanner.js'
import { settleForScan } from './settle.js'
import { type PageChanges, unrequestedChangeMessages } from './unrequested-change.js'

/** The rule's name. */
const UNREQUESTED_CHANGE_RULE: string = 'unrequested-change'

/** How long closing a window the page opened may take before the watch gives up on it. */
const POPUP_CLOSE_MS: number = 5000

/** A watch over one page load, stopped once nothing more is done on the page without input. */
export interface ChangeWatch {
  /**
   * Stop watching, close any window the page opened, and hand over what was recorded.
   * @param loaded the address the page was loaded at, after any HTTP redirect.
   * @returns the navigations and windows the page made.
   */
  readonly stop: (loaded: string) => Promise<PageChanges>
}

/**
 * Start recording every top-level navigation of a page and every window it opens.
 * @param page the page, about to be loaded, or already loaded.
 * @param committed the addresses the page has committed to already, when it is watched once it has loaded.
 * @returns the watch.
 */
export const watchUnrequestedChange = (page: Page, committed: readonly string[] = []): ChangeWatch => {
  const navigations: string[] = [...committed]
  const popups: Page[] = []
  const onNavigated = (frame: Frame): void => {
    if (frame === page.mainFrame()) {
      // eslint-disable-next-line functional/immutable-data -- each address the page commits to is kept until the watch stops
      navigations.push(frame.url())
    }
  }
  const onPopup = (popup: Page): void => {
    // eslint-disable-next-line functional/immutable-data -- each window the page opens is kept until the watch stops
    popups.push(popup)
  }
  page.on('framenavigated', onNavigated)
  page.on('popup', onPopup)
  return {
    stop: async (loaded: string): Promise<PageChanges> => {
      page.off('framenavigated', onNavigated)
      page.off('popup', onPopup)
      const opened: readonly string[] = popups.map((popup: Page): string => popup.url())
      // A window left open would hold its own requests and timers for the rest of the pass.
      await Promise.all(
        popups.map(async (popup: Page): Promise<void> => {
          await withinLimit(popup.close(), POPUP_CLOSE_MS)
        }),
      )
      return { loaded, navigations: [...navigations], popups: opened }
    },
  }
}

/**
 * The findings of a watch: the first move to another page, and every window opened.
 * @param changes what the watch recorded.
 * @param place where the page was loaded, reported for the page as a whole, at every viewport alike.
 * @returns the findings.
 */
export const unrequestedChangeFindings = (changes: PageChanges, place: FindingPlace): readonly BrowserFinding[] =>
  unrequestedChangeMessages(changes).map(
    (message: string): BrowserFinding => findingAt({ ...place, viewport: undefined }, UNREQUESTED_CHANGE_RULE, message),
  )

/**
 * Settle a page discovery has just loaded, watching it while it settles, and add what it changed on its own to
 * the crawl's findings. The page is named by the route the crawl followed to it.
 * @param page the page, loaded at the route.
 * @param state the crawl so far.
 * @param route the route the crawl followed.
 * @param loaded the address the page was loaded at, after any HTTP redirect.
 * @returns the crawl, with the findings.
 */
export const settledUnrequested = async <State extends { readonly findings: readonly BrowserFinding[] }>(
  page: Page,
  state: State,
  route: string,
  loaded: string,
): Promise<State> => {
  const watch: ChangeWatch = watchUnrequestedChange(page, [loaded, page.url()])
  await settleForScan(page)
  const changed: readonly BrowserFinding[] = unrequestedChangeFindings(await watch.stop(loaded), {
    location: route,
    state: 'discovery',
    engine: engineOf(page),
    viewport: undefined,
  })
  return { ...state, findings: [...state.findings, ...changed] }
}
