// The page a layout spec judges, built from the numbers a real page produces: whole CSS pixels and
// computed style strings. Shared by the specs of the layout rules, so each states only its own cases.
import { type DeclaredExemption, staleExemptions, type UsedExemption } from '@/browser-exemptions'
import { type BrowserFinding, findingLine, type Viewport } from '@/browser-findings'
import { type JudgedPage, judgeLayout, LAYOUT_RULES } from '@/layout-defects'
import type { LayoutBorder, LayoutNode, LayoutRect, LayoutSnapshot } from '@/layout-snapshot'
import { LAYOUT_MINIMUM_GAP } from '@/settings'

export const TRANSPARENT: string = 'rgba(0, 0, 0, 0)'
export const WHITE: string = 'rgb(255, 255, 255)'
export const TINT: string = 'rgb(236, 253, 245)'
export const RED: string = 'rgb(220, 38, 38)'

export const NO_BORDER: LayoutBorder = {
  width: 0,
  style: 'none',
  color: 'rgb(0, 0, 0)',
}
export const GREEN_BORDER: LayoutBorder = {
  width: 4,
  style: 'solid',
  color: 'rgb(22, 163, 74)',
}

export const rect = (left: number, top: number, right: number, bottom: number): LayoutRect => ({
  left,
  top,
  right,
  bottom,
})

export const nodeOf = (fields: Partial<LayoutNode> & Pick<LayoutNode, 'index' | 'parent'>): LayoutNode => ({
  label: `div#n${String(fields.index)}`,
  tag: 'div',
  rects: [],
  textRects: [],
  lineHeight: 0,
  text: '',
  display: 'block',
  visibility: 'visible',
  position: 'static',
  zIndex: 'auto',
  transform: 'none',
  rotate: 'none',
  backgroundColor: TRANSPARENT,
  backgroundImage: 'none',
  isBackgroundClippedToText: false,
  boxShadow: 'none',
  borders: {
    top: NO_BORDER,
    right: NO_BORDER,
    bottom: NO_BORDER,
    left: NO_BORDER,
  },
  isAriaHidden: false,
  isClipped: false,
  overflowX: 'visible',
  overflowY: 'visible',
  textOverflow: 'clip',
  lineClamp: 'none',
  scrollWidth: 0,
  clientWidth: 0,
  scrollHeight: 0,
  clientHeight: 0,
  title: '',
  ariaLabel: '',
  exemption: undefined,
  exemptionReason: '',
  fontSize: 16,
  fontFamily: 'sans-serif',
  media: undefined,
  ...fields,
})

const PHONE: Viewport = { width: 390, height: 844 }

export const snapshotOf = (nodes: readonly LayoutNode[], viewport: Viewport = PHONE): LayoutSnapshot => ({
  route: '/en/account/verify',
  viewportWidth: viewport.width,
  viewportHeight: viewport.height,
  scrollWidth: viewport.width,
  rootBackground: WHITE,
  nodes,
  fontFaces: [],
})

// The engine and state every layout spec measures a page in.
const ENGINE: string = 'chromium'
const STATE: string = 'resting'

// The rules an element can be exempted from, in the order a malformed exemption lists them.
export const EXEMPTIBLE_RULES: readonly string[] = [
  'attached',
  'overflow',
  'text-overlap',
  'clipped-text',
  'obscured',
  'focus-obscured',
  'text-spacing',
  'text-resize',
  'small-text',
  'image-load',
  'image-distorted',
  'image-upscaled',
  'image-oversized',
  'media-space',
  'unrevealed-content',
  'focus-visible',
  'focus-contrast',
  'focus-order',
  'disclosure-keyboard',
]

/**
 * Every layout defect on one page, across the viewports it was measured at, as report lines: each viewport
 * judged by the geometry rules as the scan judges it, then every exemption that excused nothing at any of them.
 * @param snapshots the same page measured at each viewport.
 * @param minimumGap the smallest gap, in CSS pixels, between a boxed element and its sibling.
 * @returns one line per defect, without duplicates, in the order the viewports were measured.
 */
export const layoutDefectsOf = (snapshots: readonly LayoutSnapshot[], minimumGap: number): readonly string[] => {
  const judged: readonly JudgedPage[] = snapshots.map(
    (snapshot: LayoutSnapshot): JudgedPage =>
      judgeLayout(
        snapshot,
        {
          location: snapshot.route,
          state: STATE,
          engine: ENGINE,
          viewport: { width: snapshot.viewportWidth, height: snapshot.viewportHeight },
        },
        { minimumGap },
      ),
  )
  const stale: readonly BrowserFinding[] = staleExemptions(
    judged.flatMap((page: JudgedPage): readonly DeclaredExemption[] => page.declared),
    judged.flatMap((page: JudgedPage): readonly UsedExemption[] => page.used),
    LAYOUT_RULES,
    ENGINE,
  )
  return [
    ...new Set<string>(
      [...judged.flatMap((page: JudgedPage): readonly BrowserFinding[] => page.findings), ...stale].map(
        (finding: BrowserFinding): string => findingLine(finding),
      ),
    ),
  ]
}

export const defectsOf = (nodes: readonly LayoutNode[], minimumGap: number = LAYOUT_MINIMUM_GAP): readonly string[] =>
  layoutDefectsOf([snapshotOf(nodes)], minimumGap)

// Tall enough for every page the specs build; only its width, the phone's, decides anything.
const BODY_HEIGHT: number = 300

export const BODY: LayoutNode = nodeOf({
  index: 0,
  parent: -1,
  tag: 'body',
  rects: [rect(0, 0, PHONE.width, BODY_HEIGHT)],
})
