// The htmx fragments a page names: which addresses are requested, and what their answers prove.
import { describe, expect, it } from 'vitest'
import { type FragmentAnswer, fragmentUrls, judgeFragments } from '@/htmx-fragments.js'

const ORIGIN: string = 'https://localhost:8443'

describe('fragmentUrls', () => {
  it('resolves each value against the page, keeps the query, and drops blanks and duplicates', () => {
    expect(fragmentUrls(['rows?page=2', '/board/row', '', '  ', '/board/row'], `${ORIGIN}/board/`, ORIGIN)).toEqual([
      `${ORIGIN}/board/rows?page=2`,
      `${ORIGIN}/board/row`,
    ])
  })

  it('requests nothing on another origin, and nothing it cannot parse', () => {
    expect(fragmentUrls(['https://example.com/row', 'https://[::1'], `${ORIGIN}/`, ORIGIN)).toEqual([])
  })
})

const answer = (url: string, status: number, contentType?: string): FragmentAnswer => ({
  url: `${ORIGIN}${url}`,
  status,
  contentType,
})

describe('judgeFragments', () => {
  it('credits the path of each fragment that rendered HTML', () => {
    expect(
      judgeFragments([
        answer('/board/row?page=1', 200, 'text/html;charset=UTF-8'),
        answer('/board/row?page=2', 200, 'text/html'),
      ]),
    ).toEqual({ credited: ['/board/row'], problems: [] })
  })

  it('credits nothing for a redirect, a refusal or an answer that is not HTML, and reports none of them', () => {
    expect(
      judgeFragments([
        answer('/account/row', 302, 'text/html'),
        answer('/search', 400, 'text/html'),
        answer('/status', 200, 'application/json'),
        answer('/empty', 204),
      ]),
    ).toEqual({ credited: [], problems: [] })
  })

  it('reports a server error and a fragment that did not answer', () => {
    expect(judgeFragments([answer('/board/row', 500, 'text/html'), answer('/board/cell', 0)]).problems).toEqual([
      `htmx fragment ${ORIGIN}/board/row answered 500`,
      `htmx fragment ${ORIGIN}/board/cell did not answer`,
    ])
  })
})
