// The sweep's disclosure pass: every disclosure opened, what it reveals judged, and how it answers the
// keyboard.
import { errors, type Locator, type Page, test } from '@playwright/test'
import { type BrowserFinding, type FindingPlace, findingAt } from './browser-findings.js'
import { disclosureDefects } from './browser-rules.js'
import { type TargetFacts, targetFacts } from './layout.js'
import { isPerceivableWithin } from './perceivable.js'
import { axeFindings, judgeRestingPlace, type PlaceScan, settleFindings } from './scanner.js'
import { settleForScan } from './settle.js'
import { ARIA_RESTING_RULES, ariaVisit } from './sweep-aria.js'
import { markPresentComposites, restingCompositeVisit, revealedCompositeVisit } from './sweep-composites.js'
import {
  type JudgedDialog,
  judgeDialog,
  markPresentDialogs,
  NEW_DIALOG_SELECTOR,
  type OpenedDialog,
  openedDialog,
} from './sweep-dialogs.js'
import { carried } from './sweep-interaction.js'
import { type InteractionPlace, interactionPlan, placeUnits } from './sweep-interaction-plan.js'
import {
  armBudget,
  type Budget,
  DISCLOSURE_ATTRIBUTE,
  type Discovery,
  type DomDocument,
  type DomElement,
  EMPTY_VISIT,
  finishPass,
  healthOf,
  isChromium,
  itemLabel,
  loadLocation,
  locationOfItem,
  merged,
  passUnits,
  placeAt,
  preparePage,
  RESTING_JUDGED,
  readDiscovery,
  type Shard,
  type ShardVisits,
  shardLocations,
  type Visit,
  visitAll,
} from './sweep-shared.js'

// The browser surface the page functions below touch, declared for the reason `settle.ts` records.
declare const document: DomDocument
declare const location: { readonly href: string }
declare const history: { readonly length: number }

/**
 * Every control that discloses something, closed. A control that opens a dialog through an HTML invoker
 * command declares it as plainly as `aria-haspopup` does; a button that calls `showModal()` from a handler
 * declares nothing, and no control is activated on a guess.
 */
export const DISCLOSURES: string =
  '[aria-expanded="false"], [aria-haspopup]:not([aria-haspopup="false"]), details:not([open]) > summary, ' +
  '[commandfor][command="show-modal"]'

/**
 * Mark the disclosures it is safe to open: visible, outside forms unless declared `type="button"`, and never a
 * link that navigates. Shared with the reduced-motion check, which opens the first of them.
 * @param selectorAndAttribute the disclosures' selector, and the attribute each is marked with by its index.
 * @returns how many were marked.
 */
export const markDisclosuresInPage = (selectorAndAttribute: readonly [string, string]): number => {
  const [selector, attribute]: readonly [string, string] = selectorAndAttribute
  const isAvailable = (element: DomElement): boolean =>
    element.checkVisibility() &&
    element.closest('[inert]') === null &&
    element.getAttribute('disabled') === null &&
    element.getAttribute('aria-disabled') !== 'true'
  const safe: readonly DomElement[] = Array.from(document.querySelectorAll(selector)).filter(
    (element: DomElement): boolean => {
      const tag: string = element.tagName.toLowerCase()
      const href: string | null = element.getAttribute('href')
      const isNavigating: boolean = tag === 'a' && href !== null && !href.startsWith('#')
      const isSubmitting: boolean =
        element.closest('form') !== null && !(tag === 'button' && element.getAttribute('type') === 'button')
      return isAvailable(element) && !isNavigating && !isSubmitting
    },
  )
  for (const [index, element] of safe.entries()) {
    element.setAttribute(attribute, String(index))
  }
  return safe.length
}

interface DisclosureFacts {
  readonly url: string
  readonly history: number
  readonly expanded: string | null
  readonly isPopup: boolean
  /** Whether a dialog that was not rendered before the control was activated is rendered now. */
  readonly isDialogOpen: boolean
  readonly isFocusOnControl: boolean
}

