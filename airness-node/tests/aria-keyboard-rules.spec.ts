import { describe, expect, it } from 'vitest'
import {
  type ArrowMove,
  type CompositeProbe,
  type CompositeWidget,
  compositeArrows,
  compositeDefects,
  type DialogProbe,
  dialogDefects,
  hasMoved,
  NO_ITEM,
  OUTSIDE,
} from '@/aria-keyboard-rules'

// The probe a well-behaved modal dialog leaves, whichever spelling opened it: a native `<dialog>` opened with
// `showModal()`, `role="dialog"` with `aria-modal="true"`, or `role="alertdialog"` with it. The sweep reads
// each spelling into the same shape, so one shape per behaviour stands for all three.
const modal: DialogProbe = {
  isModal: true,
  isFocusMovedIn: true,
  doesTabLeave: false,
  doesShiftTabLeave: false,
  isClosedOnEscape: true,
  isOpenerPresent: true,
  focusAfterClose: 'opener',
}

describe('dialogDefects', () => {
  it('accepts a modal dialog that takes focus, keeps it, closes on Escape and returns focus', () => {
    expect(dialogDefects(modal)).toEqual([])
  })

  it('reports focus left behind when the dialog opens', () => {
    expect(dialogDefects({ ...modal, isFocusMovedIn: false })).toEqual([
      'focus does not move into the dialog when it opens',
    ])
  })

  it('reports Tab and Shift+Tab leaving a modal dialog, each on its own', () => {
    expect([
      dialogDefects({ ...modal, doesTabLeave: true }),
      dialogDefects({ ...modal, doesShiftTabLeave: true }),
      dialogDefects({ ...modal, doesTabLeave: true, doesShiftTabLeave: true }),
    ]).toEqual([
      ['Tab moves focus out of the open modal dialog'],
      ['Shift+Tab moves focus out of the open modal dialog'],
      ['Tab moves focus out of the open modal dialog', 'Shift+Tab moves focus out of the open modal dialog'],
    ])
  })

  it('lets focus tab out of a non-modal dialog, which is part of the page', () => {
    expect(dialogDefects({ ...modal, isModal: false, doesTabLeave: true, doesShiftTabLeave: true })).toEqual([])
  })

  it('still holds a non-modal dialog to focus moving in, Escape and focus return', () => {
    const open: DialogProbe = { ...modal, isModal: false }
    expect([
      dialogDefects({ ...open, isFocusMovedIn: false }),
      dialogDefects({ ...open, isClosedOnEscape: false }),
      dialogDefects({ ...open, focusAfterClose: 'elsewhere' }),
    ]).toEqual([
      ['focus does not move into the dialog when it opens'],
      ['Escape does not close it'],
      ['focus does not return to its opener after Escape closes it'],
    ])
  })

  it('judges focus return only once Escape closed the dialog', () => {
    expect(dialogDefects({ ...modal, isClosedOnEscape: false, focusAfterClose: 'lost' })).toEqual([
      'Escape does not close it',
    ])
  })

  it('requires focus on the opener while the opener remains, wherever else it went', () => {
    expect([
      dialogDefects({ ...modal, focusAfterClose: 'elsewhere' }),
      dialogDefects({ ...modal, focusAfterClose: 'lost' }),
    ]).toEqual([
      ['focus does not return to its opener after Escape closes it'],
      ['focus does not return to its opener after Escape closes it'],
    ])
  })

  it('accepts any perceivable element once the opener went away, and rejects focus lost to nowhere', () => {
    const gone: DialogProbe = { ...modal, isOpenerPresent: false }
    expect([
      dialogDefects({ ...gone, focusAfterClose: 'elsewhere' }),
      dialogDefects({ ...gone, focusAfterClose: 'opener' }),
      dialogDefects({ ...gone, focusAfterClose: 'lost' }),
    ]).toEqual([[], [], ['focus is lost after Escape closes it, and its opener is gone']])
  })
})

const widget = (role: string, overrides: Partial<CompositeWidget> = {}): CompositeWidget => ({
  role,
  orientation: undefined,
  isRightToLeft: false,
  ...overrides,
})

