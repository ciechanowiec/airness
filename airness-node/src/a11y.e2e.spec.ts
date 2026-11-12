// The browser sweep discovers what to scan from the running application - every route reachable from
// the home page, its `hreflang` alternates and its `sitemap.xml`, every story an auxiliary Storybook
// serves, every breakpoint and colour scheme its stylesheets declare - and judges each one by the same
// rules a project's own specs reach through `expectSweptPage`. A page, story, breakpoint or locale is
// therefore checked the moment it exists, with no test to add.
//
// Discovery runs first, once, in Chromium, and writes what it found for every pass; its problems
// are recorded rather than failing it, because every pass depends on it. Each pass is split into
// shards, one test each, and the shards run on parallel workers while the project's own specs keep to
// one. Each shard has its own budget, derived from the work discovery found, and reports every defect
// it finds rather than stopping at the first. The summary runs last, whatever failed before it: it
// judges discovery's problems and the exemptions no pass used, reports a shard that did not finish,
// and prints what was covered. Under `mvn verify -Pextended` the sweep also runs in Firefox and WebKit,
// where the engine pass covers one route per template and one story per component; `mvn verify -Pextended,delta` sweeps only the routes and stories the change reaches, in Chromium.
// The scrolling pass crosses one route per template and one story per component on a touch phone, so its
// context emulates one: a coarse pointer without hover, which a page that scrolls natively on touch keys on.
// A member holding `tests/e2e/sign-in.setup.ts` is swept a second time in the session that setup left: the
// same discovery and passes, each test tagged as signed in, which keeps its files apart from the anonymous
// visit's. The setup runs before discovery, and the storage state reaches these tests alone, so the
// harness's, the stack layers' and the project's own specifications still start without a session.
import { existsSync } from 'node:fs'
import { expect, test } from '@playwright/test'
import { projectDirectory } from './project-settings.js'
import {
  expectDarkScheme,
  expectDisclosureStates,
  expectDiscoveredSite,
  expectEngineRules,
  expectInteractionStates,
  expectRestingRules,
  expectScrolling,
  expectSweepSummary,
  expectTextStress,
  expectWidthSweep,
  type Shard,
  SWEEP_SHARDS,
} from './sweep.js'
import {
  e2eOutputDirectory,
  hasSignedInVisitor,
  SIGN_IN_SETUP,
  SIGNED_IN_TAG,
  sessionFile,
  type Visitor,
} from './sweep-visitors.js'

const CHROMIUM: string = '@airness-chromium'
const ENGINE: string = '@airness-engine'
const DISCOVERY: string = '@airness-discovery'

const SHARDS: readonly Shard[] = Array.from(
  { length: SWEEP_SHARDS },
  (_unused: unknown, index: number): Shard => ({
    index,
    count: SWEEP_SHARDS,
  }),
)

const SESSION: string = sessionFile(e2eOutputDirectory(projectDirectory))

const DISCOVERY_TITLE: string = 'discovers every route, story, breakpoint and colour scheme'
const RESTING_TITLE: string = 'every page passes the resting rules at every viewport'
const INTERACTION_TITLE: string = 'every control passes in hover and keyboard focus'
const DISCLOSURE_TITLE: string = 'every disclosure passes open and answers the keyboard'
const TEXT_STRESS_TITLE: string = 'text survives WCAG spacing and doubled size'
const WIDTH_TITLE: string = 'layout holds across the whole width range'
const DARK_TITLE: string = 'every page passes in its dark scheme'
const SCROLLING_TITLE: string = 'every sampled page rests where it is scrolled and reveals what it brings into view'
const ENGINE_TITLE: string = 'every sampled page passes in this engine'

const partTitle = (title: string, shard: Shard): string =>
  `${title} (part ${String(shard.index + 1)} of ${String(shard.count)})`

// The signed-in tests carry a second tag, which the passes read to keep the visit's files apart.
const tagsOf = (tag: string, visitor: Visitor): string[] => (visitor === 'signed-in' ? [tag, SIGNED_IN_TAG] : [tag])

// Each pass calls its helper directly in the test body rather than through a table, so the lint rule
// requiring an assertion in every test can see one: the `expect` helpers are what it counts.
const desktopTests = (visitor: Visitor): void => {
  test(DISCOVERY_TITLE, { tag: tagsOf(DISCOVERY, visitor) }, async ({ page }) => {
    await expectDiscoveredSite(page)
  })

  for (const shard of SHARDS) {
    test(partTitle(RESTING_TITLE, shard), { tag: tagsOf(CHROMIUM, visitor) }, async ({ page }) => {
      await expectRestingRules(page, shard)
    })
  }

  for (const shard of SHARDS) {
    test(partTitle(INTERACTION_TITLE, shard), { tag: tagsOf(CHROMIUM, visitor) }, async ({ page }) => {
      await expectInteractionStates(page, shard)
    })
  }

  for (const shard of SHARDS) {
    test(partTitle(DISCLOSURE_TITLE, shard), { tag: tagsOf(CHROMIUM, visitor) }, async ({ page }) => {
      await expectDisclosureStates(page, shard)
    })
  }

  for (const shard of SHARDS) {
    test(partTitle(TEXT_STRESS_TITLE, shard), { tag: tagsOf(CHROMIUM, visitor) }, async ({ page }) => {
      await expectTextStress(page, shard)
    })
  }

  for (const shard of SHARDS) {
    test(partTitle(WIDTH_TITLE, shard), { tag: tagsOf(CHROMIUM, visitor) }, async ({ page }) => {
      await expectWidthSweep(page, shard)
    })
  }

  for (const shard of SHARDS) {
    test(partTitle(DARK_TITLE, shard), { tag: tagsOf(CHROMIUM, visitor) }, async ({ page }) => {
      await expectDarkScheme(page, shard)
    })
  }

  for (const shard of SHARDS) {
    test(partTitle(ENGINE_TITLE, shard), { tag: tagsOf(ENGINE, visitor) }, async ({ page }) => {
      await expectEngineRules(page, shard)
    })
  }
}

const phoneTests = (visitor: Visitor): void => {
  for (const shard of SHARDS) {
    test(partTitle(SCROLLING_TITLE, shard), { tag: tagsOf(CHROMIUM, visitor) }, async ({ page }) => {
      await expectScrolling(page, shard)
    })
  }
}

// A session the setup did not leave would fail every signed-in test on a missing file; this names the cause.
const expectSession = (): void => {
  expect(existsSync(SESSION), `${SIGN_IN_SETUP} finished without calling saveSweepSession`).toBe(true)
}

test.describe('Airness browser sweep', () => {
  desktopTests('anonymous')

  test('every exemption excuses something, and every pass finished', { tag: '@airness-summary' }, async () => {
    await expectSweepSummary()
  })
})

// A describe of its own, because a context's touch and mobile emulation is fixed when the context opens.
test.describe('Airness browser sweep on a touch phone', () => {
  test.use({ hasTouch: true, isMobile: true })
  phoneTests('anonymous')
})

if (hasSignedInVisitor(projectDirectory, existsSync)) {
  test.describe('Airness browser sweep, signed in', () => {
    test.use({ storageState: SESSION })
    test.beforeAll(expectSession)
    desktopTests('signed-in')
  })

  test.describe('Airness browser sweep on a touch phone, signed in', () => {
    test.use({ hasTouch: true, isMobile: true, storageState: SESSION })
    test.beforeAll(expectSession)
    phoneTests('signed-in')
  })
}
