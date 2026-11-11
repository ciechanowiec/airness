import { describe, expect, it } from 'vitest'
import {
  type AnimationSample,
  animationMessage,
  animationSampleOf,
  parallaxMessage,
  type RecordedAnimation,
  smoothScrollMessage,
  type TransformTrack,
} from '@/reduced-motion-policy'

const LABEL: string = 'div.panel'
const SCROLL: string = 'a scroll'

// A panel sliding in over 600ms, which the reader's scroll started.
const sample = (fields: Partial<AnimationSample>): AnimationSample => ({
  kind: 'css-transition',
  timeline: 'time',
  properties: [{ name: 'transform', values: ['translateY(40px)', 'none'] }],
  activeMs: 600,
  width: 320,
  height: 200,
  wasRunningBefore: false,
  isOnActivatedControl: false,
  ...fields,
})

const recorded = (fields: Partial<RecordedAnimation>): RecordedAnimation => ({
  constructorName: 'CSSTransition',
  isTimeDriven: true,
  keyframes: [
    { offset: 0, computedOffset: 0, easing: 'ease', composite: 'auto', transform: 'translateY(40px)' },
    { offset: 1, computedOffset: 1, easing: 'linear', composite: 'auto', transform: 'none' },
  ],
  activeMs: 600,
  width: 320,
  height: 200,
  wasRunningBefore: false,
  isOnActivatedControl: false,
  ...fields,
})

const MOTION_MIN_MS: number = 100
const MOTION_MIN_AREA: number = 2304

// What the browser recorded, as the verdicts read its properties.
const keyframeProperties = (keyframes: RecordedAnimation['keyframes']): AnimationSample['properties'] =>
  animationSampleOf(recorded({ keyframes })).properties

// The properties a sample moves, as its message names them, or none when it reports no motion.
const movingProperties = (properties: AnimationSample['properties']): readonly string[] =>
  /moves \((?<moving>.*), 600ms\)/u
    .exec(animationMessage(LABEL, sample({ properties }), SCROLL) ?? '')
    ?.groups?.['moving']?.split(', ') ?? []

const track = (fields: Partial<TransformTrack>): TransformTrack => ({
  values: ['none', 'none', 'matrix(1, 0, 0, 1, 0, -40)', 'matrix(1, 0, 0, 1, 0, -80)'],
  width: 390,
  height: 300,
  isAnimated: false,
  ...fields,
})

describe('moving properties', () => {
  it('are transforms and their individual properties, insets, motion paths and margins', () => {
    const names: readonly string[] = [
      'transform',
      'translate',
      'rotate',
      'scale',
      'top',
      'left',
      'right',
      'bottom',
      'inset',
      'inset-inline-start',
      'offset-distance',
      'offset-path',
      'margin',
      'margin-top',
    ]
    expect(movingProperties(names.map((name: string) => ({ name, values: ['0px', '40px'] })))).toEqual(names)
  })

  it('are not fades, colours or sizes', () => {
    expect(
      movingProperties([
        { name: 'opacity', values: ['0', '1'] },
        { name: 'color', values: ['rgb(0, 0, 0)', 'rgb(255, 0, 0)'] },
        { name: 'background-color', values: ['white', 'black'] },
        { name: 'height', values: ['0px', '200px'] },
      ]),
    ).toEqual([])
  })

  it('count a single keyframe, which animates from the element own value, and not keyframes that agree', () => {
    expect([
      movingProperties([{ name: 'transform', values: ['translateX(100%)'] }]),
      movingProperties([{ name: 'transform', values: ['none', 'none'] }]),
    ]).toEqual([['transform'], []])
  })

  it('name each property once, however many animations or keyframes carry it', () => {
    expect(
      movingProperties([
        { name: 'translate', values: ['0px', '10px', '20px'] },
        { name: 'translate', values: ['20px', '0px'] },
      ]),
    ).toEqual(['translate'])
  })
})

describe('an animation under reduced motion', () => {
  it('fails as a CSS transition, a CSS animation or a Web Animation the reader set off', () => {
    expect(
      (['css-transition', 'css-animation', 'web-animation'] as const).map((kind) =>
        animationMessage(LABEL, sample({ kind }), SCROLL),
      ),
    ).toEqual([
      `${LABEL} moves (transform, 600ms) after a scroll while the reader asks for reduced motion`,
      `${LABEL} moves (transform, 600ms) after a scroll while the reader asks for reduced motion`,
      `${LABEL} moves (transform, 600ms) after a scroll while the reader asks for reduced motion`,
    ])
  })

  it('fails endlessly when it never ends', () => {
    expect(animationMessage(LABEL, sample({ activeMs: null }), 'opening button "Menu"')).toBe(
      `${LABEL} moves (transform, endlessly) after opening button "Menu" while the reader asks for reduced motion`,
    )
  })

  it('passes a fade', () => {
    expect(
      animationMessage(LABEL, sample({ properties: [{ name: 'opacity', values: ['0', '1'] }] }), SCROLL),
    ).toBeUndefined()
  })

  it('passes the near-instant transition a reduced-motion reset leaves', () => {
    expect([
      animationMessage(LABEL, sample({ activeMs: 0.01 }), SCROLL),
      animationMessage(LABEL, sample({ activeMs: MOTION_MIN_MS - 1 }), SCROLL),
      animationMessage(LABEL, sample({ activeMs: MOTION_MIN_MS }), SCROLL) === undefined,
    ]).toEqual([undefined, undefined, false])
  })

  it('passes an icon turning, smaller than a fingertip target', () => {
    expect([
      animationMessage(LABEL, sample({ width: 24, height: 24 }), SCROLL),
      animationMessage(LABEL, sample({ width: MOTION_MIN_AREA, height: 1 }), SCROLL) === undefined,
    ]).toEqual([undefined, false])
  })

  it('passes what was running before the reader acted, and what moves the control the reader pressed', () => {
    expect([
      animationMessage(LABEL, sample({ wasRunningBefore: true }), SCROLL),
      animationMessage(LABEL, sample({ isOnActivatedControl: true }), 'opening button "Menu"'),
    ]).toEqual([undefined, undefined])
  })

  it('fails a scroll-driven animation that moves content, however long it was running', () => {
    expect(animationMessage(LABEL, sample({ timeline: 'scroll', wasRunningBefore: true, activeMs: 0 }), SCROLL)).toBe(
      `${LABEL} moves with the scroll (transform) while the reader asks for reduced motion`,
    )
  })

  it('passes a scroll-driven fade', () => {
    expect(
      animationMessage(
        LABEL,
        sample({ timeline: 'scroll', properties: [{ name: 'opacity', values: ['0', '1'] }] }),
        SCROLL,
      ),
    ).toBeUndefined()
  })
})

