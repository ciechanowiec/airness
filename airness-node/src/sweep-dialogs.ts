// The dialog half of the disclosure pass: a dialog a control opened, driven by the keyboard as the dialog
// pattern of the WAI-ARIA Authoring Practices describes it (rule `dialog-keyboard`).
//
// A dialog is found by what opened, not by how: before a control is activated, every dialog already
// rendered is marked, and the dialog rendered afterwards without the mark is the one it opened - a native
// `<dialog>`, `role="dialog"` or `role="alertdialog"`, modal by `showModal()` or `aria-modal="true"`. The
// sweep never calls a dialog's own methods to open it: a control must open it, as a reader's would.
import type { Locator, Page } from '@playwright/test'
import { type DialogProbe, dialogDefects, type FocusAfterClose } from './aria-keyboard-rules.js'
import { type FindingPlace, findingAt } from './browser-findings.js'
import type { TargetFacts } from './layout.js'
import { isPerceivableWithin } from './perceivable.js'
import { carried } from './sweep-interaction.js'
import { isMetInPage } from './sweep-poll.js'
import type { DomDocument, DomElement, Visit } from './sweep-shared.js'

// The browser surface the page functions below touch, declared for the reason `settle.ts` records.
declare const document: DomDocument

const DIALOGS: string = 'dialog[open], [role="dialog"], [role="alertdialog"]'
const PRESENT_ATTRIBUTE: string = 'data-airness-dialog-present'
const OPENED_ATTRIBUTE: string = 'data-airness-dialog'
const OPENED_SELECTOR: string = `[${OPENED_ATTRIBUTE}]`

/** A rendered dialog that was not rendered before the control in hand was activated. */
export const NEW_DIALOG_SELECTOR: string = `:is(${DIALOGS}):not([${PRESENT_ATTRIBUTE}])`

const markPresentInPage = ([dialogs, present, opened]: readonly [string, string, string]): void => {
  const marked: string = `[${present}], [${opened}]`
  for (const element of document.querySelectorAll(marked)) {
    element.removeAttribute(present)
    element.removeAttribute(opened)
  }
  for (const element of document.querySelectorAll(dialogs)) {
    if (element.checkVisibility()) {
      element.setAttribute(present, '')
    }
  }
}

/**
 * Mark every dialog rendered now, so the one a control opens next can be told from them.
 * @param page the page, settled before the control is activated.
 */
export const markPresentDialogs = async (page: Page): Promise<void> => {
  await page.evaluate(markPresentInPage, [DIALOGS, PRESENT_ATTRIBUTE, OPENED_ATTRIBUTE] as const)
}

/** The dialog a control opened. */
export interface OpenedDialog {
  readonly isModal: boolean
  /** How many elements inside it are in the Tab sequence, which bounds the walk that proves a trap. */
  readonly tabbables: number
}

// The outermost new dialog, the modal one first, marked for the page functions that follow.
const openedDialogInPage = ([candidates, opened]: readonly [string, string]): OpenedDialog | undefined => {
  const rendered: readonly DomElement[] = Array.from(document.querySelectorAll(candidates)).filter(
    (element: DomElement): boolean => element.checkVisibility(),
  )
  const outermost: readonly DomElement[] = rendered.filter(
    (element: DomElement): boolean =>
      !rendered.some((other: DomElement): boolean => other !== element && other.contains(element)),
  )
  const isModal = (element: DomElement): boolean =>
    element.matches('dialog:modal') || element.getAttribute('aria-modal') === 'true'
  const chosen: DomElement | undefined =
    outermost.find((element: DomElement): boolean => isModal(element)) ?? outermost[0]
  if (chosen === undefined) {
    return undefined
  }
  chosen.setAttribute(opened, '')
  const tabbables: number = Array.from(chosen.querySelectorAll<HTMLElement>('*')).filter(
    (element: HTMLElement): boolean =>
      element.tabIndex >= 0 && !element.matches(':disabled') && element.checkVisibility(),
  ).length
  return { isModal: isModal(chosen), tabbables }
}

