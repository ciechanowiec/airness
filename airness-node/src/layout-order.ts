// How the resting scan holds CSS reordering to WCAG 1.3.2 Meaningful Sequence (rule `reading-order`). A screen
// reader, a braille display and the keyboard follow the document, so text CSS shows in another order is seen
// in one order and read in another: a figure shown above its label is announced after it.
//
// A flex or grid group is judged only when a property that moves items is in effect - `order`, a reversed
// direction or wrap, explicit grid lines, dense packing - and only its items that each set one block of text
// are compared: a term and its figure, a label, a button. An image moved above a heading and a sidebar of many
// blocks shown before the main content are not phrases read out of order, and are left alone. Each pass over
// the page is linear, and a page that moves nothing costs one pass.
import type { CandidateFinding } from './browser-exemptions.js'
import { type PlacedWord, type TextBlock, textBlocksOf } from './layout-lines.js'
import type { LayoutNode, LayoutRect, LayoutSnapshot } from './layout-snapshot.js'

const FLEX_DISPLAYS: ReadonlySet<string> = new Set<string>(['flex', 'inline-flex'])
const GRID_DISPLAYS: ReadonlySet<string> = new Set<string>(['grid', 'inline-grid'])

// An absolutely positioned or fixed child is no item of its container's flow.
const OUT_OF_FLOW: ReadonlySet<string> = new Set<string>(['absolute', 'fixed'])

// The tolerance every geometric comparison allows, in CSS pixels.
const GEOMETRY_TOLERANCE: number = 1

const isFlex = (node: LayoutNode): boolean => FLEX_DISPLAYS.has(node.display)

const isGrid = (node: LayoutNode): boolean => GRID_DISPLAYS.has(node.display)

// A grid line the author names, by number or name, rather than leaving the item to auto-placement.
const isExplicitLine = (line: string): boolean => line !== 'auto' && !line.startsWith('span')

const containerCauses = (container: LayoutNode): readonly string[] => [
  ...(isFlex(container) && container.flow.flexDirection.endsWith('-reverse')
    ? [`flex-direction: ${container.flow.flexDirection}`]
    : []),
  ...(isFlex(container) && container.flow.flexWrap === 'wrap-reverse' ? ['flex-wrap: wrap-reverse'] : []),
  ...(isGrid(container) && container.flow.gridAutoFlow.includes('dense')
    ? [`grid-auto-flow: ${container.flow.gridAutoFlow}`]
    : []),
]

const itemCauses = (container: LayoutNode, item: LayoutNode): readonly string[] => [
  ...(item.flow.order === '0' ? [] : [`order: ${item.flow.order} on ${item.label}`]),
  ...(isGrid(container) && item.flow.gridLines.some((line: string): boolean => isExplicitLine(line))
    ? [`grid lines placing ${item.label}`]
    : []),
]

// The flex or grid container an element is an in-flow item of, in a horizontal writing mode.
const containerOf = (snapshot: LayoutSnapshot, node: LayoutNode): LayoutNode | undefined => {
  const parent: LayoutNode | undefined = snapshot.nodes[node.parent]
  return parent !== undefined &&
    (isFlex(parent) || isGrid(parent)) &&
    parent.flow.writingMode === 'horizontal-tb' &&
    !OUT_OF_FLOW.has(node.position)
    ? parent
    : undefined
}

// The item of a group a node sits in: the node itself, or its nearest ancestor that is one.
const itemHolding = (
  snapshot: LayoutSnapshot,
  items: ReadonlySet<LayoutNode>,
  node: LayoutNode | undefined,
): LayoutNode | undefined =>
  node === undefined || items.has(node) ? node : itemHolding(snapshot, items, snapshot.nodes[node.parent])

const boxOf = (words: readonly PlacedWord[]): LayoutRect => ({
  left: Math.min(...words.map((word: PlacedWord): number => word.rect.left)),
  top: Math.min(...words.map((word: PlacedWord): number => word.rect.top)),
  right: Math.max(...words.map((word: PlacedWord): number => word.rect.right)),
  bottom: Math.max(...words.map((word: PlacedWord): number => word.rect.bottom)),
})

/**
 * Whether one text is shown before another, reading line by line from the top.
 * @param first where the first text is shown.
 * @param second where the second text is shown.
 * @param isRightToLeft whether the group's lines run from right to left.
 * @returns true when the first lies entirely above the second, or on its line and entirely before it.
 */
