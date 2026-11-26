// How the resting scan reads a page's blocks of text for WCAG 1.4.8: lines longer than 80 characters, or 40
// in Chinese, Japanese and Korean, and blocks justified to both edges; beside them, blocks wrapped without
// weighing their last lines. Each line is stated as the words the browser reports, one rect per word.
import { describe, expect, it } from 'vitest'
import type { CandidateFinding } from '@/browser-exemptions'
import { restingCandidates } from '@/browser-rules'
import { findJustifiedText, findLongLines, findUnevenWrap } from '@/layout-lines'
import type { LayoutNode, LayoutRect, LayoutSnapshot, LayoutWord } from '@/layout-snapshot'
import { BODY, nodeOf, rect, snapshotOf } from './helpers/layout-fixture.js'

// Every character is 8px wide, every space 4px, every line 20px tall with its text 18px of it.
const CHARACTER: number = 8
const SPACE: number = 4
const LINE: number = 20
const TEXT_HEIGHT: number = 18

/** One word of a line: how many characters it has, and how many of them are CJK. */
interface Word {
  readonly characters: number
  readonly cjk?: number
}

const latin = (...lengths: readonly number[]): readonly Word[] =>
  lengths.map((characters: number): Word => ({ characters }))

/**
 * The rects of a run of words on one line, set left to right from `left` with a space between each.
 * @param line the line's index, from the top.
 * @param words the words.
 * @param left where the first word starts.
 * @param gap the space between two words.
 * @returns one rect per word.
 */
const run = (line: number, words: readonly Word[], left: number = 0, gap: number = SPACE): readonly LayoutWord[] =>
  words.reduce((placed: readonly LayoutWord[], word: Word): readonly LayoutWord[] => {
    const start: number = placed.length === 0 ? left : (placed.at(-1)?.rect.right ?? left) + gap
    return [
      ...placed,
      {
        rect: rect(start, line * LINE, start + word.characters * CHARACTER, line * LINE + TEXT_HEIGHT),
        characters: word.characters,
        cjkCharacters: word.cjk ?? 0,
      },
    ]
  }, [])

// A paragraph wide enough for any line the cases set, holding the given words.
const paragraph = (words: readonly LayoutWord[], fields: Partial<LayoutNode> = {}): LayoutNode =>
  nodeOf({
    index: 1,
    parent: 0,
    tag: 'p',
    label: 'p.lead',
    rects: [rect(0, 0, 1200, 200)],
    textRects: words.map((word: LayoutWord): LayoutRect => word.rect),
    words,
    ...fields,
  })

const pageOf = (...nodes: readonly LayoutNode[]): LayoutSnapshot =>
  snapshotOf([BODY, ...nodes], { width: 1280, height: 800 })

const messages = (candidates: readonly CandidateFinding[]): readonly string[] =>
  candidates.map((candidate: CandidateFinding): string => `${candidate.rule}: ${candidate.message}`)

// Nine eight-letter words and the eight spaces between them: 80 characters.
const EIGHTY: readonly Word[] = latin(8, 8, 8, 8, 8, 8, 8, 8, 8)
const EIGHTY_ONE: readonly Word[] = latin(8, 8, 8, 8, 8, 8, 8, 8, 9)

