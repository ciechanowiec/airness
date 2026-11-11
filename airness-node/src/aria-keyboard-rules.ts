// The rules that judge how a dialog and a composite widget answer the keyboard, over what the sweep observed
// while driving them. axe checks that roles, states and properties are spelled and nested correctly; what a
// role promises a keyboard user - the keys of the WAI-ARIA Authoring Practices pattern it names - only
// driving it shows. Each rule reports only a definite contradiction of its pattern: a key a pattern leaves
// to the author, or a move this module cannot tell from a correct one, is not judged.

/** Where focus was after Escape closed a dialog. */
export type FocusAfterClose = 'opener' | 'elsewhere' | 'lost'

/** What driving one opened dialog with the keyboard showed (rule `dialog-keyboard`). */
export interface DialogProbe {
  /** Whether it is modal: a `<dialog>` opened with `showModal()`, or a dialog role with `aria-modal="true"`. */
  readonly isModal: boolean
  /** Whether focus was inside it once it had opened. */
  readonly isFocusMovedIn: boolean
  /**
   * Whether Tab, pressed past its last element, reached page content outside it. Focus leaving the document
   * for the browser's own interface is not page content: that is where a native modal `<dialog>` sends it.
   */
  readonly doesTabLeave: boolean
  /** Whether Shift+Tab, pressed past its first element, reached page content outside it. */
  readonly doesShiftTabLeave: boolean
  readonly isClosedOnEscape: boolean
  /** Whether the control that opened it was still connected and rendered once it closed. */
  readonly isOpenerPresent: boolean
  /**
   * Where perceivable focus was once it closed: on its opener, on another element, or nowhere a reader can
   * see, such as the body.
   */
  readonly focusAfterClose: FocusAfterClose
}

const closingDefects = (probe: DialogProbe): readonly string[] => {
  if (!probe.isClosedOnEscape) {
    return ['Escape does not close it']
  }
  if (probe.isOpenerPresent) {
    return probe.focusAfterClose === 'opener' ? [] : ['focus does not return to its opener after Escape closes it']
  }
  return probe.focusAfterClose === 'lost' ? ['focus is lost after Escape closes it, and its opener is gone'] : []
}

/**
 * What is wrong with how an opened dialog answers the keyboard (rule `dialog-keyboard`).
 *
 * Every dialog takes focus when it opens, closes on Escape and hands focus back to the control that opened
 * it, or, when that control went away with it, to some element a reader can see. Only a modal dialog keeps
 * Tab and Shift+Tab inside: a non-modal one is part of the page, and tabbing out of it is how a reader
 * leaves it.
 * @param probe what the keyboard showed.
 * @returns one message per defect.
 */
export const dialogDefects = (probe: DialogProbe): readonly string[] => [
  ...(probe.isFocusMovedIn ? [] : ['focus does not move into the dialog when it opens']),
  ...(probe.isModal && probe.doesTabLeave ? ['Tab moves focus out of the open modal dialog'] : []),
  ...(probe.isModal && probe.doesShiftTabLeave ? ['Shift+Tab moves focus out of the open modal dialog'] : []),
  ...closingDefects(probe),
]

/** The roles of the composite widgets whose items the arrow keys move among. */
export const COMPOSITE_ROLES: readonly string[] = [
  'tablist',
  'menu',
  'menubar',
  'listbox',
  'radiogroup',
  'grid',
  'tree',
  'treegrid',
]

/** The axis a widget's items run along. */
type Orientation = 'horizontal' | 'vertical'

/** A composite widget as the page declares it. */
export interface CompositeWidget {
  /** Its role; native radio buttons sharing a name form a `radiogroup`. */
  readonly role: string
  /** Its `aria-orientation`, when it declares a valid one. */
  readonly orientation: Orientation | undefined
  /** Whether its computed direction is right to left, which turns the horizontal keys around. */
  readonly isRightToLeft: boolean
}

