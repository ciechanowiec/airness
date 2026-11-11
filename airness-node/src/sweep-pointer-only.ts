// The interaction pass's check that what a page invites a pointer to operate, the keyboard can operate too
// (rule `pointer-only`). Before the keyboard walk, the page is asked to mark every element focus reaches;
// after it, every element that presents as operable is read, and the verdicts in `pointer-only` decide
// which of them nothing the keyboard reached stands in for. Of those, only one a pointer can actually reach -
// rendered, in the page, and answering at its own centre - is reported, so a visually hidden or covered
// element is left to the rules that judge those.
import type { Page } from '@playwright/test'
import { classifyHitTarget, type HitTargetProbe } from './a11y-hit-target.js'
import { type BrowserFinding, type FindingPlace, findingAt } from './browser-findings.js'
import { type TargetFacts, targetFacts } from './layout.js'
import {
  COMPOSITE_OWNERS,
  INTERACTIVE_ROLES,
  isWalkComplete,
  type LabelControl,
  OPERABLE_SELECTOR,
  type OwnFacts,
  type PointerCandidate,
  pointerOnlyMessage,
  pointerOnlySignal,
  type StandInFacts,
} from './pointer-only.js'
import { hitTargetInPage } from './sweep-hit-target.js'
import {
  type ComputedStyleOf,
  type DomDocument,
  type DomElement,
  type DomRect,
  EMPTY_VISIT,
  merged,
  type Visit,
} from './sweep-shared.js'

// The browser surface the page functions below touch, declared for the reason `settle.ts` records.
declare const document: DomDocument
declare const getComputedStyle: ComputedStyleOf
declare const scrollBy: (options: ScrollToOptions) => void

/** The attribute the page sets on every element keyboard focus reaches during the walk. */
const REACHED_ATTRIBUTE: string = 'data-airness-reached'

/** The value of that attribute on the first element reached, which a walk that wrapped comes back to. */
const FIRST_REACHED: string = 'first'

/** The attribute naming, for one reading, each element that presents as operable. */
const CANDIDATE_ATTRIBUTE: string = 'data-airness-pointer-only'

// A page full of clickable rows reports its first few; each one costs a probe, and the rest add nothing an
// agent repairing the first would not find.
const MAX_PROBED: number = 20

/** How a finding on an element is judged against the exemptions above it, as the interaction pass does it. */
export type CarryFinding = (finding: BrowserFinding, facts: TargetFacts | undefined) => Visit

// Marks every element focus reaches from now on, the first one apart, without moving focus itself. The page
// keeps its own state across the walk, which no Node-side record of the stops could: an element inside a
// shadow root is reported as its host, which is what stands in for it.
const trackReachInPage = ([attribute, first]: readonly [string, string]): void => {
  document.addEventListener(
    'focusin',
    (event: FocusEvent): void => {
      const target: EventTarget | null = event.target
      if (!(target instanceof Element) || target.hasAttribute(attribute)) {
        return
      }
      target.setAttribute(attribute, document.querySelector(`[${CSS.escape(attribute)}]`) === null ? first : '')
    },
    { capture: true },
  )
}

/**
 * Ask the page to mark every element keyboard focus reaches, before the walk begins.
 * @param page the page, loaded and settled, with focus at its start.
 */
export const trackKeyboardReach = async (page: Page): Promise<void> => {
  await page.evaluate(trackReachInPage, [REACHED_ATTRIBUTE, FIRST_REACHED] as const)
}

/** How the walk ended, as the page saw it. */
interface WalkEnd {
  readonly reached: number
  readonly hasWrapped: boolean
}

const walkEndInPage = ([attribute, first]: readonly [string, string]): WalkEnd => {
  const active: DomElement | null = document.activeElement
  return {
    reached: document.querySelectorAll(`[${CSS.escape(attribute)}]`).length,
    hasWrapped: active === null || active === document.body || active.getAttribute(attribute) === first,
  }
}

/** What the page functions reading the candidates need to know. */
interface CandidateQuery {
  readonly reached: string
  readonly candidate: string
  readonly roles: readonly string[]
  readonly owners: string
  readonly operable: string
  /** What makes a control unavailable to focus: disabled, or inert. */
  readonly unavailable: string
}

