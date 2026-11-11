import { describe, expect, it } from 'vitest'
import {
  COMPOSITE_OWNERS,
  INTERACTIVE_ROLES,
  isWalkComplete,
  OPERABLE_SELECTOR,
  type PointerCandidate,
  pointerOnlyMessage,
  pointerOnlySignal,
} from '@/pointer-only'

// A `div` the keyboard never reached, presenting nothing yet: each case adds what it is about.
const candidate = (fields: Partial<PointerCandidate>): PointerCandidate => ({
  tag: 'div',
  role: '',
  hasOnclick: false,
  cursor: 'auto',
  parentCursor: 'auto',
  isReached: false,
  isDisabled: false,
  isInert: false,
  isRendered: true,
  isRoot: false,
  hasReachedOperableAncestor: false,
  hasReachedOperableDescendant: false,
  isInReachedGroup: false,
  isSameHrefReached: false,
  labelControl: 'none',
  ...fields,
})

const POINTER: Partial<PointerCandidate> = { cursor: 'pointer' }

describe('what presents an element as operable', () => {
  it('is a pointer cursor the element sets itself', () => {
    expect(pointerOnlySignal(candidate(POINTER))).toBe('a pointer cursor of its own')
  })

  it('is not a pointer cursor inherited from a link, a button or a label above it', () => {
    expect(pointerOnlySignal(candidate({ cursor: 'pointer', parentCursor: 'pointer' }))).toBeUndefined()
  })

  it('is any interactive role, whatever the cursor', () => {
    expect(INTERACTIVE_ROLES.map((role: string): string | undefined => pointerOnlySignal(candidate({ role })))).toEqual(
      INTERACTIVE_ROLES.map((role: string): string => `role "${role}"`),
    )
  })

  it('is an inline onclick handler', () => {
    expect(pointerOnlySignal(candidate({ hasOnclick: true }))).toBe('an inline onclick handler')
  })

  it('is not a structural role, an inherited cursor or a default one', () => {
    expect([
      pointerOnlySignal(candidate({ role: 'region' })),
      pointerOnlySignal(candidate({ role: 'presentation', cursor: 'default' })),
      pointerOnlySignal(candidate({ cursor: 'text' })),
      pointerOnlySignal(candidate({})),
    ]).toEqual([undefined, undefined, undefined, undefined])
  })
})

describe('a pointer-only element', () => {
  it('is a clickable div, a role without a tab stop, or an inline onclick the keyboard never reached', () => {
    expect([
      pointerOnlySignal(candidate(POINTER)),
      pointerOnlySignal(candidate({ role: 'button' })),
      pointerOnlySignal(candidate({ tag: 'span', hasOnclick: true })),
      pointerOnlySignal(candidate({ tag: 'a', ...POINTER })),
    ]).toEqual([
      'a pointer cursor of its own',
      'role "button"',
      'an inline onclick handler',
      'a pointer cursor of its own',
    ])
  })

  it('is not one the keyboard reached', () => {
    expect(pointerOnlySignal(candidate({ ...POINTER, isReached: true }))).toBeUndefined()
  })

  it('is not one a reached control stands in for: around it, inside it, beside it in its group, or at its address', () => {
    expect([
      pointerOnlySignal(candidate({ ...POINTER, hasReachedOperableAncestor: true })),
      pointerOnlySignal(candidate({ ...POINTER, hasReachedOperableDescendant: true })),
      pointerOnlySignal(candidate({ role: 'tab', isInReachedGroup: true })),
      pointerOnlySignal(candidate({ tag: 'input', ...POINTER, isInReachedGroup: true })),
      pointerOnlySignal(candidate({ tag: 'a', ...POINTER, isSameHrefReached: true })),
    ]).toEqual([undefined, undefined, undefined, undefined, undefined])
  })

  it('is not a label whose control the keyboard can focus, or whose control is disabled', () => {
    expect([
      pointerOnlySignal(candidate({ tag: 'label', ...POINTER, labelControl: 'focusable' })),
      pointerOnlySignal(candidate({ tag: 'label', ...POINTER, labelControl: 'disabled' })),
    ]).toEqual([undefined, undefined])
  })

  it('is a label whose control the keyboard cannot focus', () => {
    expect(pointerOnlySignal(candidate({ tag: 'label', ...POINTER, labelControl: 'unfocusable' }))).toBe(
      'a pointer cursor of its own',
    )
  })

  it('is not disabled, inert, unrendered, or the document itself', () => {
    expect([
      pointerOnlySignal(candidate({ ...POINTER, isDisabled: true })),
      pointerOnlySignal(candidate({ ...POINTER, isInert: true })),
      pointerOnlySignal(candidate({ ...POINTER, isRendered: false })),
      pointerOnlySignal(candidate({ tag: 'body', ...POINTER, isRoot: true })),
    ]).toEqual([undefined, undefined, undefined, undefined])
  })
})

describe('a keyboard walk', () => {
  it('crossed the page when focus left the document or came back to the first stop before the cap', () => {
    expect(isWalkComplete({ reached: 4, cap: 6, hasWrapped: true })).toBe(true)
  })

  it('did not cross the page when it ran out of stops', () => {
    expect(isWalkComplete({ reached: 6, cap: 6, hasWrapped: true })).toBe(false)
  })

  it('did not cross the page when focus stayed on a later stop, as inside an embedded frame', () => {
    expect(isWalkComplete({ reached: 3, cap: 6, hasWrapped: false })).toBe(false)
  })
})

describe('the selectors the page reads', () => {
  it('count every interactive role and every native control as operable', () => {
    expect(OPERABLE_SELECTOR.split(', ')).toEqual(
      expect.arrayContaining([
        'a[href]',
        'button',
        'summary',
        'select',
        'textarea',
        'input:not([type="hidden"])',
        '[role="button"]',
        '[role="treeitem"]',
      ]),
    )
  })

  it('name the composite widgets the arrow keys move within, native lists among them', () => {
    expect(COMPOSITE_OWNERS.split(', ')).toEqual(
      expect.arrayContaining(['[role="tablist"]', '[role="radiogroup"]', '[role="menu"]', 'select', 'datalist']),
    )
  })
})

describe('the pointer-only message', () => {
  it('names the element and what presents it as operable', () => {
    expect(pointerOnlyMessage('div.card "Open"', 'a pointer cursor of its own')).toBe(
      'div.card "Open" presents as operable (a pointer cursor of its own), but the keyboard never reaches it and ' +
        'no control the keyboard reaches stands in for it',
    )
  })
})
