// The same page is measured on both CI architectures by the exact browser the sweep runs.
import { writeFileSync } from 'node:fs'
import { type Browser, type Page, type PinnedServer, startPinnedServer } from '#browser/pinned-server'
import { measurePage } from '#harness/layout'

const ARGUMENT_OFFSET: number = 2
const [output]: readonly (string | undefined)[] = process.argv.slice(ARGUMENT_OFFSET)
if (output === undefined) {
  throw new Error('Expected the snapshot file to write')
}
const JSON_INDENT: number = 2
const VIEWPORT: { readonly width: number; readonly height: number } = { width: 390, height: 844 }
const fontsReadyInPage = async (): Promise<void> => {
  await document.fonts.ready
}

const PAGE: string = `<!doctype html><html lang="en"><head><title>Geometry</title><style>
body { margin: 0; font-family: sans-serif; }
main { display: flex; flex-direction: column; gap: 16px; padding: 16px; }
.row { display: flex; gap: 8px; flex-wrap: wrap; }
.row span { padding: 4px 8px; border: 1px solid #333; }
.grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
</style></head><body><main>
<h1>Fixtures and results this weekend</h1>
<p>Saturday brings three matches across two venues, and the late kick-off moves to Sunday afternoon
because the floodlights at the northern ground are being replaced before the cup tie next month.</p>
<div class="row"><span>Home</span><span>Teams</span><span>Fixtures</span>
<span>Standings</span><span>Contact</span></div>
<div class="grid"><p>Played 12</p><p>Won 8</p><p>Drawn 3</p><p>Lost 1</p><p>Goals 27</p><p>Points 27</p></div>
</main></body></html>`

const server: PinnedServer = await startPinnedServer('geometry')
try {
  const browser: Browser = await server.connect('chromium')
  try {
    const page: Page = await browser.newPage({ viewport: VIEWPORT, deviceScaleFactor: 1 })
    await page.setContent(PAGE)
    await page.evaluate(fontsReadyInPage)
    writeFileSync(output, `${JSON.stringify(await measurePage(page, '/geometry'), null, JSON_INDENT)}\n`)
  } finally {
    await browser.close()
  }
} finally {
  server.stop()
}