// Every element presenting a pointer cursor of its own, an interactive role or an inline `onclick`, marked
// with its index in document order, with what it shows of itself. The test for presenting is repeated here
// only to keep the reading small on a page of thousands of elements; the policy decides.
const ownFactsInPage = (query: CandidateQuery): readonly OwnFacts[] => {
  const earlier: readonly Element[] = Array.from(document.querySelectorAll(`[${CSS.escape(query.candidate)}]`))
  for (const marked of earlier) {
    marked.removeAttribute(query.candidate)
  }
  const read = (element: Element): OwnFacts => {
    const parent: Element | null = element.parentElement
    return {
      tag: element.tagName.toLowerCase(),
      role: element.getAttribute('role')?.trim().split(/\s+/u, 1)[0] ?? '',
      hasOnclick: element.hasAttribute('onclick'),
      cursor: getComputedStyle(element).cursor,
      parentCursor: parent === null ? 'auto' : getComputedStyle(parent).cursor,
      isReached: element.hasAttribute(query.reached),
      isDisabled: element.matches(':disabled') || element.closest('[aria-disabled="true"]') !== null,
      isInert: element.closest('[inert]') !== null,
      isRendered: element.checkVisibility({ visibilityProperty: true, opacityProperty: true }),
      isRoot: element === document.documentElement || element === document.body,
    }
  }
  const presenting: readonly (readonly [Element, OwnFacts])[] = Array.from(
    document.querySelectorAll('*'),
    (element: Element): readonly [Element, OwnFacts] => [element, read(element)],
  ).filter(
    ([, facts]: readonly [Element, OwnFacts]): boolean =>
      query.roles.includes(facts.role) ||
      facts.hasOnclick ||
      (facts.cursor === 'pointer' && facts.parentCursor !== 'pointer'),
  )
  return presenting.map(([element, facts]: readonly [Element, OwnFacts], index: number): OwnFacts => {
    element.setAttribute(query.candidate, String(index))
    return facts
  })
}

// What stands in for each marked element, in the order they were marked: a reached control around it or
// inside it, a composite widget or radio group the keyboard entered, a reached link to the same address, and
// for a label, whether its control can be focused.
const standInFactsInPage = (query: CandidateQuery): readonly StandInFacts[] => {
  const reached: string = `[${CSS.escape(query.reached)}]`
  const isFocusable = (control: HTMLElement): boolean => control.tabIndex >= 0 && !control.matches(query.unavailable)
  const hasStandIn = (element: Element | null): boolean =>
    element !== null &&
    ((element.matches(reached) && element.matches(query.operable)) ||
      (element instanceof HTMLLabelElement && element.control !== null && isFocusable(element.control)) ||
      hasStandIn(element.parentElement))
  const isGroupEntered = (element: Element): boolean => {
    const owner: Element | null = element.closest(query.owners)
    const group: readonly Element[] =
      element instanceof HTMLInputElement && element.type === 'radio' && element.name !== ''
        ? Array.from(document.querySelectorAll(`input[type="radio"][name="${CSS.escape(element.name)}"]`)).filter(
            (member: Element): boolean => member instanceof HTMLInputElement && member.form === element.form,
          )
        : []
    return (
      (owner !== null &&
        (owner.matches(reached) ||
          owner.querySelector(reached) !== null ||
          owner.hasAttribute('aria-activedescendant'))) ||
      group.some((member: Element): boolean => member.matches(reached))
    )
  }
  const labelControl = (element: Element): LabelControl => {
    const control: HTMLElement | null = element instanceof HTMLLabelElement ? element.control : null
    if (control === null) {
      return 'none'
    }
    if (control.matches(':disabled')) {
      return 'disabled'
    }
    return isFocusable(control) ? 'focusable' : 'unfocusable'
  }
  return Array.from(
    document.querySelectorAll(`[${CSS.escape(query.candidate)}]`),
    (element: Element): StandInFacts => ({
      hasReachedOperableAncestor: hasStandIn(element.parentElement),
      hasReachedOperableDescendant: Array.from(element.querySelectorAll(reached)).some((descendant: Element): boolean =>
        descendant.matches(query.operable),
      ),
      isInReachedGroup: isGroupEntered(element),
      isSameHrefReached:
        element.matches('a[href]') &&
        document.querySelector(`a[href="${CSS.escape(element.getAttribute('href') ?? '')}"]${reached}`) !== null,
      labelControl: labelControl(element),
    }),
  )
}