/** One axis's keys: the one that moves to the next item and the one that moves back. */
export interface ArrowPair {
  readonly next: string
  readonly previous: string
  /**
   * Whether the items run in document order along this axis, so a move can be judged forwards or backwards.
   * A grid's cells do not: its vertical keys move by a row.
   */
  readonly isOrdered: boolean
}

// The orientation each linear role implies when it declares none (WAI-ARIA 1.2).
const IMPLIED_ORIENTATION: Readonly<Record<string, Orientation>> = {
  tablist: 'horizontal',
  menubar: 'horizontal',
  menu: 'vertical',
  listbox: 'vertical',
  tree: 'vertical',
}

const TWO_DIMENSIONAL: ReadonlySet<string> = new Set<string>(['grid', 'treegrid'])

const pairOf = (orientation: Orientation, isRightToLeft: boolean, isOrdered: boolean): ArrowPair => {
  if (orientation === 'vertical') {
    return { next: 'ArrowDown', previous: 'ArrowUp', isOrdered }
  }
  return isRightToLeft
    ? { next: 'ArrowLeft', previous: 'ArrowRight', isOrdered }
    : { next: 'ArrowRight', previous: 'ArrowLeft', isOrdered }
}

/**
 * The arrow keys a widget's pattern moves among its items with, in the order the sweep tries them.
 *
 * A linear widget has one axis, the one it declares or its role implies. A radio group without an
 * orientation answers both axes, as the radio group pattern and the platform do, so either one moving
 * satisfies it. A grid or a tree grid moves along both axes, by a cell and by a row.
 * @param widget the widget.
 * @returns one pair per axis.
 */
export const compositeArrows = (widget: CompositeWidget): readonly ArrowPair[] => {
  if (TWO_DIMENSIONAL.has(widget.role)) {
    return [pairOf('vertical', widget.isRightToLeft, false), pairOf('horizontal', widget.isRightToLeft, false)]
  }
  const orientation: Orientation | undefined = widget.orientation ?? IMPLIED_ORIENTATION[widget.role]
  return orientation === undefined
    ? [pairOf('vertical', widget.isRightToLeft, true), pairOf('horizontal', widget.isRightToLeft, true)]
    : [pairOf(orientation, widget.isRightToLeft, true)]
}

/** The item index of a move that ended with no item active, as an unset `aria-activedescendant` leaves it. */
export const NO_ITEM: number = -1

/** The item index of a move that took focus out of the widget. */
export const OUTSIDE: number = -2

/** One arrow key pressed, and the item active before and after it. */
export interface ArrowMove {
  readonly key: string
  /** The index of the active item before the key, or `NO_ITEM`. */
  readonly from: number
  /** The index of the active item after it, `NO_ITEM`, or `OUTSIDE`. */
  readonly to: number
}

/**
 * Whether a key moved focus, or the active descendant, to another item of the widget.
 * @param move the key and the items before and after it.
 * @returns true when another item became active.
 */
export const hasMoved = (move: ArrowMove): boolean => move.to >= 0 && move.to !== move.from

/** What driving one composite widget with the keyboard showed (rule `composite-keyboard`). */
export interface CompositeProbe {
  readonly widget: CompositeWidget
  /**
   * Whether a disclosure revealed it, as a menu button reveals its menu. Its own pattern decides what Tab
   * does there - a menu closes on it - so only its arrow keys are judged.
   */
  readonly isPopup: boolean
  /** Whether each rendered item, in document order, is disabled. */
  readonly disabled: readonly boolean[]
  /**
   * How many of its items are in the Tab sequence on their own. Native radio buttons are not counted: the
   * browser gives their group one Tab stop whatever each one's `tabIndex` reads.
   */
  readonly tabbableItems: number
  /** Whether anything in it, the widget itself included, is in the Tab sequence. */
  readonly hasTabStop: boolean
  /** Whether Tab, pressed from where focus enters it, landed on another element inside it. */
  readonly doesTabStayInside: boolean
  /** The arrow keys pressed from where focus enters it, in order; empty when it was not driven. */
  readonly moves: readonly ArrowMove[]
}