describe('compositeArrows', () => {
  it('moves a horizontal tab list and menu bar with the horizontal keys', () => {
    expect([compositeArrows(widget('tablist')), compositeArrows(widget('menubar'))]).toEqual([
      [{ next: 'ArrowRight', previous: 'ArrowLeft', isOrdered: true }],
      [{ next: 'ArrowRight', previous: 'ArrowLeft', isOrdered: true }],
    ])
  })

  it('turns the horizontal keys around in a right-to-left widget', () => {
    expect(compositeArrows(widget('tablist', { isRightToLeft: true }))).toEqual([
      { next: 'ArrowLeft', previous: 'ArrowRight', isOrdered: true },
    ])
  })

  it('moves a menu, list box and tree with the vertical keys, whatever the direction', () => {
    const vertical: readonly unknown[] = [{ next: 'ArrowDown', previous: 'ArrowUp', isOrdered: true }]
    expect([
      compositeArrows(widget('menu')),
      compositeArrows(widget('listbox')),
      compositeArrows(widget('tree', { isRightToLeft: true })),
    ]).toEqual([vertical, vertical, vertical])
  })

  it('follows a declared aria-orientation over the one the role implies', () => {
    expect([
      compositeArrows(widget('tablist', { orientation: 'vertical' })),
      compositeArrows(widget('listbox', { orientation: 'horizontal' })),
      compositeArrows(widget('menu', { orientation: 'horizontal', isRightToLeft: true })),
    ]).toEqual([
      [{ next: 'ArrowDown', previous: 'ArrowUp', isOrdered: true }],
      [{ next: 'ArrowRight', previous: 'ArrowLeft', isOrdered: true }],
      [{ next: 'ArrowLeft', previous: 'ArrowRight', isOrdered: true }],
    ])
  })

  it('lets a radio group without an orientation, a role or a native group alike, answer either axis', () => {
    expect(compositeArrows(widget('radiogroup'))).toEqual([
      { next: 'ArrowDown', previous: 'ArrowUp', isOrdered: true },
      { next: 'ArrowRight', previous: 'ArrowLeft', isOrdered: true },
    ])
    expect(compositeArrows(widget('radiogroup', { orientation: 'horizontal' }))).toEqual([
      { next: 'ArrowRight', previous: 'ArrowLeft', isOrdered: true },
    ])
  })

  it('moves a grid and a tree grid along both axes without judging their order', () => {
    const both: readonly unknown[] = [
      { next: 'ArrowDown', previous: 'ArrowUp', isOrdered: false },
      { next: 'ArrowRight', previous: 'ArrowLeft', isOrdered: false },
    ]
    expect([compositeArrows(widget('grid')), compositeArrows(widget('treegrid', { orientation: 'vertical' }))]).toEqual(
      [both, both],
    )
  })
})

describe('hasMoved', () => {
  it('counts only a move onto another item', () => {
    expect([
      hasMoved({ key: 'ArrowDown', from: 0, to: 1 }),
      hasMoved({ key: 'ArrowDown', from: NO_ITEM, to: 0 }),
      hasMoved({ key: 'ArrowDown', from: 1, to: 1 }),
      hasMoved({ key: 'ArrowDown', from: 1, to: NO_ITEM }),
      hasMoved({ key: 'ArrowDown', from: 1, to: OUTSIDE }),
    ]).toEqual([true, true, false, false, false])
  })
})

const ENABLED_THREE: readonly boolean[] = [false, false, false]
const SIX_ENABLED: readonly boolean[] = [false, false, false, false, false, false]

const tablist = (moves: readonly ArrowMove[], overrides: Partial<CompositeProbe> = {}): CompositeProbe => ({
  widget: widget('tablist'),
  isPopup: false,
  disabled: ENABLED_THREE,
  tabbableItems: 1,
  hasTabStop: true,
  doesTabStayInside: false,
  moves,
  ...overrides,
})

describe('compositeDefects: the Tab stop', () => {
  it('accepts a roving tabindex, one item in the Tab sequence', () => {
    expect(compositeDefects(tablist([{ key: 'ArrowRight', from: 0, to: 1 }]))).toEqual([])
  })

  it('accepts an aria-activedescendant widget, the widget itself the Tab stop and no item tabbable', () => {
    expect(
      compositeDefects(
        tablist([{ key: 'ArrowDown', from: NO_ITEM, to: 0 }], { widget: widget('listbox'), tabbableItems: 0 }),
      ),
    ).toEqual([])
  })

  it('reports a tab list whose every tab is in the Tab sequence', () => {
    expect(compositeDefects(tablist([{ key: 'ArrowRight', from: 0, to: 1 }], { tabbableItems: 3 }))).toEqual([
      '3 of its items are in the Tab sequence; a composite widget is one Tab stop: keep one item at tabindex 0 ' +
        'and the others at -1, or focus the widget and use aria-activedescendant',
    ])
  })

  it('reports a widget the Tab sequence never reaches', () => {
    expect(compositeDefects(tablist([], { tabbableItems: 0, hasTabStop: false }))).toEqual([
      'nothing in it is in the Tab sequence, so the keyboard cannot reach it',
    ])
  })

  it('reports Tab landing inside the widget, as a button nested in a tab makes it', () => {
    expect(compositeDefects(tablist([{ key: 'ArrowRight', from: 0, to: 1 }], { doesTabStayInside: true }))).toEqual([
      'Tab moves to another element inside it instead of leaving it',
    ])
  })

  it('leaves Tab to the pattern of a widget a disclosure revealed, as a menu closes on it', () => {
    expect(
      compositeDefects(
        tablist([{ key: 'ArrowDown', from: 0, to: 1 }], {
          widget: widget('menu'),
          isPopup: true,
          hasTabStop: false,
          tabbableItems: 0,
          doesTabStayInside: true,
        }),
      ),
    ).toEqual([])
  })
})