/**
 * The dialog the control just activated opened, marked for judging, or undefined when it opened none.
 * @param page the page, settled after the activation.
 * @returns whether it is modal and how many of its elements take Tab.
 */
export const openedDialog = async (page: Page): Promise<OpenedDialog | undefined> =>
  await page.evaluate(openedDialogInPage, [NEW_DIALOG_SELECTOR, OPENED_ATTRIBUTE] as const)

/** Where focus is relative to the opened dialog: in it, on page content outside it, or not in the page. */
type FocusPlace = 'inside' | 'outside' | 'away'

/** The opened dialog and its opener, as the page has them now. */
interface DialogFacts {
  readonly focus: FocusPlace
  readonly isClosed: boolean
  readonly isOpenerPresent: boolean
  readonly isFocusOnOpener: boolean
}

// Focus that left the document is in the browser's own interface, where a native modal dialog sends Tab
// past its last element before it comes back: not page content, so not a leak.
const dialogFactsInPage = ([dialogSelector, openerSelector]: readonly [string, string]): DialogFacts => {
  const dialog: DomElement | null = document.querySelector(dialogSelector)
  const opener: DomElement | null = document.querySelector(openerSelector)
  const active: DomElement | null = document.activeElement
  const isHeldBy = (element: DomElement | null): boolean => active !== null && element?.contains(active) === true
  const isAway: boolean = active === null || active === document.body || !document.hasFocus()
  const inOrOut: FocusPlace = isHeldBy(dialog) ? 'inside' : 'outside'
  return {
    focus: isAway ? 'away' : inOrOut,
    isClosed: dialog?.checkVisibility() !== true,
    isOpenerPresent: opener?.checkVisibility() === true,
    isFocusOnOpener: !isAway && isHeldBy(opener),
  }
}

const factsSource = (openerSelector: string): string =>
  `(${String(dialogFactsInPage)})(${JSON.stringify([OPENED_SELECTOR, openerSelector])})`

const factsOf = async (page: Page, openerSelector: string): Promise<DialogFacts> =>
  await page.evaluate<DialogFacts>(factsSource(openerSelector))

const focusOf = async (page: Page, openerSelector: string): Promise<FocusPlace> =>
  await page.evaluate<FocusPlace>(`${factsSource(openerSelector)}.focus`)

const focusFirstInPage = (selector: string): void => {
  const dialog: HTMLElement | null = document.querySelector<HTMLElement>(selector)
  const first: HTMLElement | undefined = Array.from(dialog?.querySelectorAll<HTMLElement>('*') ?? []).find(
    (element: HTMLElement): boolean =>
      element.tabIndex >= 0 && !element.matches(':disabled') && element.checkVisibility(),
  )
  ;(first ?? dialog)?.focus()
}

// How long a dialog may take to move focus in, or to close and hand it back. Libraries move focus once
// their entry or exit has rendered, a tick or a frame after the key.
const FOCUS_MOVE_MS: number = 1000
// The Tab walk goes once around the dialog and two stops beyond, enough to pass through the browser's
// interface and land on whatever comes next, and never more than this.
const EXTRA_STOPS: number = 2
const MAX_STOPS: number = 30

// Wait until the facts meet a condition, written over `facts`; a condition not met in time is what the facts
// read next report.
const waitForFacts = async (page: Page, openerSelector: string, condition: string): Promise<void> => {
  await isMetInPage(page, `((facts) => ${condition})(${factsSource(openerSelector)})`, FOCUS_MOVE_MS)
}

const doesKeyLeave = async (page: Page, openerSelector: string, key: string, remaining: number): Promise<boolean> => {
  if (remaining <= 0) {
    return false
  }
  await page.keyboard.press(key)
  return (
    (await focusOf(page, openerSelector)) === 'outside' ||
    (await doesKeyLeave(page, openerSelector, key, remaining - 1))
  )
}

