// The composite-widget half of the disclosure pass: every tab list, menu, menu bar, list box, radio group,
// grid, tree and tree grid driven by the keyboard as its pattern in the WAI-ARIA Authoring Practices
// describes (rule `composite-keyboard`). A group of native radio buttons sharing a name is a radio group
// too; the browser gives it its keys, so it fails only where the page takes them away.
//
// The widgets rendered at rest are judged on their Tab stop and arrow keys once a place's disclosures have
// been driven, because the keys change the page - a tab list selects as it moves - and the disclosures are
// judged on the page as it loaded. A widget a disclosure reveals, a menu button's menu or a combobox's list
// box, is judged on its arrow keys while it is open: its own pattern decides what Tab does there.
import type { Page } from '@playwright/test'
import {
  type ArrowMove,
  type ArrowPair,
  COMPOSITE_ROLES,
  type CompositeProbe,
  type CompositeWidget,
  compositeArrows,
  compositeDefects,
  hasMoved,
  NO_ITEM,
  OUTSIDE,
} from './aria-keyboard-rules.js'
import { type FindingPlace, findingAt } from './browser-findings.js'
import { type TargetFacts, targetFacts } from './layout.js'
import { carried } from './sweep-interaction.js'
import { isMetInPage } from './sweep-poll.js'
import { EMPTY_VISIT, merged, type Visit } from './sweep-shared.js'

const COMPOSITE_ATTRIBUTE: string = 'data-airness-composite'
const PRESENT_ATTRIBUTE: string = 'data-airness-composite-present'

// The items each role's arrow keys move among. A grid built on a table names its cells by their tags.
const MENU_ITEMS: string = '[role="menuitem"], [role="menuitemcheckbox"], [role="menuitemradio"]'
const GRID_ITEMS: string = '[role="row"], [role="gridcell"], [role="columnheader"], [role="rowheader"], tr, td, th'
const ITEM_SELECTORS: Readonly<Record<string, string>> = {
  tablist: '[role="tab"]',
  menu: MENU_ITEMS,
  menubar: MENU_ITEMS,
  listbox: '[role="option"]',
  radiogroup: '[role="radio"], input[type="radio"]',
  tree: '[role="treeitem"]',
  grid: GRID_ITEMS,
  treegrid: GRID_ITEMS,
}

/** What a page function is told: the attributes it marks with, and the widget it works on. */
interface CompositeRequest {
  readonly attribute: string
  readonly present: string
  readonly widgets: string
  readonly items: Readonly<Record<string, string>>
  readonly index: number
  readonly noItem: number
  readonly outside: number
}

/** A widget as a page function finds it again from its mark. */
interface WidgetElements {
  /** The element carrying the role, or null for a group of native radio buttons. */
  readonly container: HTMLElement | null
  /** The element the mark is on: the container, or the group's first radio button. */
  readonly anchor: HTMLElement
  readonly role: string
  readonly items: readonly HTMLElement[]
}

/** The steps every composite page function shares, each handed the others. */
interface CompositeTools {
  readonly request: CompositeRequest
  readonly isTabbable: (element: HTMLElement) => boolean
  readonly isHolding: (outer: Element, inner: Element | null) => boolean
  readonly widgetOf: (tools: CompositeTools) => WidgetElements | undefined
  readonly entryOf: (widget: WidgetElements, tools: CompositeTools) => HTMLElement | undefined
  readonly isFocusInside: (widget: WidgetElements, tools: CompositeTools) => boolean
  readonly isControlling: (active: Element, container: HTMLElement | null) => boolean
  readonly activeIndex: (widget: WidgetElements, tools: CompositeTools) => number
}

// The page functions run in the browser, which knows nothing of this module, so each receives these steps
// from their source, as `layout.ts` hands its measuring steps over. Each refers to the others through the
// tools it is handed, never through this module's scope.

const isTabbable = (element: HTMLElement): boolean =>
  element.tabIndex >= 0 && !element.matches(':disabled') && element.checkVisibility()

const isHolding = (outer: Element, inner: Element | null): boolean =>
  inner !== null && (outer === inner || outer.contains(inner))

