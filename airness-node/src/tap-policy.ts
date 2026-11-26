// What the touch pass holds a page to (rule `tap-ignored`): an element a mouse click answers answers a tap on
// a touch screen as well. A control that listens only for a mouse - a handler that returns early unless the
// pointer is a mouse, or one that runs only where the device can hover - works for every visitor at a desk and
// does nothing for one holding a phone or a tablet, while every check that drives the page with a mouse or a
// keyboard passes. The browser taps each such element on a touch phone and records what changed; an element
// the tap left unanswered is clicked with a mouse at the same width, in a browser that can hover, and only
// one the click answers is reported. A control that answers neither - a button no script listens to yet, one
// that only plays a sound - is no difference between a mouse and a finger. Everything that decides a verdict
// is here.
//
// WHAT IS TAPPED. A button, which does nothing of its own until a script answers it, and every element
// presenting as operable the way the pointer-only rule reads it: an interactive role, an inline `onclick`, a
// pointer cursor of its own. What the browser itself answers is not tapped: a link with an address, a
// disclosure's summary, a form's submit or reset button, a label whose control the tap reaches, and a field
// a visitor types into or picks from. Neither is an inactive element, nor one a pointer cannot reach.
//
// WHAT COUNTS AS AN ANSWER. Anything the page does: the document changes, the address or the history moves,
// a popover, dialog or popup opens, a form control changes its value, the window scrolls, or the page asks
// the network for something. Focus moving to the element is not one: the browser does that for a tap on
// any focusable element, whether or not anything listens.
import { operableSignal } from './pointer-only.js'

/** The rules the touch pass judges, on one route per template rather than on every route. */
export const TOUCH_RULES: readonly string[] = ['tap-ignored']

// The input types that are buttons, which do nothing until a script answers them.
const BUTTON_INPUT_TYPES: ReadonlySet<string> = new Set<string>(['button'])

// The elements whose own behaviour answers a tap, whatever a script does.
const NATIVE_TAGS: ReadonlySet<string> = new Set<string>(['summary', 'select', 'textarea', 'option', 'optgroup'])

/** What the browser reports about an element a tap may be aimed at. */
export interface TapFacts {
  /** The element's tag name, lower case. */
  readonly tag: string
  /** An input's or a button's `type`, lower case, or empty for any other element. */
  readonly type: string
  /** The first token of its `role`, or empty without one. */
  readonly role: string
  /** Whether it carries an inline `onclick` attribute. */
  readonly hasOnclick: boolean
  /** Its computed `cursor`. */
  readonly cursor: string
  /** Its parent's computed `cursor`, which tells a cursor it sets from one it inherits. */
  readonly parentCursor: string
  /** Whether it is a link or an image map area with an address, which the browser follows. */
  readonly hasHref: boolean
  /** Whether it is a submit or reset button that belongs to a form, which the browser submits or resets. */
  readonly isFormButton: boolean
  /** Whether it is a label whose control a tap reaches. */
  readonly isLabelOfControl: boolean
  /** Whether a visitor can type into it: an editable host or one inside it. */
  readonly isEditable: boolean
  /** Whether it is `:disabled` or sits under `aria-disabled="true"`. */
  readonly isDisabled: boolean
  /** Whether it or an ancestor carries `inert`. */
  readonly isInert: boolean
  /** Whether it renders, visible and not fully transparent. */
  readonly isRendered: boolean
  /** Whether it is the document's root or body, which every page shows. */
  readonly isRoot: boolean
}

const isAnsweredByBrowser = (facts: TapFacts): boolean =>
  facts.hasHref ||
  facts.isFormButton ||
  facts.isLabelOfControl ||
  facts.isEditable ||
  NATIVE_TAGS.has(facts.tag) ||
  (facts.tag === 'input' && !BUTTON_INPUT_TYPES.has(facts.type))

const isUnavailable = (facts: TapFacts): boolean =>
  facts.isRoot || facts.isDisabled || facts.isInert || !facts.isRendered

const isButton = (facts: TapFacts): boolean =>
  facts.tag === 'button' || (facts.tag === 'input' && BUTTON_INPUT_TYPES.has(facts.type))

/**
 * Whether a tap is aimed at an element, and what presents it as something to operate.
 * @param facts what the browser reports about the element.
 * @returns the reason it is tapped, or undefined when it is not.
 */
export const tapSignal = (facts: TapFacts): string | undefined => {
  if (isUnavailable(facts) || isAnsweredByBrowser(facts)) {
    return undefined
  }
  return isButton(facts) ? `a ${facts.tag} element` : operableSignal(facts)
}

/** What changed on the page after one tap. */
export interface TapResponse {
  /** How many changes the document's tree saw, the harness's own marks left out. */
  readonly mutations: number
  /** Whether the address changed, its fragment included. */
  readonly hasMovedAddress: boolean
  /** Whether the session history gained an entry. */
  readonly hasMovedHistory: boolean
  /** Whether a form control's value or checked state changed. */
  readonly hasChangedForm: boolean
  /** Whether a popover or a dialog opened or closed. */
  readonly hasToggledLayer: boolean
  /** Whether the window scrolled. */
  readonly hasScrolled: boolean
  /** Whether the page asked the network for a document or data, or opened a popup or a download. */
  readonly hasReachedOut: boolean
}

/**
 * Whether a page answered a tap at all.
 * @param response what changed after the tap.
 * @returns true when anything did.
 */
export const isTapAnswered = (response: TapResponse): boolean =>
  response.mutations > 0 ||
  response.hasMovedAddress ||
  response.hasMovedHistory ||
  response.hasChangedForm ||
  response.hasToggledLayer ||
  response.hasScrolled ||
  response.hasReachedOut

/**
 * What is wrong with an element that answered a mouse click and ignored a tap (rule `tap-ignored`).
 * @param label the element, as a finding names it.
 * @param signal what presents it as operable.
 * @returns the message.
 */
export const tapIgnoredMessage = (label: string, signal: string): string =>
  `${label} presents as operable (${signal}) and answers a mouse click, but a tap on a touch screen changed ` +
  'nothing: no change to the document, its address, a form, a popover or dialog, its scroll or its requests; ' +
  'answer a tap as a click is answered, whatever the pointer and whether the device can hover'
