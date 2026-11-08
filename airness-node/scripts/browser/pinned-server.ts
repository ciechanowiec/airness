// The contracts use the exact container launcher and browser protocol the Maven e2e gate uses.
import { type Browser, type BrowserType, chromium, firefox, webkit } from '@playwright/test'
import type { GateResult } from '#harness/gate-result'
import { isStartFailure, type PinnedBrowsers, startPinnedBrowsers } from '#harness/pinned-browsers'
import { type Member, membersOf } from '#harness/project'

export type { Browser, Page } from '@playwright/test'
/** A started server shared by the contracts of one run. */
export interface PinnedServer {
  readonly connect: (engine: string) => Promise<Browser>
  readonly stop: () => void
}
const START_ATTEMPTS: number = 60
const RETRY_MS: number = 1000

const ENGINES: Readonly<Record<string, BrowserType>> = { chromium, firefox, webkit }

/** Start the harness's pinned browser server and connect through its protected endpoint. */
export const startPinnedServer = async (_purpose: string): Promise<PinnedServer> => {
  const member: Member | undefined = membersOf(process.cwd())[0]
  if (member === undefined) {
    throw new Error('The browser contracts need a node project')
  }
  const started: PinnedBrowsers | GateResult = await startPinnedBrowsers(member)
  if (isStartFailure(started)) {
    throw new Error(`${started.summary}: ${started.findings.join('; ')}`)
  }
  return {
    stop: started.stop,
    connect: async (engine: string): Promise<Browser> => {
      const browser: BrowserType | undefined = ENGINES[engine]
      if (browser === undefined) {
        throw new Error(`Unknown browser engine ${engine}`)
      }
      return await connectBrowser(browser, started, 1)
    },
  }
}

const connectBrowser = async (browser: BrowserType, started: PinnedBrowsers, attempt: number): Promise<Browser> => {
  try {
    return await browser.connect(started.endpoint, {
      ...(started.isTunnelled && { exposeNetwork: '<loopback>' }),
    })
  } catch (error: unknown) {
    if (attempt >= START_ATTEMPTS) {
      throw error
    }
    await new Promise<void>((resolve): void => {
      setTimeout(resolve, RETRY_MS)
    })
    return await connectBrowser(browser, started, attempt + 1)
  }
}