describe('an element a script moves as the reader scrolls', () => {
  it('fails when its transform held still at rest and changed with every scroll', () => {
    expect(parallaxMessage(LABEL, track({}))).toBe(
      `${LABEL} moves with the scroll (its transform changes on every scroll, from none to ` +
        'matrix(1, 0, 0, 1, 0, -80)) while the reader asks for reduced motion',
    )
  })

  it('passes one that changed once and held, as a header hidden on the way down', () => {
    expect(
      parallaxMessage(LABEL, track({ values: ['none', 'none', 'translateY(-100%)', 'translateY(-100%)'] })),
    ).toBeUndefined()
  })

  it('passes one that moves on its own, before any scroll', () => {
    expect(
      parallaxMessage(LABEL, track({ values: ['translateX(0px)', 'translateX(10px)', 'translateX(20px)', 'x'] })),
    ).toBeUndefined()
  })

  it('passes one an animation drives, which the animation samples judge, and one too small to matter', () => {
    expect([
      parallaxMessage(LABEL, track({ isAnimated: true })),
      parallaxMessage(LABEL, track({ width: 20, height: 20 })),
    ]).toEqual([undefined, undefined])
  })

  it('passes one read too few times to tell', () => {
    expect([
      parallaxMessage(LABEL, track({ values: ['none', 'none', 'translateY(10px)'] })),
      parallaxMessage(LABEL, track({ values: [] })),
    ]).toEqual([undefined, undefined])
  })
})

describe('smooth scrolling under reduced motion', () => {
  it('fails a document that scrolls smoothly to its own fragments', () => {
    expect(smoothScrollMessage('smooth', true)).toBe(
      'the document scrolls smoothly to its in-page links (scroll-behavior: smooth) while the reader asks for ' +
        'reduced motion',
    )
  })

  it('passes a document that jumps, or that has no fragment to scroll to', () => {
    expect([smoothScrollMessage('auto', true), smoothScrollMessage('smooth', false)]).toEqual([undefined, undefined])
  })
})

describe('keyframe properties', () => {
  it('name each animated property in CSS spelling, leaving out the keys that time a frame', () => {
    expect(
      keyframeProperties([
        { offset: 0, marginTop: '0px', '--lift': '0', opacity: 0 },
        { offset: 1, marginTop: '20px', '--lift': '1', opacity: 1 },
      ]),
    ).toEqual([
      { name: 'margin-top', values: ['0px', '20px'] },
      { name: '--lift', values: ['0', '1'] },
      { name: 'opacity', values: ['0', '1'] },
    ])
  })

  it('keep only the values a frame gives, as a property named in one frame of several is', () => {
    expect(
      keyframeProperties([
        { translate: '0px', offset: 0 },
        { offset: 1, rotate: null },
      ]),
    ).toEqual([
      { name: 'translate', values: ['0px'] },
      { name: 'rotate', values: [] },
    ])
  })
})

describe('a recorded animation', () => {
  it('is described by the kind its class names, a CSS transition, a CSS animation or a script animation', () => {
    expect(
      ['CSSTransition', 'CSSAnimation', 'Animation'].map(
        (constructorName: string): AnimationSample['kind'] => animationSampleOf(recorded({ constructorName })).kind,
      ),
    ).toEqual(['css-transition', 'css-animation', 'web-animation'])
  })

  it('runs on the scroll when it is not on the document timeline', () => {
    expect([
      animationSampleOf(recorded({})).timeline,
      animationSampleOf(recorded({ isTimeDriven: false })).timeline,
    ]).toEqual(['time', 'scroll'])
  })

  it('never ends when its duration is infinite, and lasts no time when its duration is unreadable', () => {
    expect([
      animationSampleOf(recorded({ activeMs: Number.POSITIVE_INFINITY })).activeMs,
      animationSampleOf(recorded({ activeMs: Number.NaN })).activeMs,
      animationSampleOf(recorded({})).activeMs,
    ]).toEqual([null, 0, 600])
  })

  it('carries its properties, box and attribution into the sample the verdicts read', () => {
    expect(animationSampleOf(recorded({ wasRunningBefore: true, isOnActivatedControl: true }))).toEqual(
      sample({ wasRunningBefore: true, isOnActivatedControl: true }),
    )
  })
})
