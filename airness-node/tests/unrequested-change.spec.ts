import { describe, expect, it } from 'vitest'
import { type PageChanges, unrequestedChangeMessages } from '@/unrequested-change'

const ORIGIN: string = 'http://127.0.0.1:3000'

const changes = (fields: Partial<PageChanges>): PageChanges => ({
  loaded: `${ORIGIN}/offers`,
  navigations: [`${ORIGIN}/offers`],
  popups: [],
  ...fields,
})

// Where the page moved to, as its message names it, or undefined when no message says it moved.
const unrequestedNavigation = (fields: Partial<PageChanges>): string | undefined =>
  unrequestedChangeMessages(changes({ ...fields, popups: [] }))
    .map(
      (message: string): string | undefined =>
        /^the page moved to (?<to>\S+) on its own/u.exec(message)?.groups?.['to'],
    )
    .find((to: string | undefined): boolean => to !== undefined)

describe('an unrequested navigation', () => {
  it('is a move to another path after the page loaded, by location or by a router', () => {
    expect([
      unrequestedNavigation({ navigations: [`${ORIGIN}/offers`, `${ORIGIN}/`] }),
      unrequestedNavigation({ navigations: [`${ORIGIN}/offers`, `${ORIGIN}/offers?x=1`, `${ORIGIN}/sign-in`] }),
    ]).toEqual(['/', '/sign-in'])
  })

  it('is a move to another origin', () => {
    expect(unrequestedNavigation({ navigations: [`${ORIGIN}/offers`, 'https://example.com/offers'] })).toBe(
      'https://example.com/offers',
    )
  })

  it('is not a change of hash, of query, or of a trailing slash on the same path', () => {
    expect([
      unrequestedNavigation({ navigations: [`${ORIGIN}/offers`, `${ORIGIN}/offers#terms`] }),
      unrequestedNavigation({ navigations: [`${ORIGIN}/offers`, `${ORIGIN}/offers?page=2`] }),
      unrequestedNavigation({ navigations: [`${ORIGIN}/offers`, `${ORIGIN}/offers/`] }),
    ]).toEqual([undefined, undefined, undefined])
  })

  it('is not an HTTP redirect, which lands before the first document', () => {
    expect(unrequestedNavigation({ loaded: `${ORIGIN}/sign-in`, navigations: [`${ORIGIN}/sign-in`] })).toBeUndefined()
  })

  it('is not a late navigation of the page that was there before this one committed', () => {
    expect(unrequestedNavigation({ navigations: [`${ORIGIN}/previous-elsewhere`, `${ORIGIN}/offers`] })).toBeUndefined()
  })

  it('is not judged when the loaded page never committed, or its address cannot be read', () => {
    expect([
      unrequestedNavigation({ navigations: [`${ORIGIN}/elsewhere`] }),
      unrequestedNavigation({ loaded: 'not a url', navigations: ['not a url', `${ORIGIN}/`] }),
      unrequestedNavigation({ navigations: [`${ORIGIN}/offers`, 'not a url'] }),
    ]).toEqual([undefined, undefined, undefined])
  })
})

describe('unrequested change messages', () => {
  it('name the path on the page origin it moved to, with its query', () => {
    expect(unrequestedChangeMessages(changes({ navigations: [`${ORIGIN}/offers`, `${ORIGIN}/?from=offers`] }))).toEqual(
      ['the page moved to /?from=offers on its own while it loaded, before any input'],
    )
  })

  it('name the whole address of another origin', () => {
    expect(unrequestedChangeMessages(changes({ navigations: [`${ORIGIN}/offers`, 'https://example.com/'] }))).toEqual([
      'the page moved to https://example.com/ on its own while it loaded, before any input',
    ])
  })

  it('report every window opened, with its address when it has one', () => {
    expect(unrequestedChangeMessages(changes({ popups: [`${ORIGIN}/newsletter`, 'about:blank', ''] }))).toEqual([
      'the page opened a new window at /newsletter on its own while it loaded, before any input',
      'the page opened a new window on its own while it loaded, before any input',
      'the page opened a new window on its own while it loaded, before any input',
    ])
  })

  it('report nothing for a page that stayed and opened nothing', () => {
    expect(unrequestedChangeMessages(changes({}))).toEqual([])
  })
})