const disclosureFactsInPage = ([selector, newDialogs]: readonly [string, string]): DisclosureFacts => {
  const control: DomElement | null = document.querySelector(selector)
  const isDialogOpen: boolean = Array.from(document.querySelectorAll(newDialogs)).some((dialog: DomElement): boolean =>
    dialog.checkVisibility(),
  )
  const active: DomElement | null = document.activeElement
  const attributeOf = (name: string): string | null => control?.getAttribute(name) ?? null
  const expanded: string | null =
    control?.tagName.toLowerCase() === 'summary'
      ? String(typeof control.parentElement?.getAttribute('open') === 'string')
      : attributeOf('aria-expanded')
  const containsFocus = (container: DomElement | null): boolean =>
    container !== null && active !== null && (container === active || container.contains(active))
  return {
    url: location.href,
    history: history.length,
    expanded,
    isPopup: (attributeOf('aria-haspopup') ?? 'false') !== 'false',
    isDialogOpen,
    isFocusOnControl: containsFocus(control),
  }
}

// Whether the control a disclosure opened from holds focus again, and nothing it opened is still open.
const isClosedToControlInPage = (selector: string): boolean => {
  const control: DomElement | null = document.querySelector(selector)
  const active: DomElement | null = document.activeElement
  const modal: DomElement | null = document.querySelector(
    'dialog:modal, [role="dialog"][aria-modal="true"], [role="alertdialog"][aria-modal="true"]',
  )
  return (
    control !== null &&
    active !== null &&
    (control === active || control.contains(active)) &&
    control.getAttribute('aria-expanded') !== 'true' &&
    modal?.checkVisibility() !== true
  )
}

// How long a disclosure may take to close and hand focus back after Escape. Libraries such as Radix
// close in the key's own task and move focus once their exit has rendered, a tick or a frame later.
const FOCUS_RETURN_MS: number = 1000
const OPENING_TIMEOUT_MS: number = 5000

const factsOf = async (page: Page, selector: string): Promise<DisclosureFacts> =>
  await page.evaluate(disclosureFactsInPage, [selector, NEW_DIALOG_SELECTOR] as const)

const closedAfterEscape = async (page: Page, selector: string, isDismissible: boolean): Promise<DisclosureFacts> => {
  if (!isDismissible) {
    return await factsOf(page, selector)
  }
  try {
    await page.waitForFunction(isClosedToControlInPage, selector, {
      timeout: FOCUS_RETURN_MS,
    })
  } catch (error: unknown) {
    // Not closed back to its control in time: the facts read next say which half failed.
    if (!(error instanceof errors.TimeoutError)) {
      throw error
    }
  }
  return await factsOf(page, selector)
}

interface OpenedDisclosure {
  readonly wasOpened: boolean
  readonly motion: InteractionPlace['motion']
  readonly page: Page
  readonly selector: string
  readonly control: Locator
  readonly before: DisclosureFacts
  readonly opened: DisclosureFacts
  readonly place: FindingPlace
  readonly facts: TargetFacts | undefined
}

const labelOfDisclosure = (facts: TargetFacts | undefined): string => facts?.label ?? 'a disclosure'

const isReturnedVisibly = async (control: Locator, closed: DisclosureFacts): Promise<boolean> =>
  closed.isFocusOnControl && (await isPerceivableWithin(control))

const closeRemaining = async (
  control: Locator,
  shouldClose: boolean,
  motion: InteractionPlace['motion'],
): Promise<void> => {
  if (shouldClose && (await control.isVisible())) {
    if (motion === 'no-preference') {
      const role: string | null = await control.getAttribute('role', { timeout: OPENING_TIMEOUT_MS })
      await control.press(role === 'treeitem' ? 'ArrowLeft' : 'Enter', { timeout: OPENING_TIMEOUT_MS })
    } else {
      await control.click({ timeout: OPENING_TIMEOUT_MS })
    }
  }
}

// A dialog the disclosure opened is judged by the dialog pattern, which closes it on Escape; anything
// still open is closed with the pass's input method, so the next disclosure starts from a closed page.
const dismissDialog = async (disclosure: OpenedDisclosure, dialog: OpenedDialog): Promise<readonly Visit[]> => {
  const judged: JudgedDialog = await judgeDialog(
    {
      page: disclosure.page,
      opener: disclosure.control,
      openerSelector: disclosure.selector,
      place: disclosure.place,
      facts: disclosure.facts,
    },
    dialog,
  )
  await closeRemaining(disclosure.control, judged.isOpen, disclosure.motion)
  await settleForScan(disclosure.page, disclosure.motion === 'reduce')
  return judged.visits
}

