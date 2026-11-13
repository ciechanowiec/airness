import { describe, expect, it } from 'vitest'
import type { HitTargetProbe } from '@/a11y-hit-target.js'
import { type HitTargetElement, hitTargetInPage } from '@/sweep-hit-target.js'

interface Box {
  readonly left: number
  readonly top: number
  readonly right: number
  readonly bottom: number
  readonly width: number
  readonly height: number
}

const boxOf = (left: number, top: number, width: number, height: number): Box => ({
  left,
  top,
  right: left + width,
  bottom: top + height,
  width,
  height,
})

const isInside = (box: Box, across: number, down: number): boolean =>
  across >= box.left && across <= box.right && down >= box.top && down <= box.bottom

const STYLE: { readonly clipPath: string; getPropertyValue: (property: string) => string } = {
  clipPath: 'none',
  getPropertyValue: (property: string): string => ({ clip: 'auto', 'pointer-events': 'auto' })[property] ?? 'visible',
}

// A link laid out on two lines: the end of one line and the start of the next. The document answers with the link
// inside either fragment and with the paragraph around it anywhere else.
const wrappedLink = (fragments: readonly Box[]): HitTargetElement => {
  const paragraph: HitTargetElement = linkOf(fragments, null)
  return linkOf(fragments, paragraph)
}

const linkOf = (fragments: readonly Box[], around: HitTargetElement | null): HitTargetElement => {
  const lefts: readonly number[] = fragments.map((fragment: Box): number => fragment.left)
  const tops: readonly number[] = fragments.map((fragment: Box): number => fragment.top)
  const rights: readonly number[] = fragments.map((fragment: Box): number => fragment.right)
  const bottoms: readonly number[] = fragments.map((fragment: Box): number => fragment.bottom)
  const left: number = Math.min(...lefts)
  const top: number = Math.min(...tops)
  const union: Box = boxOf(left, top, Math.max(...rights) - left, Math.max(...bottoms) - top)
  const element: HitTargetElement = {
    getBoundingClientRect: (): Box => union,
    getClientRects: (): readonly Box[] => fragments,
    contains: (other: unknown): boolean => other === element,
    closest: (): null => null,
    ownerDocument: {
      elementFromPoint: (across: number, down: number): unknown =>
        fragments.some((fragment: Box): boolean => isInside(fragment, across, down)) ? element : around,
      documentElement: { clientWidth: 1280, clientHeight: 800 },
      defaultView: { getComputedStyle: () => STYLE },
    },
  }
  return element
}

describe('the hit target of a control', () => {
  it('is reached at the centre of a fragment when the link wraps, though the centre of its box lies between them', () => {
    const probe: HitTargetProbe = hitTargetInPage(wrappedLink([boxOf(600, 100, 200, 24), boxOf(0, 124, 120, 24)]))
    expect(probe.ownsCentrePoint).toBe(true)
    expect(probe.width).toBe(800)
  })

  it('is obscured when something else answers at the centre of every fragment', () => {
    const covered: HitTargetElement = {
      ...wrappedLink([boxOf(600, 100, 200, 24), boxOf(0, 124, 120, 24)]),
    }
    const element: HitTargetElement = {
      ...covered,
      ownerDocument: { ...covered.ownerDocument, elementFromPoint: (): null => null },
    }
    expect(hitTargetInPage(element).ownsCentrePoint).toBe(false)
  })

  it('reads a single box at its own centre, with the style its window computes', () => {
    const probe: HitTargetProbe = hitTargetInPage(wrappedLink([boxOf(10, 10, 40, 40)]))
    expect(probe).toMatchObject({ ownsCentrePoint: true, clip: 'auto', pointerEvents: 'auto', isOutsidePage: false })
  })
})
