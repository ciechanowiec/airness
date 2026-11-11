// What the interaction pass holds a page to after its keyboard walk (rule `pointer-only`): an element that
// presents itself to a pointer as something to operate, and that the keyboard never reached, fails unless a
// control the keyboard did reach stands in for it. WCAG 2.1.1 and 2.1.3 ask that everything a pointer can
// operate is operable from a keyboard; a `div` with a click handler and a pointer cursor is the classic way
// a page breaks that while every automated scan of its markup passes. The browser side walks the page with
// Tab, marks every element focus reached, and reads the facts below; everything that decides a verdict is
// here.
//
// WHY "PRESENTS AS OPERABLE" IS NARROW. A click handler attached through `addEventListener` or a framework
// leaves no trace a page can read, so the rule reads what the page shows a pointer instead, and only what
// is unambiguous: a pointer cursor the element sets itself, an interactive ARIA role, or an inline `onclick`.
// A cursor is inherited, so one an operable ancestor set - a link's text, a button's icon, a label's
// caption - presents nothing of its own; only the element whose parent does not show the pointer is read.
//
// WHY A STAND-IN EXCUSES IT. A card whose surface is clickable for convenience and whose title is a real link
// is operable from the keyboard through that link; so is a tab whose tablist the keyboard entered, which the
// arrow keys reach by design, a radio whose group the keyboard entered, a label whose control is focusable,
// and a duplicate image link beside a reached link to the same address. Each is reported nowhere.

/** The ARIA roles that present an element as a control someone operates. */
export const INTERACTIVE_ROLES: readonly string[] = [
  'button',
  'checkbox',
  'combobox',
  'link',
  'menuitem',
  'menuitemcheckbox',
  'menuitemradio',
  'option',
  'radio',
  'searchbox',
  'slider',
  'spinbutton',
  'switch',
  'tab',
  'textbox',
  'treeitem',
]

/**
 * The containers whose members the keyboard reaches with the arrow keys once it has entered them, so a
 * member the Tab walk skipped is reached all the same: a roving tab index keeps one member in the order.
 */
export const COMPOSITE_OWNERS: string = [
  '[role="grid"]',
  '[role="listbox"]',
  '[role="menu"]',
  '[role="menubar"]',
  '[role="radiogroup"]',
  '[role="tablist"]',
  '[role="toolbar"]',
  '[role="tree"]',
  '[role="treegrid"]',
  'select',
  'datalist',
].join(', ')

/** The elements that operate something themselves, which is what lets one reached by the keyboard stand in. */
export const OPERABLE_SELECTOR: string = [
  'a[href]',
  'button',
  'input:not([type="hidden"])',
  'select',
  'textarea',
  'summary',
  ...INTERACTIVE_ROLES.map((role: string): string => `[role="${role}"]`),
].join(', ')

const POINTER_CURSOR: string = 'pointer'

/** How a label relates to the control it names, when it names one. */
export type LabelControl = 'none' | 'focusable' | 'disabled' | 'unfocusable'

/** What the browser reports an element that may present itself as operable shows of itself. */
export interface OwnFacts {
  /** The element's tag name, lower case. */
  readonly tag: string
  /** The first token of its `role`, or empty without one. */
  readonly role: string
  /** Whether it carries an inline `onclick` attribute. */
  readonly hasOnclick: boolean
  /** Its computed `cursor`. */
  readonly cursor: string
  /** Its parent's computed `cursor`, which tells a cursor it sets from one it inherits. */
  readonly parentCursor: string
  /** Whether the keyboard walk focused it. */
  readonly isReached: boolean
  /** Whether it is `:disabled` or sits under `aria-disabled="true"`. */
  readonly isDisabled: boolean
  /** Whether it or an ancestor carries `inert`. */
  readonly isInert: boolean
  /** Whether it renders, visible and not fully transparent. */
  readonly isRendered: boolean
  /** Whether it is the document's root or body, which every page shows. */
  readonly isRoot: boolean
}

/** What the browser reports about the controls that could stand in for such an element. */
export interface StandInFacts {
  /** Whether an ancestor that operates something itself was focused by the walk. */
  readonly hasReachedOperableAncestor: boolean
  /** Whether a descendant that operates something itself was focused by the walk. */
  readonly hasReachedOperableDescendant: boolean
  /** Whether it belongs to a composite widget or a radio group the keyboard entered. */
  readonly isInReachedGroup: boolean
  /** Whether it is a link to an address a reached link also goes to. */
  readonly isSameHrefReached: boolean
  /** For a `label`, the control it names. */
  readonly labelControl: LabelControl
}

/** Everything the browser reports about an element that may present itself as operable. */
export type PointerCandidate = OwnFacts & StandInFacts

/** What a walk that has ended reports, which decides whether its silence about an element means anything. */
export interface KeyboardWalk {
  /** How many distinct elements the walk focused. */
  readonly reached: number
  /** The most stops the walk could take. */
  readonly cap: number
  /** Whether focus left the document, or came back to the first element it reached, when the walk ended. */
  readonly hasWrapped: boolean
}

/**
 * Whether a keyboard walk crossed the whole page. A walk that stopped at its cap, or inside an embedded
 * frame, whose content keeps focus without the document seeing it move, never reached what lies beyond, so
 * nothing it did not reach can be judged.
 * @param walk what the walk reports.
 * @returns whether it crossed every stop the page has.
 */
export const isWalkComplete = (walk: KeyboardWalk): boolean => walk.hasWrapped && walk.reached < walk.cap

// What presents an element as operable to a pointer, in the words a finding uses.
const operableSignal = (candidate: PointerCandidate): string | undefined => {
  if (INTERACTIVE_ROLES.includes(candidate.role)) {
    return `role "${candidate.role}"`
  }
  if (candidate.hasOnclick) {
    return 'an inline onclick handler'
  }
  return candidate.cursor === POINTER_CURSOR && candidate.parentCursor !== POINTER_CURSOR
    ? 'a pointer cursor of its own'
    : undefined
}

const isOutOfReach = (candidate: PointerCandidate): boolean =>
  candidate.isRoot ||
  candidate.isDisabled ||
  candidate.isInert ||
  !candidate.isRendered ||
  candidate.labelControl === 'focusable' ||
  candidate.labelControl === 'disabled'

const isStoodIn = (candidate: PointerCandidate): boolean =>
  candidate.isReached ||
  candidate.hasReachedOperableAncestor ||
  candidate.hasReachedOperableDescendant ||
  candidate.isInReachedGroup ||
  candidate.isSameHrefReached

/**
 * Whether an element is one the keyboard cannot operate though a pointer is invited to: it presents as
 * operable, is available, and neither it nor a control standing in for it was reached by a complete walk.
 * The browser then asks whether a pointer can reach it at all; only one it can is reported.
 * @param candidate the element's facts.
 * @returns the reason it presents as operable, or undefined when it is not a pointer-only element.
 */
export const pointerOnlySignal = (candidate: PointerCandidate): string | undefined =>
  isOutOfReach(candidate) || isStoodIn(candidate) ? undefined : operableSignal(candidate)

/**
 * What is wrong with a pointer-only element (rule `pointer-only`).
 * @param label the element, as a finding names it.
 * @param signal what presents it as operable.
 * @returns the message.
 */
export const pointerOnlyMessage = (label: string, signal: string): string =>
  `${label} presents as operable (${signal}), but the keyboard never reaches it and no control the keyboard ` +
  'reaches stands in for it'