// The arrow keys of a widget it revealed, then Escape for a popup, then the keyboard judgement, then close
// anything remaining with the pass's input method, so the next disclosure starts from a closed page.
const dismissDisclosure = async (disclosure: OpenedDisclosure): Promise<readonly Visit[]> => {
  const { page, selector, control, before, place, facts }: OpenedDisclosure = disclosure
  const dialog: OpenedDialog | undefined = disclosure.wasOpened ? await openedDialog(page) : undefined
  if (dialog !== undefined) {
    return await dismissDialog(disclosure, dialog)
  }
  const revealed: Visit = disclosure.wasOpened ? await revealedCompositeVisit(page, place) : EMPTY_VISIT
  const isDismissible: boolean = disclosure.wasOpened && before.isPopup
  if (isDismissible) {
    await page.keyboard.press('Escape')
  }
  const closed: DisclosureFacts = await closedAfterEscape(page, selector, isDismissible)
  const isClosedOnEscape: boolean = isDismissible && closed.expanded !== 'true'
  await closeRemaining(control, disclosure.wasOpened && !isClosedOnEscape, disclosure.motion)
  await settleForScan(page, disclosure.motion === 'reduce')
  return [
    revealed,
    ...disclosureDefects({
      wasOpened: disclosure.wasOpened,
      isPopup: before.isPopup,
      closedOnEscape: isClosedOnEscape,
      focusReturned: await isReturnedVisibly(control, closed),
    }).map(
      (defect: string): Visit =>
        carried(findingAt(place, 'disclosure-keyboard', `${labelOfDisclosure(facts)}: ${defect}`), facts),
    ),
  ]
}

const judgeOpened = async (page: Page, place: FindingPlace, isStory: boolean): Promise<Visit> => {
  const unsettled: readonly BrowserFinding[] = await settleFindings(page, place)
  const judged: PlaceScan = await judgeRestingPlace(page, place)
  const axe: readonly BrowserFinding[] = await axeFindings(page, place)
  return merged([
    {
      ...judged,
      findings: [...unsettled, ...judged.findings, ...axe],
      notes: [],
    },
    await ariaVisit(page, place, isStory),
  ])
}

interface DrivenDisclosure {
  readonly visit: Visit
  readonly isAborted: boolean
}

const isOpenedInPage = ([selector, newDialogs]: readonly [string, string]): boolean => {
  const control: DomElement | null = document.querySelector(selector)
  if (control === null) {
    return false
  }
  if (control.getAttribute('aria-expanded') === 'true') {
    return true
  }
  if (control.tagName.toLowerCase() === 'summary') {
    return typeof control.parentElement?.getAttribute('open') === 'string'
  }
  return Array.from(document.querySelectorAll(newDialogs)).some((dialog: DomElement): boolean =>
    dialog.checkVisibility(),
  )
}

const OPENING_KEYS: Readonly<Record<string, string>> = { combobox: 'ArrowDown', treeitem: 'ArrowRight' }

const openWithKeyboard = async (page: Page, control: Locator, selector: string): Promise<void> => {
  await control.focus({ timeout: OPENING_TIMEOUT_MS })
  if (await page.evaluate(isOpenedInPage, [selector, NEW_DIALOG_SELECTOR] as const)) {
    return
  }
  const role: string | null = await control.getAttribute('role', { timeout: OPENING_TIMEOUT_MS })
  await page.keyboard.press(OPENING_KEYS[role ?? ''] ?? 'Enter')
}

const openedAfterActivation = async (page: Page, selector: string): Promise<DisclosureFacts> => {
  try {
    await page.waitForFunction(isOpenedInPage, [selector, NEW_DIALOG_SELECTOR] as const, {
      timeout: OPENING_TIMEOUT_MS,
    })
  } catch (error: unknown) {
    if (!(error instanceof errors.TimeoutError)) {
      throw error
    }
  }
  return await factsOf(page, selector)
}