// Each walk starts from the dialog's first element, so a walk the other one left outside proves nothing.
const doesWalkLeave = async (page: Page, openerSelector: string, key: string, stops: number): Promise<boolean> => {
  await page.evaluate(focusFirstInPage, OPENED_SELECTOR)
  return await doesKeyLeave(page, openerSelector, key, stops)
}

const focusAfterClose = async (page: Page, opener: Locator, facts: DialogFacts): Promise<FocusAfterClose> => {
  const focused: Locator = facts.isFocusOnOpener ? opener : page.locator(':focus')
  if (facts.focus === 'away' || !(await isPerceivableWithin(focused))) {
    return 'lost'
  }
  return facts.isFocusOnOpener ? 'opener' : 'elsewhere'
}

// A native dialog Escape did not close is closed here, so the next control starts from a page whose
// background is not inert; a dialog by role is left to the disclosure pass, which activates its opener
// again.
const wasNativeClosedInPage = (selector: string): boolean => {
  const dialog: HTMLDialogElement | null = document.querySelector<HTMLDialogElement>(`dialog${selector}`)
  if (dialog?.checkVisibility() !== true) {
    return false
  }
  dialog.close()
  return true
}

/** How a dialog is driven: the page, the control that opened it, and where findings go. */
export interface DialogDrive {
  readonly page: Page
  readonly opener: Locator
  readonly openerSelector: string
  readonly place: FindingPlace
  /** The opener's label and the exemptions above it, which excuse a finding on the dialog it opens. */
  readonly facts: TargetFacts | undefined
}

/** What judging an opened dialog left: its findings, and whether it is still open. */
export interface JudgedDialog {
  readonly visits: readonly Visit[]
  readonly isOpen: boolean
}

/**
 * Drive an opened dialog by the keyboard and judge it: focus moves in, a modal one keeps Tab and Shift+Tab
 * inside, Escape closes it, and focus returns to its opener or, when the opener went with it, to an element
 * a reader can see.
 * @param drive the page, the opener and where findings go.
 * @param dialog the dialog `openedDialog` found.
 * @returns the findings, and whether the dialog is still open for the caller to close.
 */
export const judgeDialog = async (drive: DialogDrive, dialog: OpenedDialog): Promise<JudgedDialog> => {
  const { page, opener, openerSelector, place, facts }: DialogDrive = drive
  await waitForFacts(page, openerSelector, "facts.focus === 'inside'")
  const isFocusMovedIn: boolean = (await focusOf(page, openerSelector)) === 'inside'
  const stops: number = Math.min(dialog.tabbables + EXTRA_STOPS, MAX_STOPS)
  const doesTabLeave: boolean = dialog.isModal && (await doesWalkLeave(page, openerSelector, 'Tab', stops))
  const doesShiftTabLeave: boolean = dialog.isModal && (await doesWalkLeave(page, openerSelector, 'Shift+Tab', stops))
  await page.evaluate(focusFirstInPage, OPENED_SELECTOR)
  await page.keyboard.press('Escape')
  await waitForFacts(
    page,
    openerSelector,
    "facts.isClosed && facts.focus !== 'away' && (facts.isFocusOnOpener || !facts.isOpenerPresent)",
  )
  const closed: DialogFacts = await factsOf(page, openerSelector)
  const probe: DialogProbe = {
    isModal: dialog.isModal,
    isFocusMovedIn,
    doesTabLeave,
    doesShiftTabLeave,
    isClosedOnEscape: closed.isClosed,
    isOpenerPresent: closed.isOpenerPresent,
    focusAfterClose: closed.isClosed ? await focusAfterClose(page, opener, closed) : 'lost',
  }
  const isStillOpen: boolean = !(closed.isClosed || (await page.evaluate(wasNativeClosedInPage, OPENED_SELECTOR)))
  return {
    visits: dialogDefects(probe).map(
      (defect: string): Visit =>
        carried(findingAt(place, 'dialog-keyboard', `${facts?.label ?? 'a dialog opener'}: ${defect}`), facts),
    ),
    isOpen: isStillOpen,
  }
}