// Brings an element into the viewport by scrolling the window alone. Scrolling it into view the way a locator
// does would scroll a carousel's track as well, and a slide brought in that way is one the page's own
// controls bring in; left clipped by its track, it answers nothing at its centre and is not reported.
const revealInWindowInPage = (selector: string): void => {
  const element: DomElement | null = document.querySelector(selector)
  if (element === null) {
    return
  }
  const halves: number = 2
  const box: DomRect = element.getBoundingClientRect()
  const height: number = document.documentElement.clientHeight
  if (box.top < 0 || box.bottom > height) {
    scrollBy({ top: box.top + box.height / halves - height / halves, behavior: 'instant' })
  }
}

/** How long reading a marked element's hit target may take before the element is taken to be gone. */
const PROBE_TIMEOUT_MS: number = 5000

/** An element that presents as operable and that nothing the keyboard reached stands in for. */
interface MarkedCandidate {
  readonly index: number
  readonly signal: string
}

// An element the page removed since it was marked has nothing to probe.
const probeOf = async (page: Page, selector: string): Promise<HitTargetProbe | undefined> => {
  try {
    return await page.locator(selector).evaluate(hitTargetInPage, undefined, { timeout: PROBE_TIMEOUT_MS })
  } catch {
    return undefined
  }
}

const pointerOnlyAt = async (
  page: Page,
  place: FindingPlace,
  marked: MarkedCandidate,
  carry: CarryFinding,
): Promise<Visit> => {
  const selector: string = `[${CANDIDATE_ATTRIBUTE}="${String(marked.index)}"]`
  await page.evaluate(revealInWindowInPage, selector)
  const probe: HitTargetProbe | undefined = await probeOf(page, selector)
  if (probe === undefined || classifyHitTarget(probe).verdict !== 'hoverable') {
    return EMPTY_VISIT
  }
  const facts: TargetFacts | undefined = await targetFacts(page, selector)
  return carry(findingAt(place, 'pointer-only', pointerOnlyMessage(facts?.label ?? 'an element', marked.signal)), facts)
}

/**
 * Judge, after a keyboard walk, every element on the page that presents as operable and that neither the
 * walk nor a control standing in for it reached. A walk that did not cross the whole page judges nothing and
 * says so in a note.
 * @param page the page, as the walk left it, with its reach tracked from the walk's start.
 * @param place where findings are made.
 * @param cap the most stops the walk could take.
 * @param carry how a finding on an element is judged against the exemptions above it.
 * @returns the visit carrying the findings.
 */
export const pointerOnlyVisit = async (
  page: Page,
  place: FindingPlace,
  cap: number,
  carry: CarryFinding,
): Promise<Visit> => {
  const end: WalkEnd = await page.evaluate(walkEndInPage, [REACHED_ATTRIBUTE, FIRST_REACHED] as const)
  if (!isWalkComplete({ ...end, cap })) {
    return {
      ...EMPTY_VISIT,
      notes: [
        `${place.location}: the keyboard walk did not cross the whole page, so what only a pointer reaches went ` +
          'unjudged',
      ],
    }
  }
  const query: CandidateQuery = {
    reached: REACHED_ATTRIBUTE,
    candidate: CANDIDATE_ATTRIBUTE,
    roles: INTERACTIVE_ROLES,
    owners: COMPOSITE_OWNERS,
    operable: OPERABLE_SELECTOR,
    unavailable: ':disabled, [inert], [inert] *',
  }
  const own: readonly OwnFacts[] = await page.evaluate(ownFactsInPage, query)
  const standIns: readonly StandInFacts[] = await page.evaluate(standInFactsInPage, query)
  const candidates: readonly PointerCandidate[] = own.flatMap(
    (facts: OwnFacts, index: number): readonly PointerCandidate[] => {
      const standIn: StandInFacts | undefined = standIns[index]
      return standIn === undefined ? [] : [{ ...facts, ...standIn }]
    },
  )
  const pointerOnly: readonly MarkedCandidate[] = candidates
    .flatMap((candidate: PointerCandidate, index: number): readonly MarkedCandidate[] => {
      const signal: string | undefined = pointerOnlySignal(candidate)
      return signal === undefined ? [] : [{ index, signal }]
    })
    .slice(0, MAX_PROBED)
  const visits: readonly Visit[] = await pointerOnly.reduce(
    async (earlier: Promise<readonly Visit[]>, marked: MarkedCandidate): Promise<readonly Visit[]> => [
      ...(await earlier),
      await pointerOnlyAt(page, place, marked, carry),
    ],
    Promise.resolve<readonly Visit[]>([]),
  )
  return merged(visits)
}