describe('line length', () => {
  it('fails a rendered line longer than 80 characters, its spaces counted, and passes one of 80', () => {
    expect([
      messages(findLongLines(pageOf(paragraph(run(0, EIGHTY_ONE))))),
      messages(findLongLines(pageOf(paragraph([...run(0, EIGHTY), ...run(1, EIGHTY)])))),
    ]).toEqual([["line-length: a line of 81 characters, beyond 80; narrow the text's measure: p.lead"], []])
  })

  it('names the longest line of a block once', () => {
    expect(
      messages(findLongLines(pageOf(paragraph([...run(0, EIGHTY_ONE), ...run(1, latin(8, 8, 8, 8, 8, 8, 8, 8, 20))])))),
    ).toEqual(["line-length: a line of 92 characters, beyond 80; narrow the text's measure: p.lead"])
  })

  it('joins the words a block sets on one line from the inline elements inside it, but not an inline block', () => {
    const own: readonly LayoutWord[] = run(0, latin(8, 8, 8, 8, 8))
    const linked: readonly LayoutWord[] = run(0, latin(8, 8, 8, 9), 5 * CHARACTER * 8 + 5 * SPACE)
    const block: LayoutNode = paragraph(own)
    const link = (display: string): LayoutNode =>
      nodeOf({
        index: 2,
        parent: 1,
        tag: 'a',
        label: 'a "linked words"',
        display,
        rects: [rect(linked[0]?.rect.left ?? 0, 0, linked.at(-1)?.rect.right ?? 0, TEXT_HEIGHT)],
        words: linked,
      })
    expect([
      messages(findLongLines(pageOf(block, link('inline')))),
      messages(findLongLines(pageOf(block, link('inline-block')))),
    ]).toEqual([["line-length: a line of 81 characters, beyond 80; narrow the text's measure: p.lead"], []])
  })

  it('counts no space between words that touch, as a word and the punctuation after its link do', () => {
    expect(messages(findLongLines(pageOf(paragraph(run(0, latin(9, 9, 9, 9, 9, 9, 9, 9, 9), 0, 0)))))).toEqual([
      "line-length: a line of 81 characters, beyond 80; narrow the text's measure: p.lead",
    ])
  })

  it('reads words further apart than an em as columns side by side, not one line', () => {
    const columns: readonly LayoutWord[] = [...run(0, latin(8, 8, 8, 8, 8)), ...run(0, latin(8, 8, 8, 8, 8), 600)]
    expect(messages(findLongLines(pageOf(paragraph(columns))))).toEqual([])
  })

  it('holds a line of Chinese, Japanese or Korean to 40 characters', () => {
    expect([
      messages(findLongLines(pageOf(paragraph(run(0, [{ characters: 41, cjk: 41 }]))))),
      messages(findLongLines(pageOf(paragraph(run(0, [{ characters: 40, cjk: 40 }]))))),
      // A Latin line quoting a few CJK characters is held to 80.
      messages(findLongLines(pageOf(paragraph(run(0, [...latin(8, 8, 8, 8, 8), { characters: 10, cjk: 10 }]))))),
    ]).toEqual([["line-length: a line of 41 CJK characters, beyond 40; narrow the text's measure: p.lead"], [], []])
  })

  it('leaves preformatted text, code and form controls to their own lines', () => {
    const long: readonly LayoutWord[] = run(0, EIGHTY_ONE)
    const inPre: readonly LayoutNode[] = [
      nodeOf({ index: 1, parent: 0, tag: 'pre', rects: [rect(0, 0, 1200, 40)] }),
      nodeOf({ index: 2, parent: 1, tag: 'span', display: 'block', rects: [rect(0, 0, 1200, 20)], words: long }),
    ]
    expect(
      [
        pageOf(paragraph(long, { tag: 'pre' })),
        pageOf(paragraph(long, { tag: 'code', display: 'block' })),
        pageOf(paragraph(long, { tag: 'textarea', display: 'inline-block' })),
        pageOf(paragraph(long, { tag: 'option' })),
        pageOf(paragraph(long, { whiteSpace: 'pre' })),
        pageOf(paragraph(long, { whiteSpace: 'pre-wrap' })),
        pageOf(paragraph(long, { whiteSpace: 'break-spaces' })),
        pageOf(...inPre),
      ].flatMap((page: LayoutSnapshot): readonly CandidateFinding[] => findLongLines(page)),
    ).toEqual([])
  })

  it('still judges inline code in prose, and prose that keeps only its line breaks', () => {
    const code: LayoutNode = nodeOf({
      index: 2,
      parent: 1,
      tag: 'code',
      display: 'inline',
      rects: [rect(0, 0, 64, TEXT_HEIGHT)],
      words: run(0, latin(8)),
    })
    expect([
      findLongLines(pageOf(paragraph(run(0, latin(8, 8, 8, 8, 8, 8, 8, 9), 8 * CHARACTER + SPACE)), code)).length,
      findLongLines(pageOf(paragraph(run(0, EIGHTY_ONE), { whiteSpace: 'pre-line' }))).length,
    ]).toEqual([1, 1])
  })

  it('ignores text nobody sees: hidden, visually hidden, hidden from assistive technology or turned', () => {
    const long: readonly LayoutWord[] = run(0, EIGHTY_ONE)
    expect(
      [
        pageOf(paragraph(long, { visibility: 'hidden' })),
        pageOf(paragraph(long, { isClipped: true })),
        pageOf(paragraph(long, { isAriaHidden: true })),
        pageOf(paragraph(long, { rotate: '12deg' })),
        pageOf(paragraph(long, { rects: [] })),
        pageOf(paragraph(long.map((word: LayoutWord): LayoutWord => ({ ...word, characters: 0 })))),
      ].flatMap((page: LayoutSnapshot): readonly CandidateFinding[] => findLongLines(page)),
    ).toEqual([])
  })

  it('counts only the words an ellipsis or a clipping ancestor leaves in view', () => {
    const long: readonly LayoutWord[] = run(0, EIGHTY_ONE)
    const truncated: LayoutNode = paragraph(long, {
      rects: [rect(0, 0, 400, 20)],
      overflowX: 'hidden',
      textOverflow: 'ellipsis',
    })
    const frame: LayoutNode = nodeOf({
      index: 1,
      parent: 0,
      rects: [rect(0, 0, 400, 20)],
      overflowX: 'hidden',
      overflowY: 'hidden',
    })
    const inFrame: LayoutNode = paragraph(long, { index: 2, parent: 1 })
    expect([findLongLines(pageOf(truncated)).length, findLongLines(pageOf(frame, inFrame)).length]).toEqual([0, 0])
  })

  it('is part of the resting scan', () => {
    expect(messages(restingCandidates(pageOf(paragraph(run(0, EIGHTY_ONE)))))).toEqual([
      "line-length: a line of 81 characters, beyond 80; narrow the text's measure: p.lead",
    ])
  })
})

