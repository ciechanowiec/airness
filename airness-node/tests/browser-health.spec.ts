import { describe, expect, it } from 'vitest'
import {
  allowedOrigins,
  type ConsoleEntry,
  consolePageError,
  isAllowedRequest,
  isFailedStatus,
  pageOwnEntries,
  reportedText,
  SCREENSHOT_BEGINS,
  SCREENSHOT_ENDS,
} from '@/browser-health'

const ORIGINS: readonly string[] = allowedOrigins(['http://localhost:3100', 'http://localhost:3000/', 'not a url'])

describe('the browser network guard', () => {
  it('derives each allowed origin once from the server URLs', () => {
    expect(ORIGINS).toEqual(['http://localhost:3100', 'http://localhost:3000'])
  })

  it('lets the application, its auxiliary servers and in-browser URLs through', () => {
    expect(
      [
        'http://localhost:3100/_next/app.js',
        'http://localhost:3000/api/media/a.png',
        'data:image/png;base64,AA',
        'blob:x',
      ].map((url: string): boolean => isAllowedRequest(url, ORIGINS)),
    ).toEqual([true, true, true, true])
  })

  it('blocks a third-party host, another port and an unparsable URL', () => {
    expect(
      ['https://fonts.googleapis.com/css2', 'http://localhost:4000/', '::'].map((url: string): boolean =>
        isAllowedRequest(url, ORIGINS),
      ),
    ).toEqual([false, false, false])
  })
})

describe('console page errors', () => {
  it('reports every console.error and every hydration warning', () => {
    expect([
      consolePageError({ type: 'error', text: 'TypeError: x is undefined' }),
      consolePageError({
        type: 'warning',
        text: 'A tree hydrated but some attributes did not match',
      }),
    ]).toEqual([
      'console.error: TypeError: x is undefined',
      'hydration warning: A tree hydrated but some attributes did not match',
    ])
  })

  it('leaves a failed resource to the failed-request rule, and ignores other messages', () => {
    expect([
      consolePageError({
        type: 'error',
        text: 'Failed to load resource: the server responded with a status of 404',
      }),
      consolePageError({ type: 'warning', text: 'deprecated option' }),
      consolePageError({ type: 'log', text: 'ready' }),
    ]).toEqual([undefined, undefined, undefined])
  })

  it('cuts a long message to its first line', () => {
    expect([reportedText(` ${'a'.repeat(301)}\nstack`), reportedText('short\nstack')]).toEqual([
      `${'a'.repeat(300)}...`,
      'short',
    ])
  })
})

describe('failed responses', () => {
  it('fails a 4xx or 5xx status and passes a redirect', () => {
    expect([404, 500, 302, 200].map((status: number): boolean => isFailedStatus(status))).toEqual([
      true,
      true,
      false,
      false,
    ])
  })
})

const BEGINS: ConsoleEntry = { type: 'info', text: SCREENSHOT_BEGINS }
const ENDS: ConsoleEntry = { type: 'info', text: SCREENSHOT_ENDS }

// WebKit words the refusal by whether the policy names a style-src directive or falls back to default-src.
const DEFAULT_REFUSAL: ConsoleEntry = {
  type: 'error',
  text: "Refused to apply a stylesheet because its hash, its nonce, or 'unsafe-inline' appears in neither the style-src directive nor the default-src directive of the Content Security Policy.",
}
const STYLE_SRC_REFUSAL: ConsoleEntry = {
  type: 'error',
  text: "Refused to apply a stylesheet because its hash, its nonce, or 'unsafe-inline' does not appear in the style-src directive of the Content Security Policy.",
}

describe('the console messages a page answers for', () => {
  it("drops the refusal of the style each of the sweep's own screenshots adds, in either wording", () => {
    expect(pageOwnEntries([BEGINS, DEFAULT_REFUSAL, ENDS, BEGINS, STYLE_SRC_REFUSAL, ENDS], 'chromium')).toEqual([])
  })

  it('keeps a refusal the page caused before, after or between screenshots', () => {
    expect(
      pageOwnEntries([DEFAULT_REFUSAL, BEGINS, ENDS, STYLE_SRC_REFUSAL, BEGINS, ENDS, DEFAULT_REFUSAL], 'chromium'),
    ).toEqual([DEFAULT_REFUSAL, STYLE_SRC_REFUSAL, DEFAULT_REFUSAL])
  })

  it('keeps every other message logged while a screenshot is taken', () => {
    const thrown: ConsoleEntry = { type: 'error', text: 'TypeError: x is undefined' }
    const warned: ConsoleEntry = { type: 'warning', text: DEFAULT_REFUSAL.text }
    expect(pageOwnEntries([BEGINS, thrown, warned, ENDS], 'chromium')).toEqual([thrown, warned])
  })

  it('leaves every stylesheet refusal in WebKit to Chromium, because WebKit refuses its own select stylesheet alike', () => {
    expect(pageOwnEntries([DEFAULT_REFUSAL, STYLE_SRC_REFUSAL], 'webkit')).toEqual([])
  })

  it('keeps every other message WebKit logs', () => {
    const thrown: ConsoleEntry = { type: 'error', text: 'TypeError: x is undefined' }
    const warned: ConsoleEntry = { type: 'warning', text: DEFAULT_REFUSAL.text }
    expect(pageOwnEntries([thrown, DEFAULT_REFUSAL, warned], 'webkit')).toEqual([thrown, warned])
  })

  it('takes a marker only as the info message the sweep logs, so a page logging its text otherwise opens no window', () => {
    const logged: ConsoleEntry = { type: 'log', text: SCREENSHOT_BEGINS }
    expect(pageOwnEntries([logged, DEFAULT_REFUSAL], 'chromium')).toEqual([logged, DEFAULT_REFUSAL])
  })
})