const isShownBefore = (first: LayoutRect, second: LayoutRect, isRightToLeft: boolean): boolean => {
  if (first.bottom <= second.top + GEOMETRY_TOLERANCE) {
    return true
  }
  if (second.bottom <= first.top + GEOMETRY_TOLERANCE) {
    return false
  }
  return isRightToLeft
    ? first.left >= second.right - GEOMETRY_TOLERANCE
    : first.right <= second.left + GEOMETRY_TOLERANCE
}

/** An item that sets one block of text, and where that text is shown. */
interface Phrase {
  readonly item: LayoutNode
  readonly box: LayoutRect
}

const groupCandidate = (
  container: LayoutNode,
  items: readonly LayoutNode[],
  blocksOf: ReadonlyMap<LayoutNode, readonly TextBlock[]>,
): readonly CandidateFinding[] => {
  const phrases: readonly Phrase[] = items.flatMap((item: LayoutNode): readonly Phrase[] => {
    const [only, ...more] = blocksOf.get(item) ?? []
    return only !== undefined && more.length === 0 ? [{ item, box: boxOf(only.words) }] : []
  })
  const isRightToLeft: boolean = container.flow.direction === 'rtl'
  const pairs: readonly (readonly [Phrase, Phrase])[] = phrases.flatMap(
    (earlier: Phrase, at: number): readonly (readonly [Phrase, Phrase])[] =>
      phrases
        .slice(at + 1)
        .filter((later: Phrase): boolean => isShownBefore(later.box, earlier.box, isRightToLeft))
        .map((later: Phrase): readonly [Phrase, Phrase] => [earlier, later]),
  )
  const [first] = pairs
  if (first === undefined) {
    return []
  }
  const [earlier, later] = first
  const causes: readonly string[] = [
    ...containerCauses(container),
    ...itemCauses(container, later.item),
    ...itemCauses(container, earlier.item),
  ]
  return [
    {
      rule: 'reading-order',
      subjects: [container],
      message:
        `${container.label} shows ${later.item.label} before ${earlier.item.label}, which comes first in the ` +
        `document, through ${causes.join(', ')}; a screen reader reads them in document order, so order the ` +
        'markup as it is shown',
    },
  ]
}

/**
 * Flex and grid groups whose text CSS shows in another order than the document's (rule `reading-order`).
 * @param snapshot the measured page.
 * @returns at most one candidate per group, naming the first pair it shows out of order.
 */
export const findReorderedText = (snapshot: LayoutSnapshot): readonly CandidateFinding[] => {
  const moving: ReadonlySet<LayoutNode> = new Set<LayoutNode>(
    snapshot.nodes.flatMap((node: LayoutNode): readonly LayoutNode[] => {
      const container: LayoutNode | undefined = containerOf(snapshot, node)
      return container !== undefined &&
        (containerCauses(container).length > 0 || itemCauses(container, node).length > 0)
        ? [container]
        : []
    }),
  )
  if (moving.size === 0) {
    return []
  }
  const items: readonly LayoutNode[] = snapshot.nodes.filter((node: LayoutNode): boolean => {
    const container: LayoutNode | undefined = containerOf(snapshot, node)
    return container !== undefined && moving.has(container)
  })
  const itemSet: ReadonlySet<LayoutNode> = new Set<LayoutNode>(items)
  const held: readonly (readonly [LayoutNode, TextBlock])[] = textBlocksOf(snapshot).flatMap(
    (block: TextBlock): readonly (readonly [LayoutNode, TextBlock])[] => {
      const item: LayoutNode | undefined = itemHolding(snapshot, itemSet, block.block)
      return item === undefined ? [] : [[item, block]]
    },
  )
  const blocksOf: ReadonlyMap<LayoutNode, readonly TextBlock[]> = new Map<LayoutNode, readonly TextBlock[]>(
    items.map((item: LayoutNode): readonly [LayoutNode, readonly TextBlock[]] => [
      item,
      held
        .filter(([holder]: readonly [LayoutNode, TextBlock]): boolean => holder === item)
        .map(([, block]: readonly [LayoutNode, TextBlock]): TextBlock => block),
    ]),
  )
  return [...moving].flatMap((container: LayoutNode): readonly CandidateFinding[] =>
    groupCandidate(
      container,
      items.filter((item: LayoutNode): boolean => item.parent === container.index),
      blocksOf,
    ),
  )
}