const widgetOf = (tools: CompositeTools): WidgetElements | undefined => {
  const marked: string = `[${tools.request.attribute}="${String(tools.request.index)}"]`
  const anchor: HTMLElement | null = document.querySelector<HTMLElement>(marked)
  if (anchor instanceof HTMLInputElement) {
    const items: readonly HTMLElement[] = Array.from(
      document.querySelectorAll<HTMLInputElement>('input[type="radio"]'),
    ).filter(
      (radio: HTMLInputElement): boolean =>
        radio.name === anchor.name && radio.form === anchor.form && radio.checkVisibility(),
    )
    return { container: null, anchor, role: 'radiogroup', items }
  }
  if (anchor === null) {
    return undefined
  }
  const role: string = anchor.getAttribute('role') ?? ''
  const items: readonly HTMLElement[] = Array.from(
    anchor.querySelectorAll<HTMLElement>(tools.request.items[role] ?? '*'),
  ).filter((item: HTMLElement): boolean => item.closest(tools.request.widgets) === anchor && item.checkVisibility())
  return { container: anchor, anchor, role, items }
}

const entryOf = (widget: WidgetElements, tools: CompositeTools): HTMLElement | undefined => {
  if (widget.container === null) {
    return (
      widget.items.find((item: HTMLElement): boolean => item instanceof HTMLInputElement && item.checked) ??
      widget.items.find((item: HTMLElement): boolean => tools.isTabbable(item))
    )
  }
  return [widget.container, ...Array.from(widget.container.querySelectorAll<HTMLElement>('*'))].find(
    (element: HTMLElement): boolean => tools.isTabbable(element),
  )
}

const isFocusInside = (widget: WidgetElements, tools: CompositeTools): boolean => {
  const active: Element | null = document.activeElement
  const isInItem: boolean = widget.items.some((item: HTMLElement): boolean => tools.isHolding(item, active))
  return document.hasFocus() && (isInItem || (widget.container !== null && tools.isHolding(widget.container, active)))
}

const isControlling = (active: Element, container: HTMLElement | null): boolean =>
  container !== null &&
  container.id !== '' &&
  [active.getAttribute('aria-controls'), active.getAttribute('aria-owns')]
    .join(' ')
    .split(/\s+/u)
    .includes(container.id)

// The item the focused element's `aria-activedescendant` names, the innermost item holding focus, no item
// while focus is in the widget or on the element that controls it, or outside.
const activeIndex = (widget: WidgetElements, tools: CompositeTools): number => {
  const active: Element | null = document.activeElement
  if (active === null || !document.hasFocus()) {
    return tools.request.outside
  }
  const descendantId: string = active.getAttribute('aria-activedescendant') ?? ''
  const descendant: Element | null = descendantId === '' ? null : document.querySelector(`#${CSS.escape(descendantId)}`)
  const holding: readonly HTMLElement[] = widget.items.filter(
    (item: HTMLElement): boolean => item === descendant || tools.isHolding(item, active),
  )
  const innermost: HTMLElement | undefined = holding.find(
    (item: HTMLElement): boolean =>
      !holding.some((other: HTMLElement): boolean => other !== item && item.contains(other)),
  )
  if (innermost !== undefined) {
    return widget.items.indexOf(innermost)
  }
  return tools.isFocusInside(widget, tools) || tools.isControlling(active, widget.container)
    ? tools.request.noItem
    : tools.request.outside
}

const STEPS: Readonly<Record<Exclude<keyof CompositeTools, 'request'>, unknown>> = {
  isTabbable,
  isHolding,
  widgetOf,
  entryOf,
  isFocusInside,
  isControlling,
  activeIndex,
}

const requestOf = (index: number): CompositeRequest => ({
  attribute: COMPOSITE_ATTRIBUTE,
  present: PRESENT_ATTRIBUTE,
  widgets: COMPOSITE_ROLES.map((role: string): string => `[role="${role}"]`).join(', '),
  items: ITEM_SELECTORS,
  index,
  noItem: NO_ITEM,
  outside: OUTSIDE,
})

// The source of a page function handed the tools for one widget, for `evaluate` and `isMetInPage`.
const inPage = (operation: (tools: CompositeTools) => unknown, index: number): string => {
  const steps: string = Object.entries(STEPS)
    .map(([name, step]: readonly [string, unknown]): string => `${name}: ${String(step)}`)
    .join(', ')
  return `(${String(operation)})({request: ${JSON.stringify(requestOf(index))}, ${steps}})`
}

