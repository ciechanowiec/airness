import path from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  distinctLocations,
  e2eOutputDirectory,
  endedSessionProblems,
  hasSignedInVisitor,
  isAtRouteCeiling,
  isOwnAnswer,
  locationsToRevisit,
  opensOnlyWithSession,
  SIGN_IN_SETUP,
  SIGNED_IN_TAG,
  sessionFile,
  sessionProblems,
  structureOf,
  visitorDirectory,
  visitorOfTags,
} from '@/sweep-visitors'

const MEMBER: string = path.join(path.sep, 'work', 'member')
interface Located {
  readonly location: string
  readonly structure?: string
}

const SWEEP: string = path.join(MEMBER, 'airness-sweep')

const HOME: string = structureOf('main h1 a')
const HOME_SIGNED_IN: string = structureOf('main h1 a form button[type=submit]')

describe('who the sweep visits as', () => {
  it('visits signed in only a member that holds the sign-in setup', () => {
    const setup: string = path.join(MEMBER, SIGN_IN_SETUP)
    expect(hasSignedInVisitor(MEMBER, (file: string): boolean => file === setup)).toBe(true)
    expect(hasSignedInVisitor(MEMBER, (): boolean => false)).toBe(false)
  })

  it('reads the visitor from the tags of the running test', () => {
    expect(visitorOfTags(['@airness-chromium', SIGNED_IN_TAG])).toBe('signed-in')
    expect(visitorOfTags(['@airness-chromium'])).toBe('anonymous')
  })

  it('keeps the anonymous visit in the sweep directory and the signed-in one beneath it', () => {
    expect(visitorDirectory(SWEEP, 'anonymous')).toBe(SWEEP)
    expect(visitorDirectory(SWEEP, 'signed-in')).toBe(path.join(SWEEP, 'signed-in'))
  })

  it('leaves the session inside the signed-in visit directory of the run output', () => {
    const output: string = e2eOutputDirectory(MEMBER)
    expect(output).toBe(path.join(MEMBER, 'node_modules', '.airness', 'e2e', 'test-results'))
    expect(sessionFile(output)).toBe(path.join(output, 'airness-sweep', 'signed-in', 'session.json'))
  })
})

describe('the pages the signed-in passes visit', () => {
  it('reads two renders with the same elements in the same order as one structure, whatever their text', () => {
    expect(structureOf('main h1 a')).toBe(HOME)
    expect(HOME_SIGNED_IN).not.toBe(HOME)
  })

  it('skips a page the anonymous visit judged in the same structure', () => {
    expect(
      locationsToRevisit([{ location: '/about', structure: HOME }], [{ location: '/about', structure: HOME }]),
    ).toEqual([])
  })

  it('visits a page whose structure signing in changed', () => {
    const signedIn: Located = { location: '/', structure: HOME_SIGNED_IN }
    expect(locationsToRevisit([signedIn], [{ location: '/', structure: HOME }])).toEqual([signedIn])
  })

  it('visits a page only the signed-in visitor reached', () => {
    const tasks: Located = { location: '/tasks', structure: HOME }
    expect(locationsToRevisit([tasks], [{ location: '/', structure: HOME }])).toEqual([tasks])
  })

  it('visits a page whose anonymous structure is unknown rather than assume it unchanged', () => {
    const signedIn: Located = { location: '/', structure: HOME }
    expect(locationsToRevisit([signedIn], [{ location: '/' }])).toEqual([signedIn])
  })

  it('keeps one location for the routes that all forward to it', () => {
    expect(distinctLocations([{ location: '/login' }, { location: '/' }, { location: '/login' }])).toEqual([
      { location: '/login' },
      { location: '/' },
    ])
  })
})

describe('the route ceiling', () => {
  it('counts a route both visits reached once', () => {
    expect(isAtRouteCeiling(new Set<string>(['/', '/a']), ['/', '/a'], 3)).toBe(false)
  })

  it('counts the routes of the earlier visit towards the ceiling', () => {
    expect(isAtRouteCeiling(new Set<string>(['/', '/tasks']), ['/', '/about'], 3)).toBe(true)
  })

  it('counts only its own routes for the anonymous visit', () => {
    expect(isAtRouteCeiling(new Set<string>(['/', '/a']), [], 3)).toBe(false)
    expect(isAtRouteCeiling(new Set<string>(['/', '/a', '/b']), [], 3)).toBe(true)
  })
})

describe('the signed-in session', () => {
  it('counts only a 2xx status as a route answering itself', () => {
    expect([199, 200, 204, 299, 300, 302, 401].map((status: number): boolean => isOwnAnswer(status))).toEqual([
      false,
      true,
      true,
      true,
      false,
      false,
      false,
    ])
  })

  it('proves the session with a route that opens with it and forwards or refuses without it', () => {
    expect(opensOnlyWithSession(200, 302)).toBe(true)
    expect(opensOnlyWithSession(200, 401)).toBe(true)
    expect(opensOnlyWithSession(200, 200)).toBe(false)
    expect(opensOnlyWithSession(302, 302)).toBe(false)
  })

  it('names the visit that ended the session', () => {
    expect(sessionProblems({ probe: '/account', endedAt: '/bye', revisited: 3 })).toEqual([
      expect.stringMatching(/^visiting \/bye ended the signed-in session/u),
    ])
  })

  it('fails a signed-in visit that found nothing signing in changed', () => {
    expect(sessionProblems({ probe: undefined, endedAt: undefined, revisited: 0 })).toEqual([
      expect.stringContaining(`check that ${SIGN_IN_SETUP} signs in before it calls saveSweepSession`),
    ])
  })

  it('accepts a visit that proved its session or found pages that signing in changed', () => {
    expect(sessionProblems({ probe: '/account', endedAt: undefined, revisited: 0 })).toEqual([])
    expect(sessionProblems({ probe: undefined, endedAt: undefined, revisited: 2 })).toEqual([])
  })

  it('reports a session that no longer opens the probe once the sweep finished', () => {
    expect(endedSessionProblems('/account', 200)).toEqual([])
    expect(endedSessionProblems('/account', 302)).toEqual([
      expect.stringContaining('no longer opened /account once the sweep finished'),
    ])
    expect(endedSessionProblems('/account', undefined)).toHaveLength(1)
  })
})
