import { describe, expect, it } from 'vitest'
import { EXEMPTIBLE_RULES } from '@/browser-exemptions.js'
import { findLayoutDefects } from '@/layout-defects.js'
import type { LayoutNode, LayoutSnapshot } from '@/layout-snapshot.js'
import { LAYOUT_MINIMUM_GAP } from '@/settings.js'
import { BODY, defectsOf, nodeOf, RED, rect, snapshotOf, TINT } from './helpers/layout-fixture.js'

const attachedTab = (exemption: Partial<LayoutNode>, panelTop: number = 40): readonly LayoutNode[] => [
  BODY,
  nodeOf({
    index: 1,
    parent: 0,
    label: 'div.tab',
    rects: [rect(0, 0, 200, 40)],
    backgroundColor: RED,
    ...exemption,
  }),
  nodeOf({
    index: 2,
    parent: 0,
    label: 'div.panel',
    rects: [rect(0, panelTop, 200, panelTop + 80)],
    backgroundColor: TINT,
  }),
]

const ATTACHED: Partial<LayoutNode> = {
  exemption: 'attached',
  exemptionReason: 'the tab joins its panel',
}

describe('an edge shared on purpose', () => {
  it('passes a touching pair when one of them declares it attached, with a reason', () => {
    expect(defectsOf(attachedTab(ATTACHED))).toEqual([])
  })

  it('fails an exemption that states no reason', () => {
    expect(defectsOf(attachedTab({ exemption: 'attached' }))).toEqual([
      `[exemption] /en/account/verify @390x844 (chromium, resting): ` +
        'layout exemption without a reason on div.tab; state it in data-airness-layout-reason',
    ])
  })

  it('fails a value the harness does not know, and does not excuse the pair', () => {
    expect(defectsOf(attachedTab({ exemption: 'joined', exemptionReason: 'tab' }))).toEqual([
      '[attached] /en/account/verify @390x844 (chromium, resting): touching boxes (gap 0px): div.tab  <->  div.panel',
      `[exemption] /en/account/verify @390x844 (chromium, resting): ` +
        `unknown rule "joined" in data-airness-layout on div.tab; ` +
        `the exemptible rules are ${EXEMPTIBLE_RULES.join(', ')}`,
    ])
  })

  it('fails an exemption that excuses nothing', () => {
    expect(defectsOf(attachedTab(ATTACHED, 60))).toEqual([
      '[exemption] /en/account/verify (chromium, every): stale layout exemption "attached" on div.tab: ' +
        'it excuses nothing in any pass, viewport, engine or state; remove it from data-airness-layout',
    ])
  })

  // A tab bar that sits against its panel on a phone and apart from it on a desktop is the ordinary
  // shape of a responsive layout: the exemption is needed at one width and is not stale.
  it('is not stale when it excuses a pair at one viewport and nothing at another', () => {
    const phone: LayoutSnapshot = snapshotOf(attachedTab(ATTACHED))
    const desktop: LayoutSnapshot = snapshotOf(attachedTab(ATTACHED, 60), {
      width: 1280,
      height: 800,
    })
    expect(findLayoutDefects([phone, desktop], LAYOUT_MINIMUM_GAP)).toEqual([])
  })

  it('reports it once when it is stale at every viewport', () => {
    const phone: LayoutSnapshot = snapshotOf(attachedTab(ATTACHED, 60))
    const desktop: LayoutSnapshot = snapshotOf(attachedTab(ATTACHED, 60), {
      width: 1280,
      height: 800,
    })
    expect(findLayoutDefects([phone, desktop], LAYOUT_MINIMUM_GAP)).toEqual([
      '[exemption] /en/account/verify (chromium, every): stale layout exemption "attached" on div.tab: ' +
        'it excuses nothing in any pass, viewport, engine or state; remove it from data-airness-layout',
    ])
  })
})