// Every widget rendered at rest that a reader reaches by Tab: not inside another widget, not disabled, and
// not the popup an element names with `aria-controls` or `aria-owns`, as a combobox names its list box,
// whose keys belong to that element. Native radio buttons outside every widget are grouped by name and
// form, the group marked on its first button.
const markRestingInPage = (tools: CompositeTools): number => {
  const { attribute, widgets }: CompositeRequest = tools.request
  for (const element of document.querySelectorAll(`[${CSS.escape(attribute)}]`)) {
    element.removeAttribute(attribute)
  }
  const isNamedByAnother = (element: Element): boolean => {
    const id: string = CSS.escape(element.id)
    const naming: string = `[aria-controls~="${id}"], [aria-owns~="${id}"]`
    return element.id !== '' && document.querySelector(naming) !== null
  }
  const isReachable = (element: Element): boolean =>
    element.checkVisibility() &&
    element.closest('[inert]') === null &&
    (element.parentElement?.closest(widgets) ?? null) === null &&
    element.getAttribute('aria-disabled') !== 'true' &&
    !isNamedByAnother(element)
  const radios: readonly HTMLInputElement[] = Array.from(
    document.querySelectorAll<HTMLInputElement>('input[type="radio"][name]'),
  ).filter(
    (radio: HTMLInputElement): boolean =>
      radio.name !== '' && radio.checkVisibility() && !radio.disabled && radio.closest(widgets) === null,
  )
  const isSameGroup = (left: HTMLInputElement, right: HTMLInputElement): boolean =>
    left.name === right.name && left.form === right.form
  const firstRadios: readonly HTMLInputElement[] = radios.filter(
    (radio: HTMLInputElement, index: number): boolean =>
      radios.findIndex((other: HTMLInputElement): boolean => isSameGroup(other, radio)) === index &&
      radios.some((other: HTMLInputElement): boolean => other !== radio && isSameGroup(other, radio)),
  )
  const marked: readonly Element[] = [
    ...Array.from(document.querySelectorAll(widgets)).filter((element: Element): boolean => isReachable(element)),
    ...firstRadios,
  ]
  for (const [index, element] of marked.entries()) {
    element.setAttribute(attribute, String(index))
  }
  return marked.length
}

const markPresentInPage = (tools: CompositeTools): number => {
  const { present, widgets }: CompositeRequest = tools.request
  for (const element of document.querySelectorAll(`[${CSS.escape(present)}]`)) {
    element.removeAttribute(present)
  }
  const rendered: readonly Element[] = Array.from(document.querySelectorAll(widgets)).filter(
    (element: Element): boolean => element.checkVisibility(),
  )
  for (const element of rendered) {
    element.setAttribute(present, '')
  }
  return rendered.length
}

// The widgets a disclosure revealed that hold focus, or whose controlling element does: the outermost new
// ones, so a submenu revealed inside a menu bar counts while the bar does not. Each is marked while it is
// asked; only those holding focus keep their mark, numbered first.
const markRevealedInPage = (tools: CompositeTools): number => {
  const { attribute, present, widgets }: CompositeRequest = tools.request
  for (const element of document.querySelectorAll(`[${CSS.escape(attribute)}]`)) {
    element.removeAttribute(attribute)
  }
  const revealed: readonly Element[] = Array.from(document.querySelectorAll(widgets)).filter(
    (element: Element): boolean => element.checkVisibility() && !element.hasAttribute(present),
  )
  const outermost: readonly Element[] = revealed.filter(
    (element: Element): boolean =>
      !revealed.some((other: Element): boolean => other !== element && other.contains(element)),
  )
  const held: readonly Element[] = outermost.filter((element: Element): boolean => {
    element.setAttribute(attribute, String(tools.request.index))
    const widget: WidgetElements | undefined = tools.widgetOf(tools)
    element.removeAttribute(attribute)
    return widget !== undefined && tools.activeIndex(widget, tools) !== tools.request.outside
  })
  for (const [index, element] of held.entries()) {
    element.setAttribute(attribute, String(index))
  }
  return held.length
}

/** A widget's declaration and items as the page renders them. */
interface WidgetShape extends CompositeWidget {
  readonly disabled: readonly boolean[]
  readonly tabbableItems: number
  readonly hasTabStop: boolean
  readonly isNative: boolean
}

