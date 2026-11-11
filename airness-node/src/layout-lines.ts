// How a page sets its blocks of text, as WCAG 1.4.8 Visual Presentation asks: no block justified to both
// edges, and no line longer than 80 characters, or 40 in Chinese, Japanese and Korean.
//
// Lines are read from what the browser rendered, never from a container's width: each word reports its
// rects, and the words a block lays out on one line, its own and those of the inline elements inside it,
// are joined into that line. A width says nothing about the font, the text or where the lines break.
import type { CandidateFinding } from './browser-exemptions.js'
import { clipOf, intersectionOf, visibleRectsOf } from './layout-clip.js'
import {
  hasArea,
  isInAriaHidden,
  isInClip,
  isInTurn,
  type LayoutNode,
  type LayoutRect,
  type LayoutSnapshot,
  type LayoutWord,
} from './layout-snapshot.js'

/** The longest line WCAG 1.4.8 allows, in characters. */
const MAXIMUM_LINE_CHARACTERS: number = 80

/** The longest line WCAG 1.4.8 allows in Chinese, Japanese or Korean, whose characters are each a word. */
const MAXIMUM_CJK_LINE_CHARACTERS: number = 40

// The computed displays of an element whose text flows into the lines of the block around it. Every other
// display - a block, a list item, a table cell, a flex or grid item, an inline block - lays out lines of its
// own, and is the block those words are judged in.
const INLINE_DISPLAYS: ReadonlySet<string> = new Set<string>([
  'inline',
  'contents',
  'ruby',
  'ruby-base',
  'ruby-text',
  'ruby-base-container',
  'ruby-text-container',
])

// The spellings that justify lines: `justify` every line but the last, `justify-all` (CSS Text 4) every one.
const JUSTIFIED: ReadonlySet<string> = new Set<string>(['justify', 'justify-all'])

// Text whose author's own line breaks and spaces carry its meaning - code, a listing, a poem set in `pre` -
// cannot be reflowed into shorter lines without changing it, so its line length is the content's own.
const PREFORMATTED_TAGS: ReadonlySet<string> = new Set<string>(['pre', 'code', 'kbd', 'samp', 'listing', 'xmp'])
const PRESERVED_WHITE_SPACE: ReadonlySet<string> = new Set<string>(['pre', 'pre-wrap', 'break-spaces'])

// A form control's text is a value the user typed or a list the control draws itself, not lines the page
// lays out. A button's label is page text and is judged.
const CONTROL_TAGS: ReadonlySet<string> = new Set<string>(['textarea', 'select', 'option', 'optgroup', 'input'])

// Two words share a line when their boxes share at least half the height of the shorter one; a superscript
// or a larger inline font still sits on the line it belongs to.
const LINE_OVERLAP_SHARE: number = 0.5

// Words further apart along a line than one em are not one line of reading: they are columns set side by
// side, or text on either side of an image. Splitting there can only shorten a line, never lengthen one.
const COLUMN_GAP_EMS: number = 1

// A gap between two words wider than this share of an em is a space, which a line's length counts; words
// that touch, as a word and a link or punctuation set in another element do, have none between them.
const SPACE_EMS: number = 0.15

const HALVES: number = 2

const ancestorsOf = (snapshot: LayoutSnapshot, node: LayoutNode): readonly LayoutNode[] => {
  const parent: LayoutNode | undefined = snapshot.nodes[node.parent]
  return parent === undefined ? [] : [parent, ...ancestorsOf(snapshot, parent)]
}

/**
 * The block whose lines an element's text is set in: the element itself unless its display is inline.
 * @param snapshot the measured page.
 * @param node the element.
 * @returns the block, or the element itself when no ancestor is one.
 */
const blockOf = (snapshot: LayoutSnapshot, node: LayoutNode): LayoutNode =>
  [node, ...ancestorsOf(snapshot, node)].find(
    (candidate: LayoutNode): boolean => !INLINE_DISPLAYS.has(candidate.display),
  ) ?? node

/** One rect of a word in a block's lines, with the font size of the element that drew it. */
interface PlacedWord {
  readonly rect: LayoutRect
  readonly characters: number
  readonly cjkCharacters: number
  readonly fontSize: number
}

