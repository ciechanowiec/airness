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
import { test } from '@playwright/test'
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

const CHROMIUM: string = '@airness-chromium'
const ENGINE: string = '@airness-engine'

const SHARDS: readonly Shard[] = Array.from(
  { length: SWEEP_SHARDS },
  (_unused: unknown, index: number): Shard => ({
    index,
    count: SWEEP_SHARDS,
  }),
)

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

// Each pass calls its helper directly in the test body rather than through a table, so the lint rule
// requiring an assertion in every test can see one: the `expect` helpers are what it counts.
test.describe('Airness browser sweep', () => {
  test(DISCOVERY_TITLE, { tag: '@airness-discovery' }, async ({ page }) => {
    await expectDiscoveredSite(page)
  })

  for (const shard of SHARDS) {
    test(partTitle(RESTING_TITLE, shard), { tag: CHROMIUM }, async ({ page }) => {
      await expectRestingRules(page, shard)
    })
  }

  for (const shard of SHARDS) {
    test(partTitle(INTERACTION_TITLE, shard), { tag: CHROMIUM }, async ({ page }) => {
      await expectInteractionStates(page, shard)
    })
  }

  for (const shard of SHARDS) {
    test(partTitle(DISCLOSURE_TITLE, shard), { tag: CHROMIUM }, async ({ page }) => {
      await expectDisclosureStates(page, shard)
    })
  }

  for (const shard of SHARDS) {
    test(partTitle(TEXT_STRESS_TITLE, shard), { tag: CHROMIUM }, async ({ page }) => {
      await expectTextStress(page, shard)
    })
  }

  for (const shard of SHARDS) {
    test(partTitle(WIDTH_TITLE, shard), { tag: CHROMIUM }, async ({ page }) => {
      await expectWidthSweep(page, shard)
    })
  }

  for (const shard of SHARDS) {
    test(partTitle(DARK_TITLE, shard), { tag: CHROMIUM }, async ({ page }) => {
      await expectDarkScheme(page, shard)
    })
  }

  for (const shard of SHARDS) {
    test(partTitle(ENGINE_TITLE, shard), { tag: ENGINE }, async ({ page }) => {
      await expectEngineRules(page, shard)
    })
  }

  test('every exemption excuses something, and every pass finished', { tag: '@airness-summary' }, async () => {
    await expectSweepSummary()
  })
})

// A describe of its own, because a context's touch and mobile emulation is fixed when the context opens.
test.describe('Airness browser sweep on a touch phone', () => {
  test.use({ hasTouch: true, isMobile: true })

  for (const shard of SHARDS) {
    test(partTitle(SCROLLING_TITLE, shard), { tag: CHROMIUM }, async ({ page }) => {
      await expectScrolling(page, shard)
    })
  }
})