const shapeInPage = (tools: CompositeTools): WidgetShape | undefined => {
  const widget: WidgetElements | undefined = tools.widgetOf(tools)
  if (widget === undefined) {
    return undefined
  }
  const declared: string | null = widget.anchor.getAttribute('aria-orientation')
  const isNative: boolean = widget.container === null
  return {
    role: widget.role,
    orientation: declared === 'horizontal' || declared === 'vertical' ? declared : undefined,
    isRightToLeft: getComputedStyle(widget.anchor).direction === 'rtl',
    disabled: widget.items.map(
      (item: HTMLElement): boolean => item.getAttribute('aria-disabled') === 'true' || item.matches(':disabled'),
    ),
    // A native radio button is left out wherever it sits: the browser gives its group one Tab stop.
    tabbableItems: widget.items.filter(
      (item: HTMLElement): boolean => !(item instanceof HTMLInputElement) && tools.isTabbable(item),
    ).length,
    hasTabStop: isNative || tools.entryOf(widget, tools) !== undefined,
    isNative,
  }
}

// Focus where Tab enters the widget: its first element in the Tab sequence, or a native group's checked
// button. A widget that forwards focus from itself to its current item does so here, as it would on Tab.
const focusEntryInPage = (tools: CompositeTools): number => {
  const widget: WidgetElements | undefined = tools.widgetOf(tools)
  if (widget === undefined) {
    return tools.request.outside
  }
  tools.entryOf(widget, tools)?.focus()
  return tools.activeIndex(widget, tools)
}

const activeIndexInPage = (tools: CompositeTools): number => {
  const widget: WidgetElements | undefined = tools.widgetOf(tools)
  return widget === undefined ? tools.request.outside : tools.activeIndex(widget, tools)
}

const isFocusInsideInPage = (tools: CompositeTools): boolean => {
  const widget: WidgetElements | undefined = tools.widgetOf(tools)
  return widget !== undefined && tools.isFocusInside(widget, tools)
}

// How long a widget may take to move focus after a key. A library that moves it once its state has
// rendered does so a tick or a frame later; a key that moves nothing costs this wait once.
const KEY_SETTLE_MS: number = 500

// Nothing moved in time is not an error: the index read next says so.
const activeAfter = async (page: Page, index: number, before: number): Promise<number> => {
  await isMetInPage(page, `${inPage(activeIndexInPage, index)} !== ${String(before)}`, KEY_SETTLE_MS)
  return await page.evaluate<number>(inPage(activeIndexInPage, index))
}

const pressed = async (page: Page, index: number, key: string, from: number): Promise<ArrowMove> => {
  await page.keyboard.press(key)
  return { key, from, to: await activeAfter(page, index, from) }
}

const isSettled = (move: ArrowMove): boolean => hasMoved(move) || move.to === OUTSIDE

// Each axis in turn: the next key, then the previous one when the next did not move, as at the last item
// of a widget that does not wrap. The first axis that moves settles it.
const drivePairs = async (
  page: Page,
  index: number,
  pairs: readonly ArrowPair[],
  from: number,
): Promise<readonly ArrowMove[]> => {
  const [pair, ...rest]: readonly ArrowPair[] = pairs
  if (pair === undefined) {
    return []
  }
  const next: ArrowMove = await pressed(page, index, pair.next, from)
  if (isSettled(next)) {
    return [next]
  }
  const previous: ArrowMove = await pressed(page, index, pair.previous, next.to)
  return isSettled(previous)
    ? [next, previous]
    : [next, previous, ...(await drivePairs(page, index, rest, previous.to))]
}

const doesTabStayInside = async (page: Page, index: number): Promise<boolean> => {
  await page.evaluate<number>(inPage(focusEntryInPage, index))
  await page.keyboard.press('Tab')
  return await page.evaluate<boolean>(inPage(isFocusInsideInPage, index))
}

/** Where a widget's findings go. */
interface CompositeDrive {
  readonly page: Page
  readonly place: FindingPlace
}

const widgetVisit = async (drive: CompositeDrive, index: number, probe: CompositeProbe): Promise<Visit> => {
  const facts: TargetFacts | undefined = await targetFacts(drive.page, `[${COMPOSITE_ATTRIBUTE}="${String(index)}"]`)
  const label: string = facts?.label ?? 'a widget'
  return merged(
    compositeDefects(probe).map(
      (defect: string): Visit =>
        carried(findingAt(drive.place, 'composite-keyboard', `${label} (${probe.widget.role}): ${defect}`), facts),
    ),
  )
}