const driveDisclosure = async (page: Page, item: InteractionPlace, index: number): Promise<DrivenDisclosure> => {
  const selector: string = `[${DISCLOSURE_ATTRIBUTE}="${String(index)}"]`
  const control: Locator = page.locator(selector)
  if (!(await control.isVisible())) {
    return { visit: EMPTY_VISIT, isAborted: false }
  }
  await markPresentDialogs(page)
  await markPresentComposites(page)
  const before: DisclosureFacts = await factsOf(page, selector)
  const facts: TargetFacts | undefined = await targetFacts(page, selector)
  if (item.motion === 'no-preference') {
    await openWithKeyboard(page, control, selector)
  } else {
    await control.click({ timeout: OPENING_TIMEOUT_MS })
  }
  await settleForScan(page, item.motion === 'reduce')
  const opened: DisclosureFacts = await openedAfterActivation(page, selector)
  if (opened.url !== before.url || opened.history !== before.history) {
    return {
      visit: {
        ...EMPTY_VISIT,
        notes: [
          `${item.location.location}: opening ${labelOfDisclosure(facts)} changed the URL, so the disclosures ` +
            'after it went unchecked',
        ],
      },
      isAborted: true,
    }
  }
  const place: FindingPlace = placeAt(
    page,
    item.location,
    item.viewport,
    `disclosure ${labelOfDisclosure(facts)}${item.motion === 'no-preference' ? ', normal motion' : ''}`,
  )
  const revealed: Visit = item.motion === 'reduce' ? await judgeOpened(page, place, item.location.isStory) : EMPTY_VISIT
  const keyboard: readonly Visit[] = await dismissDisclosure({
    wasOpened: opened.expanded === 'true' || opened.isDialogOpen,
    page,
    selector,
    control,
    before,
    opened,
    place,
    facts,
    motion: item.motion,
  })
  return { visit: merged([revealed, ...keyboard]), isAborted: false }
}

const driveDisclosures = async (
  page: Page,
  item: InteractionPlace,
  index: number,
  count: number,
): Promise<readonly Visit[]> => {
  if (index >= count) {
    return []
  }
  const driven: DrivenDisclosure = await driveDisclosure(page, item, index)
  return driven.isAborted ? [driven.visit] : [driven.visit, ...(await driveDisclosures(page, item, index + 1, count))]
}

// The composite widgets at rest are driven after the disclosures, on the page as the disclosures left it:
// closed again. A disclosure that navigated left another page, so the place is loaded afresh first.
const restingComposites = async (page: Page, item: InteractionPlace, loadedUrl: string): Promise<Visit> => {
  const place: FindingPlace = placeAt(
    page,
    item.location,
    item.viewport,
    item.motion === 'reduce' ? 'keyboard' : 'keyboard, normal motion',
  )
  const reloaded: readonly BrowserFinding[] =
    page.url() === loadedUrl
      ? []
      : await loadLocation(page, item.location, item.viewport, { ...place, motion: item.motion })
  await settleForScan(page, item.motion === 'reduce')
  return merged([{ ...EMPTY_VISIT, findings: reloaded }, await restingCompositeVisit(page, place)])
}

const disclosureVisit = async (page: Page, item: InteractionPlace): Promise<Visit> => {
  const place: FindingPlace = placeAt(page, item.location, item.viewport, 'resting')
  const loaded: readonly BrowserFinding[] = await loadLocation(page, item.location, item.viewport, {
    ...place,
    motion: item.motion,
  })
  await settleForScan(page, item.motion === 'reduce')
  const loadedUrl: string = page.url()
  const count: number = await page.evaluate(markDisclosuresInPage, [DISCLOSURES, DISCLOSURE_ATTRIBUTE] as const)
  const driven: readonly Visit[] = await driveDisclosures(page, item, 0, count)
  const composites: Visit = await restingComposites(page, item, loadedUrl)
  return merged([{ ...EMPTY_VISIT, findings: loaded }, ...driven, composites, await healthOf(page, place)])
}

const DISCLOSURE_RULES: readonly string[] = [
  ...RESTING_JUDGED,
  ...ARIA_RESTING_RULES,
  'disclosure-keyboard',
  'dialog-keyboard',
  'composite-keyboard',
]

/**
 * Open every disclosure on one shard of the locations, judge what it reveals, and judge how it answers
 * the keyboard.
 * @param page the test's page.
 * @param shard the part of the pass this test visits.
 */
export const expectDisclosureStates = async (page: Page, shard: Shard): Promise<void> => {
  test.info().skip(!isChromium(page), 'the disclosure pass runs in Chromium')
  const discovery: Discovery = readDiscovery(test.info())
  const items: readonly InteractionPlace[] = interactionPlan(discovery.locations).places
  const budget: Budget = armBudget(page, 'disclosures', shard, passUnits(items, placeUnits))
  await preparePage(page)
  const { visits, taken }: ShardVisits<InteractionPlace> = await visitAll(items, {
    budget,
    unitsOf: placeUnits,
    labelOf: itemLabel,
    visit: async (item: InteractionPlace): Promise<Visit> => await disclosureVisit(page, item),
  })
  finishPass(page, {
    budget,
    pass: 'disclosures',
    visits,
    judgedRules: DISCLOSURE_RULES,
    locations: shardLocations(taken, locationOfItem),
  })
}
