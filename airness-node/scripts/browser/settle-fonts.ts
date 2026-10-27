import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http'
import { type Browser, type Page, type PinnedServer, startPinnedServer } from '#browser/pinned-server'
// The settle waits for the font of text that appears while it runs, in every pinned engine. The text
// appears after the settle's first wait for fonts has passed, as a story or a revealed panel does, and
// its face answers only after two seconds. Settled, the face must have finished: loaded, or failed for the
// font rule to report, never still unloaded or loading for the rule to report as missing.
//
// Run from the repository root after `pnpm run build`, with the pinned Playwright image present.
import { settleForScan } from '#harness/settle'

// The one browser call the page function below makes; the lint project compiles without the DOM lib.
declare const document: {
  readonly fonts: Iterable<{ readonly family: string; readonly status: string }> | undefined
}

const lateFaceStatusInPage = (): string =>
  Array.from(document.fonts ?? [])
    .filter((face: { readonly family: string }): boolean => face.family.replaceAll('"', '') === 'Late')
    .map((face: { readonly status: string }): string => face.status)
    .join(',')

const FONT_DELAY_MS: number = 2000
// The animation holds the settle while the text appears a tenth of a second in, after the settle's first
// wait for fonts and before it ends.
const PAGE: string =
  '<!doctype html><html lang="en"><title>Late font</title><style>' +
  "@font-face { font-family: Late; src: url('/late.woff2') format('woff2'); } " +
  '.late { font-family: Late, sans-serif; } ' +
  'main { animation: hold 400ms linear; } @keyframes hold { to { opacity: 0.99; } }' +
  '</style><main><p>Ready</p></main><script>setTimeout(() => { const late = document.createElement("p"); ' +
  'late.className = "late"; late.append("Late text"); document.querySelector("main").append(late) }, 100)</script>'
const ENGINES: readonly string[] = ['chromium', 'firefox', 'webkit']
const FINISHED: ReadonlySet<string> = new Set<string>(['loaded', 'error'])

const NOT_FOUND: number = 404
const OK: number = 200

const fontServer: Server = createServer((request: IncomingMessage, response: ServerResponse): void => {
  if (request.url === '/late.woff2') {
    setTimeout((): void => {
      response.writeHead(NOT_FOUND)
      response.end()
    }, FONT_DELAY_MS)
  } else {
    response.writeHead(OK, { 'Content-Type': 'text/html' })
    response.end(PAGE)
  }
})
await new Promise<void>((resolve): void => {
  fontServer.listen(0, '127.0.0.1', resolve)
})
const address: ReturnType<Server['address']> = fontServer.address()
if (address === null || typeof address === 'string') {
  throw new Error('The font server did not bind a TCP port')
}
const origin: string = `http://127.0.0.1:${String(address.port)}/`

const server: PinnedServer = await startPinnedServer('settle-fonts')

const shortfallOf = async (engine: string): Promise<readonly string[]> => {
  const browser: Browser = await server.connect(engine)
  try {
    const page: Page = await browser.newPage()
    await page.goto(origin)
    const outcome: string = await settleForScan(page)
    const status: string = await page.evaluate(lateFaceStatusInPage)
    return outcome === 'settled' && FINISHED.has(status) ? [] : [`${engine} settled ${outcome} with the face ${status}`]
  } finally {
    await browser.close()
  }
}

try {
  const perEngine: readonly (readonly string[])[] = await Promise.all(
    ENGINES.map(async (engine: string): Promise<readonly string[]> => await shortfallOf(engine)),
  )
  const shortfalls: readonly string[] = perEngine.flat()
  if (shortfalls.length > 0) {
    throw new Error(`The settle must wait for the font of text that appears while it runs: ${shortfalls.join('; ')}`)
  }
  console.info('every pinned engine settles with the late text font finished')
} finally {
  server.stop()
  await new Promise<void>((resolve): void => {
    fontServer.close((): void => {
      resolve()
    })
  })
}