describe('justified text', () => {
  const twoLines: readonly LayoutWord[] = [...run(0, latin(8, 8)), ...run(1, latin(8))]
  const oneLine: readonly LayoutWord[] = run(0, latin(8, 8))

  it('fails a block justified over two lines or more, in either spelling', () => {
    expect([
      messages(findJustifiedText(pageOf(paragraph(twoLines, { textAlign: 'justify' })))),
      messages(findJustifiedText(pageOf(paragraph(oneLine, { textAlign: 'justify-all' })))),
      messages(findJustifiedText(pageOf(paragraph(oneLine, { textAlignLast: 'justify' })))),
    ]).toEqual([
      ['text-justified: text justified to both edges over 2 lines; align it to one edge: p.lead'],
      ['text-justified: text justified to both edges over its only line; align it to one edge: p.lead'],
      ['text-justified: text justified to both edges over its only line; align it to one edge: p.lead'],
    ])
  })

  it('passes a one-line block under text-align: justify, which is set as its start edge sets it', () => {
    expect(findJustifiedText(pageOf(paragraph(oneLine, { textAlign: 'justify' })))).toEqual([])
  })

  it('passes text aligned to one edge or centred', () => {
    expect(
      ['start', 'left', 'right', 'end', 'center'].flatMap((textAlign: string): readonly CandidateFinding[] =>
        findJustifiedText(pageOf(paragraph(twoLines, { textAlign }))),
      ),
    ).toEqual([])
  })

  it('judges the block whose lines an inline element is set in, not the element inheriting the alignment', () => {
    const span: LayoutNode = nodeOf({
      index: 2,
      parent: 1,
      tag: 'span',
      display: 'inline',
      textAlign: 'justify',
      rects: [rect(0, 0, 100, 40)],
      words: twoLines,
    })
    expect([
      findJustifiedText(pageOf(paragraph([]), span)),
      messages(findJustifiedText(pageOf(paragraph([], { textAlign: 'justify' }), { ...span, textAlign: 'justify' }))),
    ]).toEqual([[], ['text-justified: text justified to both edges over 2 lines; align it to one edge: p.lead']])
  })

  it('is part of the resting scan', () => {
    expect(
      restingCandidates(pageOf(paragraph(twoLines, { textAlign: 'justify' }))).map(
        (candidate: CandidateFinding): string => candidate.rule,
      ),
    ).toEqual(['text-justified'])
  })
})