// What of a word a reader can see: an ellipsis or a clipping ancestor hides the rest of a line, which is then
// no longer part of what is read on it.
const visibleWordsOf = (snapshot: LayoutSnapshot, node: LayoutNode): readonly PlacedWord[] => {
  if (node.visibility !== 'visible' || node.words.length === 0) {
    return []
  }
  const own: LayoutRect | undefined = clipOf(node)
  return node.words.flatMap((word: LayoutWord): readonly PlacedWord[] => {
    const shown: LayoutRect = own === undefined ? word.rect : intersectionOf(word.rect, own)
    return word.characters > 0 && hasArea(shown) && visibleRectsOf(snapshot, node, [shown]).length > 0
      ? [
          {
            rect: word.rect,
            characters: word.characters,
            cjkCharacters: word.cjkCharacters,
            fontSize: node.fontSize,
          },
        ]
      : []
  })
}

const isShown = (snapshot: LayoutSnapshot, node: LayoutNode): boolean =>
  node.visibility === 'visible' &&
  node.rects.some((rect: LayoutRect): boolean => hasArea(rect)) &&
  !isInClip(snapshot, node) &&
  !isInAriaHidden(snapshot, node) &&
  !isInTurn(snapshot, node)

/** A block of text and the words set in its lines. */
interface TextBlock {
  readonly block: LayoutNode
  readonly words: readonly PlacedWord[]
}

/**
 * Every rendered block of text on a page, with the words of its lines.
 *
 * Text in a rotated or skewed subtree is left out: its rects are the upright boxes around the turned lines,
 * which say nothing about which words share a line.
 * @param snapshot the measured page.
 * @returns one entry per block that sets any visible word, in document order.
 */
const textBlocksOf = (snapshot: LayoutSnapshot): readonly TextBlock[] => {
  const placed: readonly TextBlock[] = snapshot.nodes.flatMap((node: LayoutNode): readonly TextBlock[] => {
    const words: readonly PlacedWord[] = visibleWordsOf(snapshot, node)
    return words.length === 0 ? [] : [{ block: blockOf(snapshot, node), words }]
  })
  return [...new Set<LayoutNode>(placed.map((entry: TextBlock): LayoutNode => entry.block))]
    .filter((block: LayoutNode): boolean => isShown(snapshot, block))
    .map(
      (block: LayoutNode): TextBlock => ({
        block,
        words: placed
          .filter((entry: TextBlock): boolean => entry.block === block)
          .flatMap((entry: TextBlock): readonly PlacedWord[] => entry.words),
      }),
    )
}

const heightOf = (rect: LayoutRect): number => rect.bottom - rect.top

const isOnLine = (line: readonly PlacedWord[], word: PlacedWord): boolean =>
  line.some((other: PlacedWord): boolean => {
    const shared: number = Math.min(other.rect.bottom, word.rect.bottom) - Math.max(other.rect.top, word.rect.top)
    return shared >= Math.min(heightOf(other.rect), heightOf(word.rect)) * LINE_OVERLAP_SHARE
  })

const byTopThenLeft = (first: PlacedWord, second: PlacedWord): number =>
  first.rect.top - second.rect.top || first.rect.left - second.rect.left

const byLeft = (first: PlacedWord, second: PlacedWord): number => first.rect.left - second.rect.left

// The words grouped into the lines they were set in, top to bottom. Taken from the top down, a word almost
// always joins the line last started, so the search runs from there.
const linesOf = (words: readonly PlacedWord[]): readonly (readonly PlacedWord[])[] =>
  words
    .toSorted(byTopThenLeft)
    .reduce((lines: readonly (readonly PlacedWord[])[], word: PlacedWord): readonly (readonly PlacedWord[])[] => {
      const index: number = lines.findLastIndex((line: readonly PlacedWord[]): boolean => isOnLine(line, word))
      return index === -1
        ? [...lines, [word]]
        : lines.map((line: readonly PlacedWord[], at: number): readonly PlacedWord[] =>
            at === index ? [...line, word] : line,
          )
    }, [])

// One line split where its words stand further apart than a column gap, left to right.
const runsOf = (line: readonly PlacedWord[]): readonly (readonly PlacedWord[])[] =>
  line
    .toSorted(byLeft)
    .reduce((runs: readonly (readonly PlacedWord[])[], word: PlacedWord): readonly (readonly PlacedWord[])[] => {
      const run: readonly PlacedWord[] | undefined = runs.at(-1)
      const previous: PlacedWord | undefined = run?.at(-1)
      return run === undefined ||
        previous === undefined ||
        word.rect.left - previous.rect.right > previous.fontSize * COLUMN_GAP_EMS
        ? [...runs, [word]]
        : [...runs.slice(0, -1), [...run, word]]
    }, [])