const MOVABLE_ITEMS: number = 2

const restingMoves = async (page: Page, index: number, shape: WidgetShape): Promise<readonly ArrowMove[]> => {
  const from: number = await page.evaluate<number>(inPage(focusEntryInPage, index))
  return from === OUTSIDE || shape.disabled.length < MOVABLE_ITEMS
    ? []
    : await drivePairs(page, index, compositeArrows(shape), from)
}

const restingWidgetVisit = async (drive: CompositeDrive, index: number): Promise<Visit> => {
  const shape: WidgetShape | undefined = await drive.page.evaluate<WidgetShape | undefined>(inPage(shapeInPage, index))
  if (shape === undefined) {
    return EMPTY_VISIT
  }
  const isTabStaying: boolean = shape.hasTabStop && (await doesTabStayInside(drive.page, index))
  return await widgetVisit(drive, index, {
    widget: shape,
    isPopup: false,
    disabled: shape.disabled,
    tabbableItems: shape.tabbableItems,
    hasTabStop: shape.hasTabStop,
    doesTabStayInside: isTabStaying,
    moves: shape.hasTabStop ? await restingMoves(drive.page, index, shape) : [],
  })
}

const popupWidgetVisit = async (drive: CompositeDrive, index: number): Promise<Visit> => {
  const shape: WidgetShape | undefined = await drive.page.evaluate<WidgetShape | undefined>(inPage(shapeInPage, index))
  const from: number = await drive.page.evaluate<number>(inPage(activeIndexInPage, index))
  if (shape === undefined || from === OUTSIDE || shape.disabled.length < MOVABLE_ITEMS) {
    return EMPTY_VISIT
  }
  return await widgetVisit(drive, index, {
    widget: shape,
    isPopup: true,
    disabled: shape.disabled,
    tabbableItems: shape.tabbableItems,
    hasTabStop: shape.hasTabStop,
    doesTabStayInside: false,
    moves: await drivePairs(drive.page, index, compositeArrows(shape), from),
  })
}

const visitEach = async (
  drive: CompositeDrive,
  count: number,
  visit: (drive: CompositeDrive, index: number) => Promise<Visit>,
  index: number,
): Promise<readonly Visit[]> => {
  if (index >= count) {
    return []
  }
  const url: string = drive.page.url()
  const visited: Visit = await visit(drive, index)
  // A key that navigated left the page the remaining widgets were on.
  if (drive.page.url() !== url) {
    return [
      visited,
      {
        ...EMPTY_VISIT,
        notes: [
          `${drive.place.location}: a key in a composite widget changed the URL, so the widgets after it went ` +
            'unchecked',
        ],
      },
    ]
  }
  return [visited, ...(await visitEach(drive, count, visit, index + 1))]
}

/**
 * Drive every composite widget rendered at rest by the keyboard, and judge its Tab stop and arrow keys.
 * @param page the page, settled at rest.
 * @param place where its findings go.
 * @returns the findings, and a note when a key navigated away.
 */
export const restingCompositeVisit = async (page: Page, place: FindingPlace): Promise<Visit> => {
  const count: number = await page.evaluate<number>(inPage(markRestingInPage, 0))
  return merged(await visitEach({ page, place }, count, restingWidgetVisit, 0))
}

/**
 * Mark every composite widget rendered now, so one a disclosure reveals next can be told from them.
 * @param page the page, settled before the disclosure is activated.
 */
export const markPresentComposites = async (page: Page): Promise<void> => {
  await page.evaluate<number>(inPage(markPresentInPage, 0))
}

/**
 * Judge the arrow keys of each composite widget a disclosure just revealed that holds focus, or whose
 * controlling element holds it, as a combobox does.
 * @param page the page, with the disclosure open.
 * @param place where its findings go.
 * @returns the findings.
 */
export const revealedCompositeVisit = async (page: Page, place: FindingPlace): Promise<Visit> => {
  const count: number = await page.evaluate<number>(inPage(markRevealedInPage, 0))
  return merged(await visitEach({ page, place }, count, popupWidgetVisit, 0))
}
