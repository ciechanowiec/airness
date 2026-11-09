// Runtime health: what the browser itself reports while a page loads and is driven.
//
// A page can pass every geometric and accessibility rule while it throws, fails to load a stylesheet,
// or quietly depends on a third-party host. The browser reports each of these as an event, and these
// rules turn each event into a finding. The guard rule is the reason the suite needs no internet: any
// request outside the application and its declared auxiliary servers is blocked and reported, so a page
// cannot pass on one machine and fail on another for a reason outside the repository.

/** No uncaught error, `console.error` or hydration warning while the page loads or is driven. */
export const PAGE_ERROR_RULE: string = 'page-error'

/** No subresource answering 4xx or 5xx, and no request the network guard blocked. */
export const FAILED_REQUEST_RULE: string = 'failed-request'

/** Every crawled internal link answers. */
export const BROKEN_LINK_RULE: string = 'broken-link'

/** The page settled and its geometry held still between two measurements. */
export const PAGE_NOT_STABLE_RULE: string = 'page-not-stable'

const LOCAL_SCHEMES: ReadonlySet<string> = new Set<string>(['data:', 'blob:', 'about:'])

const originOf = (url: string): string | undefined => {
  try {
    return new URL(url).origin
  } catch {
    return undefined
  }
}

/**
 * The origins a page may load from: the application and every declared auxiliary server.
 * @param urls the server URLs.
 * @returns each origin once.
 */
export const allowedOrigins = (urls: readonly string[]): readonly string[] => [
  ...new Set<string>(urls.flatMap((url: string): readonly string[] => [originOf(url) ?? ''].filter(Boolean))),
]

/**
 * Whether the network guard lets a request through.
 * @param url the requested URL.
 * @param origins the allowed origins.
 * @returns true for an allowed origin and for a URL that never leaves the browser.
 */
export const isAllowedRequest = (url: string, origins: readonly string[]): boolean => {
  const scheme: string = /^[a-z][a-z\d+.-]*:/iu.exec(url)?.[0]?.toLowerCase() ?? ''
  if (LOCAL_SCHEMES.has(scheme)) {
    return true
  }
  const origin: string | undefined = originOf(url)
  return origin !== undefined && origins.includes(origin)
}

/** A console message as the browser reports it. */
export interface ConsoleEntry {
  readonly type: string
  readonly text: string
}

// The browser logs a failed subresource as a console error too. It is reported once, as the failed
// request it describes, with its URL and status.
const RESOURCE_FAILURE: RegExp = /^Failed to load resource\b/u

const HYDRATION: RegExp = /hydrat/iu

/**
 * The console messages that are page errors: every `console.error`, and any warning about hydration.
 * @param entry the console message.
 * @returns the message to report, or undefined when it is not a page error.
 */
export const consolePageError = (entry: ConsoleEntry): string | undefined => {
  if (entry.type === 'error' && !RESOURCE_FAILURE.test(entry.text)) {
    return `console.error: ${entry.text}`
  }
  return entry.type === 'warning' && HYDRATION.test(entry.text) ? `hydration warning: ${entry.text}` : undefined
}

/** What the sweep logs into a page, as an info message, just before a screenshot of its own. */
export const SCREENSHOT_BEGINS: string = 'airness: the sweep takes a screenshot'

/** What the sweep logs into a page, as an info message, once that screenshot is taken. */
export const SCREENSHOT_ENDS: string = 'airness: the sweep took a screenshot'

const MARKER_TYPE: string = 'info'

// Playwright's WebKit driver appends a style element of its own before every screenshot, so the CSS
// animations it captures are in step, and a content security policy that admits no inline style refuses it.
// WebKit logs that refusal as an error of the page, with the same text and location as a refusal of a style
// the page itself added, so only when it was logged tells the two apart. WebKit words the refusal one way when
// the policy names a style-src directive and another when it falls back to default-src.
const STYLE_REFUSAL: string = "Refused to apply a stylesheet because its hash, its nonce, or 'unsafe-inline'"

interface ScreenshotWindow {
  readonly isOpen: boolean
  readonly kept: readonly ConsoleEntry[]
}

const isMarker = (entry: ConsoleEntry, text: string): boolean => entry.type === MARKER_TYPE && entry.text === text

/**
 * The console messages of the page itself, in the order it logged them: every message but the sweep's own
 * screenshot markers and the stylesheet refusals logged between a pair of them, which the screenshot itself
 * caused. A refusal the page caused at any other moment, and every other message, is kept.
 * @param entries the console messages, in the order they were logged.
 * @returns the messages the page answers for.
 */
export const pageOwnEntries = (entries: readonly ConsoleEntry[]): readonly ConsoleEntry[] =>
  entries.reduce(
    (window: ScreenshotWindow, entry: ConsoleEntry): ScreenshotWindow => {
      if (isMarker(entry, SCREENSHOT_BEGINS) || isMarker(entry, SCREENSHOT_ENDS)) {
        return { isOpen: isMarker(entry, SCREENSHOT_BEGINS), kept: window.kept }
      }
      const isScreenshotStyle: boolean = window.isOpen && entry.type === 'error' && entry.text.startsWith(STYLE_REFUSAL)
      return { isOpen: window.isOpen, kept: isScreenshotStyle ? window.kept : [...window.kept, entry] }
    },
    { isOpen: false, kept: [] },
  ).kept

/** The longest stretch of a browser message a finding repeats. */
const MAX_MESSAGE: number = 300

/**
 * A browser message cut to a length a report line can carry.
 * @param text the message.
 * @returns its first line, at most 300 characters.
 */
export const reportedText = (text: string): string => {
  const [firstLine = ''] = text.trim().split('\n', 1)
  return firstLine.length > MAX_MESSAGE ? `${firstLine.slice(0, MAX_MESSAGE)}...` : firstLine
}

const HTTP_ERROR: number = 400

/**
 * Whether a response status is a failure.
 * @param status the HTTP status.
 * @returns true for 4xx and 5xx.
 */
export const isFailedStatus = (status: number): boolean => status >= HTTP_ERROR