/** One rendered line of a block, as WCAG 1.4.8 measures it. */
interface MeasuredLine {
  /** Its characters, the spaces between its words included. */
  readonly characters: number
  /** Whether most of its characters are Chinese, Japanese or Korean. */
  readonly isCjk: boolean
}

const measured = (run: readonly PlacedWord[]): MeasuredLine => {
  const glyphs: number = run.reduce((sum: number, word: PlacedWord): number => sum + word.characters, 0)
  const cjk: number = run.reduce((sum: number, word: PlacedWord): number => sum + word.cjkCharacters, 0)
  const spaces: number = run.slice(1).filter((word: PlacedWord, index: number): boolean => {
    const previous: PlacedWord | undefined = run[index]
    return previous !== undefined && word.rect.left - previous.rect.right >= previous.fontSize * SPACE_EMS
  }).length
  return { characters: Math.round(glyphs + spaces), isCjk: cjk * HALVES > glyphs }
}

const limitOf = (line: MeasuredLine): number => (line.isCjk ? MAXIMUM_CJK_LINE_CHARACTERS : MAXIMUM_LINE_CHARACTERS)

const isPreformatted = (snapshot: LayoutSnapshot, block: LayoutNode): boolean =>
  [block, ...ancestorsOf(snapshot, block)].some(
    (node: LayoutNode): boolean =>
      PREFORMATTED_TAGS.has(node.tag) || CONTROL_TAGS.has(node.tag) || PRESERVED_WHITE_SPACE.has(node.whiteSpace),
  )

/**
 * Lines longer than WCAG 1.4.8 allows (rule `line-length`): 80 characters, or 40 in Chinese, Japanese or
 * Korean. Preformatted text and code, and the text of a form control, are not judged.
 * @param snapshot the measured page.
 * @returns one candidate per block, naming its longest line beyond the limit.
 */
export const findLongLines = (snapshot: LayoutSnapshot): readonly CandidateFinding[] =>
  textBlocksOf(snapshot)
    .filter(({ block }: TextBlock): boolean => !isPreformatted(snapshot, block))
    .flatMap(({ block, words }: TextBlock): readonly CandidateFinding[] => {
      const longest: MeasuredLine | undefined = linesOf(words)
        .flatMap((line: readonly PlacedWord[]): readonly (readonly PlacedWord[])[] => runsOf(line))
        .map((run: readonly PlacedWord[]): MeasuredLine => measured(run))
        .filter((line: MeasuredLine): boolean => line.characters > limitOf(line))
        .toSorted((first: MeasuredLine, second: MeasuredLine): number => second.characters - first.characters)[0]
      return longest === undefined
        ? []
        : [
            {
              rule: 'line-length',
              subjects: [block],
              message:
                `a line of ${String(longest.characters)} ${longest.isCjk ? 'CJK ' : ''}characters, beyond ` +
                `${String(limitOf(longest))}; narrow the text's measure: ${block.label}`,
            },
          ]
    })

/**
 * Blocks of text justified to both edges (rule `text-justified`): `text-align: justify` or `justify-all` over
 * two lines or more, or `text-align-last: justify`, which stretches even a block's only line. A one-line block
 * under `text-align: justify` is set as its start edge sets it, so nothing about it is justified.
 * @param snapshot the measured page.
 * @returns one candidate per justified block.
 */
export const findJustifiedText = (snapshot: LayoutSnapshot): readonly CandidateFinding[] =>
  textBlocksOf(snapshot).flatMap(({ block, words }: TextBlock): readonly CandidateFinding[] => {
    const lines: number = linesOf(words).length
    const isJustified: boolean =
      block.textAlign === 'justify-all' ||
      JUSTIFIED.has(block.textAlignLast) ||
      (block.textAlign === 'justify' && lines >= HALVES)
    const extent: string = lines === 1 ? 'its only line' : String(lines) + ' lines'
    return isJustified
      ? [
          {
            rule: 'text-justified',
            subjects: [block],
            message: `text justified to both edges over ${extent}; align it to one edge: ${block.label}`,
          },
        ]
      : []
  })
