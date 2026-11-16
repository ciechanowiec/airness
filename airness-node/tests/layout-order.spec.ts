// How the resting scan reads CSS reordering for WCAG 1.3.2 Meaningful Sequence: a flex or grid group whose
// text a reordering property shows in another order than the document's fails, and nothing else does.
import { describe, expect, it } from 'vitest'
import { applyExemptions, type CandidateFinding, type ExemptedFindings } from '@/browser-exemptions'
import { restingCandidates } from '@/browser-rules'
import { findReorderedText } from '@/layout-order'
import type { FlowStyle, LayoutNode, LayoutRect, LayoutSnapshot } from '@/layout-snapshot'
import { BODY, FLOW, nodeOf, rect, snapshotOf } from './helpers/layout-fixture.js'

const READ_IN_DOCUMENT_ORDER: string =
  'a screen reader reads them in document order, so order the markup as it is shown'

/**
 * An element that sets one block of text, shown where `box` says.
 * @param at its index in the document, and its parent's.
 * @param label how a finding names it.
 * @param box where its text is shown.
 * @param fields anything else about it.
 * @returns the element.
 */
const phrase = (
  at: readonly [number, number],
  label: string,
  box: LayoutRect,
  fields: Partial<LayoutNode> = {},
): LayoutNode =>
  nodeOf({
    index: at[0],
    parent: at[1],
    label,
    tag: label.split(/[ .#]/u, 1)[0] ?? 'div',
    rects: [box],
    textRects: [box],
    words: [{ rect: box, characters: 4, cjkCharacters: 0 }],
    ...fields,
  })

/**
 * The group a case judges, the page's first element.
 * @param flow how its own flow is set, beside a row of flex items.
 * @param fields anything else about it.
 * @returns the group.
 */
const group = (flow: Partial<FlowStyle> = {}, fields: Partial<LayoutNode> = {}): LayoutNode =>
  nodeOf({
    index: 1,
    parent: 0,
    label: 'div.stat',
    display: 'flex',
    rects: [rect(0, 0, 220, 60)],
    ...fields,
    flow: { ...FLOW, ...flow },
  })

const moved = (flow: Partial<FlowStyle>): Partial<LayoutNode> => ({ flow: { ...FLOW, ...flow } })

// Where two texts of one group are shown: one above the other, or side by side on one line.
const TOP: LayoutRect = rect(0, 0, 100, 20)
const BELOW: LayoutRect = rect(0, 40, 100, 60)
const LEFT: LayoutRect = TOP
const RIGHT: LayoutRect = rect(120, 0, 220, 20)

const pageOf = (...nodes: readonly LayoutNode[]): LayoutSnapshot => snapshotOf([BODY, ...nodes])

const messages = (candidates: readonly CandidateFinding[]): readonly string[] =>
  candidates.map((candidate: CandidateFinding): string => `${candidate.rule}: ${candidate.message}`)

const swapped = (causes: string): string =>
  `reading-order: div.stat shows p "Second" before p "First", which comes first in the document, through ` +
  `${causes}; ${READ_IN_DOCUMENT_ORDER}`

// The second of two texts moved to the end of its group and shown at its left edge, in a group whose lines run
// in the given direction: last in a left-to-right line, first in a right-to-left one.
const movedLeft = (direction: string): readonly CandidateFinding[] =>
  findReorderedText(
    pageOf(
      group({ direction }),
      phrase([2, 1], 'p "First"', RIGHT),
      phrase([3, 1], 'p "Second"', LEFT, moved({ order: '1' })),
    ),
  )

describe('a group whose text CSS shows out of document order', () => {
  // A strip of statistics: each term precedes its figure in the markup, and `order` shows the figure above it.
  it('fails a figure shown above the term that precedes it, and passes the figure written first', () => {
    const column: Partial<FlowStyle> = { flexDirection: 'column' }
    expect([
      messages(
        findReorderedText(
          pageOf(
            group(column),
            phrase([2, 1], 'dt "Branches"', BELOW, moved({ order: '2' })),
            phrase([3, 1], 'dd "12"', TOP, moved({ order: '1' })),
          ),
        ),
      ),
      findReorderedText(pageOf(group(column), phrase([2, 1], 'dd "12"', TOP), phrase([3, 1], 'dt "Branches"', BELOW))),
    ]).toEqual([
      [
        'reading-order: div.stat shows dd "12" before dt "Branches", which comes first in the document, through ' +
          `order: 1 on dd "12", order: 2 on dt "Branches"; ${READ_IN_DOCUMENT_ORDER}`,
      ],
      [],
    ])
  })

  it('names every property that can move one item ahead of another', () => {
    const grid: Partial<LayoutNode> = { display: 'grid' }
    const judged = (container: LayoutNode, first: LayoutRect, second: LayoutNode): readonly string[] =>
      messages(findReorderedText(pageOf(container, phrase([2, 1], 'p "First"', first), second)))
    expect([
      judged(group({ flexDirection: 'row-reverse' }), RIGHT, phrase([3, 1], 'p "Second"', LEFT)),
      judged(group({ flexDirection: 'column-reverse' }), BELOW, phrase([3, 1], 'p "Second"', TOP)),
      judged(group({ flexWrap: 'wrap-reverse' }), BELOW, phrase([3, 1], 'p "Second"', TOP)),
      judged(group({}, { display: 'inline-flex' }), RIGHT, phrase([3, 1], 'p "Second"', LEFT, moved({ order: '-1' }))),
      judged(group({ gridAutoFlow: 'row dense' }, grid), RIGHT, phrase([3, 1], 'p "Second"', LEFT)),
      judged(
        group({}, grid),
        BELOW,
        phrase([3, 1], 'p "Second"', TOP, moved({ gridLines: ['1', 'auto', 'auto', 'auto'] })),
      ),
      judged(
        group({}, { display: 'inline-grid' }),
        RIGHT,
        phrase([3, 1], 'p "Second"', LEFT, moved({ gridLines: ['figure', 'figure', 'figure', 'figure'] })),
      ),
    ]).toEqual([
      [swapped('flex-direction: row-reverse')],
      [swapped('flex-direction: column-reverse')],
      [swapped('flex-wrap: wrap-reverse')],
      [swapped('order: -1 on p "Second"')],
      [swapped('grid-auto-flow: row dense')],
      [swapped('grid lines placing p "Second"')],
      [swapped('grid lines placing p "Second"')],
    ])
  })

  it('reports a group once, at its first pair shown out of order', () => {
    expect(
      findReorderedText(
        pageOf(
          group({ flexDirection: 'row-reverse' }, { rects: [rect(0, 0, 340, 20)] }),
          phrase([2, 1], 'p "First"', rect(240, 0, 340, 20)),
          phrase([3, 1], 'p "Second"', RIGHT),
          phrase([4, 1], 'p "Third"', LEFT),
        ),
      ),
    ).toHaveLength(1)
  })

  it('names the group, so an exemption on it states why the order is intended, and the resting scan judges it', () => {
    const snapshot: LayoutSnapshot = pageOf(
      group({ flexDirection: 'column-reverse' }, { exemption: 'reading-order', exemptionReason: 'the eyebrow' }),
      phrase([2, 1], 'p "First"', BELOW),
      phrase([3, 1], 'p "Second"', TOP),
    )
    const candidates: readonly CandidateFinding[] = restingCandidates(snapshot)
    const judged: ExemptedFindings = applyExemptions(
      snapshot,
      { location: '/', state: 'resting', engine: 'chromium', viewport: { width: 390, height: 844 } },
      candidates,
    )
    expect([
      candidates.map((candidate: CandidateFinding): string => candidate.rule),
      candidates.flatMap((candidate: CandidateFinding): readonly number[] =>
        candidate.subjects.map((subject: LayoutNode): number => subject.index),
      ),
      judged.findings,
      judged.used,
    ]).toEqual([['reading-order'], [1], [], [{ location: '/', key: 'body/div:0', rule: 'reading-order' }]])
  })
})

describe('a group the rule passes', () => {
  it('is one whose reordering property leaves its text in document order', () => {
    expect([
      findReorderedText(
        pageOf(group(), phrase([2, 1], 'p "First"', LEFT), phrase([3, 1], 'p "Second"', RIGHT, moved({ order: '1' }))),
      ),
      findReorderedText(
        pageOf(
          group({ flexDirection: 'column-reverse' }),
          phrase([2, 1], 'p "Only"', TOP),
          nodeOf({ index: 3, parent: 1 }),
        ),
      ),
    ]).toEqual([[], []])
  })

  it('is one no reordering property moves, though a grid flowing down its columns shows text above earlier text', () => {
    expect(
      findReorderedText(
        pageOf(
          group({ gridAutoFlow: 'column' }, { display: 'grid' }),
          phrase([2, 1], 'p "First"', TOP),
          phrase([3, 1], 'p "Second"', BELOW),
          phrase([4, 1], 'p "Third"', RIGHT),
        ),
      ),
    ).toEqual([])
  })

  it('is one that moves an image above a heading, since only items that set text are compared', () => {
    expect(
      findReorderedText(
        pageOf(
          group({ flexDirection: 'column' }),
          phrase([2, 1], 'h3 "Title"', BELOW),
          nodeOf({ index: 3, parent: 1, tag: 'img', rects: [TOP], flow: { ...FLOW, order: '-1' } }),
        ),
      ),
    ).toEqual([])
  })

  it('is one that moves a region of several blocks, as a sidebar shown before the main content is', () => {
    expect(
      findReorderedText(
        pageOf(
          group(),
          phrase([2, 1], 'main "Story"', RIGHT),
          nodeOf({ index: 3, parent: 1, tag: 'aside', rects: [LEFT], flow: { ...FLOW, order: '-1' } }),
          phrase([4, 3], 'p "Related"', rect(0, 0, 100, 8)),
          phrase([5, 3], 'p "Popular"', rect(0, 12, 100, 20)),
        ),
      ),
    ).toEqual([])
  })

  it('reads a right-to-left group from its right edge', () => {
    expect([messages(movedLeft('ltr')), movedLeft('rtl')]).toEqual([[swapped('order: 1 on p "Second"')], []])
  })

  it('leaves a vertical writing mode unjudged', () => {
    expect(
      findReorderedText(
        pageOf(
          group({ flexDirection: 'column-reverse', writingMode: 'vertical-rl' }),
          phrase([2, 1], 'p "First"', BELOW),
          phrase([3, 1], 'p "Second"', TOP),
        ),
      ),
    ).toEqual([])
  })

  it('does not compare text a reader cannot see, nor a child taken out of the flow', () => {
    const column: LayoutNode = group({ flexDirection: 'column-reverse' })
    expect([
      findReorderedText(
        pageOf(column, phrase([2, 1], 'p "First"', BELOW), phrase([3, 1], 'p "Second"', TOP, { isAriaHidden: true })),
      ),
      findReorderedText(
        pageOf(column, phrase([2, 1], 'p "First"', BELOW), phrase([3, 1], 'p "Second"', TOP, { isClipped: true })),
      ),
      findReorderedText(
        pageOf(column, phrase([2, 1], 'p "First"', BELOW), phrase([3, 1], 'p "Second"', TOP, { position: 'absolute' })),
      ),
    ]).toEqual([[], [], []])
  })
})

describe('the items of a group', () => {
  it('each set their text in one block, which may be a paragraph inside the item', () => {
    expect(
      messages(
        findReorderedText(
          pageOf(
            group({ flexDirection: 'column-reverse' }),
            nodeOf({ index: 2, parent: 1, label: 'div.term', rects: [BELOW] }),
            phrase([3, 2], 'p "First"', BELOW),
            nodeOf({ index: 4, parent: 1, label: 'div.figure', rects: [TOP] }),
            phrase([5, 4], 'p "Second"', TOP),
          ),
        ),
      ),
    ).toEqual([
      'reading-order: div.stat shows div.figure before div.term, which comes first in the document, through ' +
        `flex-direction: column-reverse; ${READ_IN_DOCUMENT_ORDER}`,
    ])
  })
})