describe('text wrap', () => {
  const twoLines: readonly LayoutWord[] = [...run(0, latin(8, 8)), ...run(1, latin(8))]
  const oneLine: readonly LayoutWord[] = run(0, latin(8, 8))
  const wrapped = (textWrapStyle: string, fields: Partial<LayoutNode> = {}): LayoutSnapshot =>
    pageOf(paragraph(twoLines, { textWrapStyle, ...fields }))

  it('fails a block wrapped over two lines or more that breaks each line as late as it can', () => {
    expect([messages(findUnevenWrap(wrapped('auto'))), messages(findUnevenWrap(wrapped('stable')))]).toEqual([
      [
        'text-wrap: text wrapped over 2 lines under text-wrap-style: auto, which can leave one word alone on the ' +
          'last line; set text-wrap: pretty, or balance for a heading: p.lead',
      ],
      [
        'text-wrap: text wrapped over 2 lines under text-wrap-style: stable, which can leave one word alone on ' +
          'the last line; set text-wrap: pretty, or balance for a heading: p.lead',
      ],
    ])
  })

  it('passes a block that weighs its last lines, in either spelling', () => {
    expect([findUnevenWrap(wrapped('pretty')), findUnevenWrap(wrapped('balance'))]).toEqual([[], []])
  })

  it('passes a block set on one line, which has no last line to leave a word on', () => {
    expect(findUnevenWrap(pageOf(paragraph(oneLine, { textWrapStyle: 'auto' })))).toEqual([])
  })

  it('leaves preformatted text, code, form controls and text that never wraps to their own lines', () => {
    expect([
      findUnevenWrap(wrapped('auto', { tag: 'pre' })),
      findUnevenWrap(wrapped('auto', { tag: 'textarea' })),
      findUnevenWrap(wrapped('auto', { whiteSpace: 'pre-wrap' })),
      findUnevenWrap(wrapped('auto', { whiteSpace: 'nowrap' })),
      findUnevenWrap(wrapped('auto', { whiteSpace: 'pre' })),
    ]).toEqual([[], [], [], [], []])
  })

  it('leaves unjudged a block in an engine that cannot set pretty', () => {
    expect(findUnevenWrap(wrapped(''))).toEqual([])
  })

  it('ignores text nobody sees: hidden, hidden from assistive technology or visually hidden', () => {
    expect([
      findUnevenWrap(wrapped('auto', { visibility: 'hidden' })),
      findUnevenWrap(wrapped('auto', { isAriaHidden: true })),
      findUnevenWrap(wrapped('auto', { isClipped: true })),
    ]).toEqual([[], [], []])
  })

  it('judges the block whose lines an inline element is set in, by the block wrap', () => {
    const span: LayoutNode = nodeOf({
      index: 2,
      parent: 1,
      tag: 'span',
      display: 'inline',
      textWrapStyle: 'auto',
      rects: [rect(0, 0, 100, 40)],
      words: twoLines,
    })
    expect([
      findUnevenWrap(pageOf(paragraph([]), span)),
      messages(findUnevenWrap(pageOf(paragraph([], { textWrapStyle: 'auto' }), span))),
    ]).toEqual([
      [],
      [
        'text-wrap: text wrapped over 2 lines under text-wrap-style: auto, which can leave one word alone on the ' +
          'last line; set text-wrap: pretty, or balance for a heading: p.lead',
      ],
    ])
  })

  it('is part of the resting scan', () => {
    expect(restingCandidates(wrapped('auto')).map((candidate: CandidateFinding): string => candidate.rule)).toEqual([
      'text-wrap',
    ])
  })
})
