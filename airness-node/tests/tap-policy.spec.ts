// How the touch pass decides which elements a tap is aimed at, and whether the page answered one. Each element
// is stated as the facts the browser reads off it, and each answer as what changed after the tap.
import { describe, expect, it } from 'vitest'
import { isTapAnswered, type TapFacts, type TapResponse, tapIgnoredMessage, tapSignal } from '@/tap-policy'

const element = (fields: Partial<TapFacts>): TapFacts => ({
  tag: 'div',
  type: '',
  role: '',
  hasOnclick: false,
  cursor: 'auto',
  parentCursor: 'auto',
  hasHref: false,
  isFormButton: false,
  isLabelOfControl: false,
  isEditable: false,
  isDisabled: false,
  isInert: false,
  isRendered: true,
  isRoot: false,
  ...fields,
})

describe('which elements a tap is aimed at', () => {
  it('taps a button, which does nothing until a script answers it, in either spelling', () => {
    expect([
      tapSignal(element({ tag: 'button', type: 'button' })),
      tapSignal(element({ tag: 'button', type: 'submit' })),
      tapSignal(element({ tag: 'input', type: 'button' })),
    ]).toEqual(['a button element', 'a button element', 'a input element'])
  })

  it('taps what presents as operable by a role, an inline handler or a pointer cursor of its own', () => {
    expect([
      tapSignal(element({ role: 'button' })),
      tapSignal(element({ hasOnclick: true })),
      tapSignal(element({ cursor: 'pointer' })),
      tapSignal(element({ cursor: 'pointer', parentCursor: 'pointer' })),
      tapSignal(element({})),
    ]).toEqual(['role "button"', 'an inline onclick handler', 'a pointer cursor of its own', undefined, undefined])
  })

  it('leaves alone what the browser answers by itself', () => {
    expect(
      [
        element({ tag: 'a', cursor: 'pointer', hasHref: true }),
        element({ tag: 'button', type: 'submit', isFormButton: true }),
        element({ tag: 'label', cursor: 'pointer', isLabelOfControl: true }),
        element({ role: 'textbox', isEditable: true }),
        element({ tag: 'summary', cursor: 'pointer' }),
        element({ tag: 'select' }),
        element({ tag: 'textarea' }),
        element({ tag: 'input', type: 'checkbox', cursor: 'pointer' }),
        element({ tag: 'input', type: 'text' }),
      ].map((facts: TapFacts): string | undefined => tapSignal(facts)),
    ).toEqual([undefined, undefined, undefined, undefined, undefined, undefined, undefined, undefined, undefined])
  })

  it('leaves alone an inactive or unrendered element, and the document itself', () => {
    expect(
      [
        element({ tag: 'button', isDisabled: true }),
        element({ tag: 'button', isInert: true }),
        element({ tag: 'button', isRendered: false }),
        element({ tag: 'body', cursor: 'pointer', isRoot: true }),
      ].map((facts: TapFacts): string | undefined => tapSignal(facts)),
    ).toEqual([undefined, undefined, undefined, undefined])
  })
})

const NOTHING: TapResponse = {
  mutations: 0,
  hasMovedAddress: false,
  hasMovedHistory: false,
  hasChangedForm: false,
  hasToggledLayer: false,
  hasScrolled: false,
  hasReachedOut: false,
}

describe('whether a page answered a tap', () => {
  it('counts anything the page did as an answer', () => {
    expect(
      [
        { mutations: 1 },
        { hasMovedAddress: true },
        { hasMovedHistory: true },
        { hasChangedForm: true },
        { hasToggledLayer: true },
        { hasScrolled: true },
        { hasReachedOut: true },
      ].map((change: Partial<TapResponse>): boolean => isTapAnswered({ ...NOTHING, ...change })),
    ).toEqual([true, true, true, true, true, true, true])
  })

  it('reads a page that changed nothing as one that ignored the press, and says what a mouse answered', () => {
    expect([isTapAnswered(NOTHING), tapIgnoredMessage('div.swatch', 'a pointer cursor of its own')]).toEqual([
      false,
      'div.swatch presents as operable (a pointer cursor of its own) and answers a mouse click, but a tap on a ' +
        'touch screen changed nothing: no change to the document, its address, a form, a popover or dialog, its ' +
        'scroll or its requests; answer a tap as a click is answered, whatever the pointer and whether the device ' +
        'can hover',
    ])
  })
})