const tabDefects = (probe: CompositeProbe): readonly string[] => {
  if (probe.isPopup) {
    return []
  }
  if (!probe.hasTabStop) {
    return ['nothing in it is in the Tab sequence, so the keyboard cannot reach it']
  }
  if (probe.tabbableItems > 1) {
    return [
      `${String(probe.tabbableItems)} of its items are in the Tab sequence; a composite widget is one Tab stop: ` +
        'keep one item at tabindex 0 and the others at -1, or focus the widget and use aria-activedescendant',
    ]
  }
  return probe.doesTabStayInside ? ['Tab moves to another element inside it instead of leaving it'] : []
}

const nextEnabled = (disabled: readonly boolean[], from: number): number => {
  const after: number = disabled.findIndex((isDisabled: boolean, index: number): boolean => index > from && !isDisabled)
  return after === NO_ITEM ? disabled.indexOf(false) : after
}

const previousEnabled = (disabled: readonly boolean[], from: number): number => {
  const before: number = disabled.findLastIndex(
    (isDisabled: boolean, index: number): boolean => index < from && !isDisabled,
  )
  return before === NO_ITEM ? disabled.lastIndexOf(false) : before
}

// A next key that lands exactly where the previous key should, and not where the next key should, moved the
// wrong way. Any other landing - a skipped item, a jump a type-ahead or a row makes - is not definite.
const directionDefects = (probe: CompositeProbe, move: ArrowMove): readonly string[] => {
  const pair: ArrowPair | undefined = compositeArrows(probe.widget).find(
    (candidate: ArrowPair): boolean => candidate.next === move.key,
  )
  if (pair === undefined || !pair.isOrdered || move.from < 0) {
    return []
  }
  const forwards: number = nextEnabled(probe.disabled, move.from)
  const backwards: number = previousEnabled(probe.disabled, move.from)
  return move.to === backwards && move.to !== forwards
    ? [`${move.key} moves to the previous item; in this widget it moves to the next one, and ${pair.previous} back`]
    : []
}

// Fewer items than this leave nothing for an arrow key to move among.
const MOVABLE_ITEMS: number = 2

const arrowDefects = (probe: CompositeProbe): readonly string[] => {
  if (probe.disabled.length < MOVABLE_ITEMS || probe.moves.length === 0) {
    return []
  }
  const escaped: ArrowMove | undefined = probe.moves.find((move: ArrowMove): boolean => move.to === OUTSIDE)
  if (escaped !== undefined) {
    return [`${escaped.key} moves focus out of it`]
  }
  const moved: ArrowMove | undefined = probe.moves.find((move: ArrowMove): boolean => hasMoved(move))
  if (moved === undefined) {
    const keys: readonly string[] = [...new Set<string>(probe.moves.map((move: ArrowMove): string => move.key))]
    return [`${keys.join(', ')} ${keys.length === 1 ? 'does' : 'do'} not move focus among its items`]
  }
  return directionDefects(probe, moved)
}

/**
 * What is wrong with how a composite widget answers the keyboard (rule `composite-keyboard`).
 *
 * A tab list, menu, menu bar, list box, radio group, grid, tree or tree grid is one Tab stop - a roving
 * tabindex or an `aria-activedescendant` - and its arrow keys move among its items along the axis its
 * orientation implies, the horizontal keys turned around in a right-to-left widget. A widget with fewer
 * than two rendered items has nothing to move among, so only its Tab stop is judged.
 * @param probe what the keyboard showed.
 * @returns one message per defect.
 */
export const compositeDefects = (probe: CompositeProbe): readonly string[] => [
  ...tabDefects(probe),
  ...arrowDefects(probe),
]