describe('compositeDefects: the arrow keys', () => {
  it('reports a tab list the arrow keys do not move', () => {
    expect(
      compositeDefects(
        tablist([
          { key: 'ArrowRight', from: 0, to: 0 },
          { key: 'ArrowLeft', from: 0, to: 0 },
        ]),
      ),
    ).toEqual(['ArrowRight, ArrowLeft do not move focus among its items'])
  })

  it('reports an active descendant the arrow keys never set', () => {
    expect(
      compositeDefects(
        tablist([{ key: 'ArrowDown', from: NO_ITEM, to: NO_ITEM }], { widget: widget('listbox'), tabbableItems: 0 }),
      ),
    ).toEqual(['ArrowDown does not move focus among its items'])
  })

  it('accepts the previous key when the next one stops at the last item without wrapping', () => {
    expect(
      compositeDefects(
        tablist([
          { key: 'ArrowRight', from: 2, to: 2 },
          { key: 'ArrowLeft', from: 2, to: 1 },
        ]),
      ),
    ).toEqual([])
  })

  it('accepts a wrap from the last item to the first', () => {
    expect(compositeDefects(tablist([{ key: 'ArrowRight', from: 2, to: 0 }]))).toEqual([])
  })

  it('reports a key that moves focus out of the widget', () => {
    expect(compositeDefects(tablist([{ key: 'ArrowRight', from: 0, to: OUTSIDE }]))).toEqual([
      'ArrowRight moves focus out of it',
    ])
  })

  it('reports the next key moving backwards, as a right-to-left tab list wired left to right does', () => {
    expect(
      compositeDefects(
        tablist([{ key: 'ArrowLeft', from: 1, to: 0 }], { widget: widget('tablist', { isRightToLeft: true }) }),
      ),
    ).toEqual(['ArrowLeft moves to the previous item; in this widget it moves to the next one, and ArrowRight back'])
  })

  it('reports a backwards wrap from the first item', () => {
    expect(compositeDefects(tablist([{ key: 'ArrowRight', from: 0, to: 2 }]))).toEqual([
      'ArrowRight moves to the previous item; in this widget it moves to the next one, and ArrowLeft back',
    ])
  })

  it('does not judge direction where both neighbours are the same item', () => {
    expect(compositeDefects(tablist([{ key: 'ArrowRight', from: 0, to: 1 }], { disabled: [false, false] }))).toEqual([])
  })

  it('skips disabled items when it finds the next one, and accepts a jump onto one', () => {
    const disabled: readonly boolean[] = [false, true, false, false]
    expect([
      compositeDefects(tablist([{ key: 'ArrowRight', from: 0, to: 2 }], { disabled })),
      compositeDefects(tablist([{ key: 'ArrowRight', from: 0, to: 1 }], { disabled })),
      compositeDefects(tablist([{ key: 'ArrowRight', from: 2, to: 0 }], { disabled })),
    ]).toEqual([
      [],
      [],
      ['ArrowRight moves to the previous item; in this widget it moves to the next one, and ArrowLeft back'],
    ])
  })

  it('judges no direction on a grid, whose vertical keys move by a row', () => {
    expect(
      compositeDefects(
        tablist([{ key: 'ArrowDown', from: 4, to: 1 }], { widget: widget('grid'), disabled: SIX_ENABLED }),
      ),
    ).toEqual([])
  })

  it('accepts a radio group, native or by role, moved by either axis', () => {
    const radios: CompositeProbe = tablist([], { widget: widget('radiogroup'), tabbableItems: 0 })
    expect([
      compositeDefects({ ...radios, moves: [{ key: 'ArrowDown', from: 0, to: 1 }] }),
      compositeDefects({
        ...radios,
        moves: [
          { key: 'ArrowDown', from: 0, to: 0 },
          { key: 'ArrowUp', from: 0, to: 0 },
          { key: 'ArrowRight', from: 0, to: 1 },
        ],
      }),
    ]).toEqual([[], []])
  })

  it('judges no arrows on a widget with one item, or one not driven', () => {
    expect([
      compositeDefects(tablist([{ key: 'ArrowRight', from: 0, to: 0 }], { disabled: [false] })),
      compositeDefects(tablist([])),
    ]).toEqual([[], []])
  })

  it('judges a revealed menu by its arrows alone', () => {
    expect(
      compositeDefects(
        tablist([{ key: 'ArrowDown', from: NO_ITEM, to: NO_ITEM }], {
          widget: widget('menu'),
          isPopup: true,
          hasTabStop: false,
        }),
      ),
    ).toEqual(['ArrowDown does not move focus among its items'])
  })
})
