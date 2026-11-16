import { describe, expect, it } from 'vitest'

import { readingMinutes } from '@/lib/reading-time'

const WORDS_PER_MINUTE: number = 220

const textNode = (text: string): Readonly<Record<string, unknown>> => ({ type: 'text', text })

const documentOf = (...paragraphs: readonly string[]): Readonly<Record<string, unknown>> => ({
  root: {
    type: 'root',
    children: paragraphs.map((text: string) => ({ type: 'paragraph', children: [textNode(text)] })),
  },
})

const words = (count: number): string => Array.from({ length: count }, () => 'word').join(' ')

describe('readingMinutes', () => {
  it('rounds a document up to the next whole minute', () => {
    expect(readingMinutes(documentOf(words(WORDS_PER_MINUTE), words(1)))).toBe(2)
  })

  it('counts words in nested nodes, such as list items', () => {
    const nested: Readonly<Record<string, unknown>> = {
      root: {
        children: [
          { type: 'list', children: [{ type: 'listitem', children: [textNode(words(WORDS_PER_MINUTE * 2))] }] },
        ],
      },
    }
    expect(readingMinutes(nested)).toBe(2)
  })

  it('takes a short document as one minute', () => {
    expect(readingMinutes(documentOf('A few words.'))).toBe(1)
  })

  it('takes an empty or malformed document as one minute', () => {
    expect([readingMinutes(undefined), readingMinutes({ root: 'nothing' }), readingMinutes(documentOf())]).toEqual([
      1, 1, 1,
    ])
  })
})
