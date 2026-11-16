// How long a post takes to read, estimated from the words of its rich text.

/** The reading speed the estimate assumes, in words a minute. */
const WORDS_PER_MINUTE: number = 220
const SHORTEST_READ: number = 1
const WORD: RegExp = /\S+/gu

const isRecord = (value: unknown): value is Readonly<Record<string, unknown>> =>
  typeof value === 'object' && value !== null

const isList = (value: unknown): value is readonly unknown[] => Array.isArray(value)

const wordsOf = (text: unknown): number => (typeof text === 'string' ? (text.match(WORD)?.length ?? 0) : 0)

// A Lexical document is a tree of nodes: text nodes carry `text`, every other node carries `children`.
const countWords = (node: unknown): number => {
  if (!isRecord(node)) {
    return 0
  }
  const children: unknown = node['children']
  const below: number = isList(children)
    ? children.reduce((sum: number, child: unknown): number => sum + countWords(child), 0)
    : 0
  return wordsOf(node['text']) + below
}

/**
 * The whole minutes a document takes to read, never less than one.
 * @param document a rich-text value as Payload stores it, `{ root: { children: [...] } }`.
 * @returns the minutes.
 */
export const readingMinutes = (document: unknown): number => {
  const words: number = countWords(isRecord(document) ? document['root'] : undefined)
  return Math.max(SHORTEST_READ, Math.ceil(words / WORDS_PER_MINUTE))
}
