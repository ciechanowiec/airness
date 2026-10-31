// The stylesheets the harness lays over a page it measures: motion stilled, text spacing stressed, failing
// elements outlined. Each is adopted as a constructed sheet rather than written as a style element, because
// a content security policy governs the styles a document parses and not the ones script builds. A page
// whose policy refuses inline style is still measured, and its policy stays enforced while it is, so an
// inline script or style the page itself writes against that policy still logs the violation the sweep
// reports as a page error. Turning the policy off for the browser would have hidden exactly that.
import type { Page } from '@playwright/test'

interface PageStyleSheet {
  readonly replaceSync: (text: string) => void
}

declare const CSSStyleSheet: new () => PageStyleSheet
declare const document: { adoptedStyleSheets: readonly PageStyleSheet[] }

// Everything this function needs is inside it: Playwright ships a page function to the browser as its own
// source text.
const adoptInPage = (css: string): void => {
  const sheet: PageStyleSheet = new CSSStyleSheet()
  sheet.replaceSync(css)
  // eslint-disable-next-line functional/immutable-data -- a constructed sheet applies only once the document adopts it, and adopting is this assignment
  document.adoptedStyleSheets = [...document.adoptedStyleSheets, sheet]
}

/**
 * Apply a stylesheet of the harness's own to the page until its next navigation.
 * @param page the page.
 * @param css the stylesheet.
 */
export const adoptStyle = async (page: Page, css: string): Promise<void> => {
  await page.evaluate(adoptInPage, css)
}
