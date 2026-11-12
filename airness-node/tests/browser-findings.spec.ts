import { describe, expect, it } from 'vitest'
import {
  type BrowserFinding,
  distinctFindings,
  type FindingPlace,
  failingPlaces,
  findingAt,
  findingLine,
} from '@/browser-findings'

const PHONE: FindingPlace = {
  location: '/teams',
  state: 'resting',
  engine: 'chromium',
  viewport: { width: 390, height: 844 },
}

describe('a browser finding', () => {
  it('names its rule, location, viewport, engine and state before what is wrong', () => {
    expect(findingLine(findingAt(PHONE, 'small-text', 'text rendered at 10px: p.note'))).toBe(
      '[small-text] /teams @390x844 (chromium, resting): text rendered at 10px: p.note',
    )
  })

  it('leaves the viewport out of a finding that holds at every viewport', () => {
    expect(findingLine(findingAt({ ...PHONE, viewport: undefined }, 'font-load', 'face failed'))).toBe(
      '[font-load] /teams (chromium, resting): face failed',
    )
  })

  it('says a finding the signed-in visit made was made signed in', () => {
    expect(findingLine({ ...findingAt(PHONE, 'small-text', 'text rendered at 10px'), isSignedIn: true })).toBe(
      '[small-text] /teams @390x844 (chromium, resting, signed in): text rendered at 10px',
    )
  })

  it('keeps the same defect apart when the two visitors both found it', () => {
    const anonymous: BrowserFinding = findingAt(PHONE, 'overflow', 'page too wide')
    const signedIn: BrowserFinding = { ...anonymous, isSignedIn: true }
    expect(distinctFindings([anonymous, signedIn])).toEqual([anonymous, signedIn])
  })

  it('reports a finding repeated across measurements once', () => {
    const finding: BrowserFinding = findingAt(PHONE, 'overflow', 'page too wide', [3])
    expect(distinctFindings([finding, { ...finding, elements: [4] }])).toEqual([finding])
  })

  it('groups the elements to outline by the place they were found at', () => {
    const desktop: FindingPlace = {
      ...PHONE,
      viewport: { width: 1280, height: 800 },
    }
    expect(
      failingPlaces([
        findingAt(PHONE, 'a', 'x', [1]),
        findingAt(PHONE, 'b', 'y', [1, 2]),
        findingAt(desktop, 'a', 'x'),
      ]),
    ).toEqual([
      { ...PHONE, elements: [1, 2] },
      { ...desktop, elements: [] },
    ])
  })
})
