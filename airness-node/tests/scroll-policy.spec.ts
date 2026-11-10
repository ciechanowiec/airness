import { describe, expect, it } from 'vitest'
import {
  hasMoved,
  hijackMessage,
  intendedRest,
  type ScrollStep,
  scrollDeltas,
  unrevealedMessage,
} from '@/scroll-policy'

const step = (fields: Partial<ScrollStep>): ScrollStep => ({
  from: 0,
  delta: 422,
  rest: 422,
  travelled: 422,
  maximum: 5000,
  ...fields,
})

describe('scroll deltas', () => {
  it('crosses a document down and back in half-viewport gestures', () => {
    expect(scrollDeltas(1000, 844)).toEqual([422, 422, 422, -422, -422, -422])
  })

  it('crosses a long document in at most ten gestures each way', () => {
    const deltas: readonly number[] = scrollDeltas(20_000, 844)
    expect([deltas.length, deltas[0], deltas.at(-1)]).toEqual([20, 2000, -2000])
  })

  it('takes no gesture on a document that does not scroll, or barely does', () => {
    expect([scrollDeltas(0, 844), scrollDeltas(2, 844), scrollDeltas(1000, 0)]).toEqual([[], [], []])
  })
})

describe('where a gesture leaves the document', () => {
  it('is its start plus its delta, within the scroll range', () => {
    expect([
      intendedRest(step({ from: 100, delta: 422 })),
      intendedRest(step({ from: 4800, delta: 422 })),
      intendedRest(step({ from: 100, delta: -422 })),
    ]).toEqual([522, 5000, 0])
  })
})

describe('scroll hijack', () => {
  it('passes a document resting where the gesture left it, eased to within the tolerance', () => {
    expect([
      hijackMessage(step({})),
      hijackMessage(step({ rest: 423.5 })),
      hijackMessage(step({ from: 4800, rest: 5000, travelled: 200 })),
    ]).toEqual([undefined, undefined, undefined])
  })

  it('fails a document snapped back to where it started, or run on past the gesture', () => {
    expect([
      hijackMessage(step({ rest: 0, travelled: 422 })),
      hijackMessage(step({ from: 1000, delta: -50, rest: 200, travelled: 800 })),
    ]).toEqual([
      'a wheel gesture of 422px from 0px came to rest at 0px instead of 422px: the page moved the document ' +
        'after the reader stopped; scroll natively, without snapping to sections or easing past the gesture',
      'a wheel gesture of -50px from 1000px came to rest at 200px instead of 950px: the page moved the document ' +
        'after the reader stopped; scroll natively, without snapping to sections or easing past the gesture',
    ])
  })

  it('fails a document still moving at the ceiling', () => {
    expect(hijackMessage(step({ rest: undefined }))).toBe(
      'a wheel gesture of 422px from 0px left the document still moving 5s later; scroll natively, and ' +
        'animate scroll-linked content without moving the document',
    )
  })

  it('passes a gesture that never moved the document, which went to an inner scroller instead', () => {
    expect([hasMoved(step({ rest: 0, travelled: 0 })), hijackMessage(step({ rest: 0, travelled: 1 }))]).toEqual([
      false,
      undefined,
    ])
  })
})

describe('unrevealed content', () => {
  it('names the element and the bound it missed', () => {
    expect(unrevealedMessage('h2 "Season ahead"')).toBe(
      'h2 "Season ahead" is scrolled into the upper half of the view but stays fully transparent 1.5s after ' +
        'the document comes to rest; reveal it when any of it enters the viewport, or show it without waiting',
    )
  })
})
