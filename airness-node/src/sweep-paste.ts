// The interaction pass's check that a sign-in field accepts a pasted value (rule `paste-blocked`). Every
// rendered, enabled input is read and marked; `paste-policy` names the ones that take part in signing in; on
// each of those a bubbling, cancelable paste carrying text is dispatched, and the policy judges whether
// anything cancelled it and whether a password field turned autofill off. It runs at the end of a visit,
// after everything else on the page was judged, because a field that accepts the paste may act on it.
import type { Page } from '@playwright/test'
import { type FindingPlace, findingAt } from './browser-findings.js'
import { type TargetFacts, targetFacts } from './layout.js'
import { type AuthenticationField, isAuthenticationField, pasteDefects } from './paste-policy.js'
import type { CarryFinding } from './sweep-pointer-only.js'
import { type DomDocument, EMPTY_VISIT, merged, type Visit } from './sweep-shared.js'

// The browser surface the page functions below touch, declared for the reason `settle.ts` records.
declare const document: DomDocument

/** The attribute naming, for one reading, each rendered and enabled input. */
const FIELD_ATTRIBUTE: string = 'data-airness-field'

/** The text the dispatched paste carries, which no field could mistake for a real secret. */
const PASTED_TEXT: string = 'airness-paste-probe'

/** A sign-in field, with the index it is marked with. */
interface MarkedField {
  readonly field: AuthenticationField
  readonly index: number
}

// Every input a reader could type into, marked with its index, with what the policy reads.
const fieldsInPage = (attribute: string): readonly AuthenticationField[] => {
  const earlier: readonly Element[] = Array.from(document.querySelectorAll(`[${CSS.escape(attribute)}]`))
  for (const marked of earlier) {
    marked.removeAttribute(attribute)
  }
  const fields: readonly HTMLInputElement[] = Array.from(document.querySelectorAll('input')).filter(
    (input: HTMLInputElement): boolean =>
      input.type !== 'hidden' && !input.disabled && input.closest('[inert]') === null && input.checkVisibility(),
  )
  return fields.map((input: HTMLInputElement, index: number): AuthenticationField => {
    input.setAttribute(attribute, String(index))
    return { type: input.type, autocomplete: input.getAttribute('autocomplete') ?? '' }
  })
}

// Whatever cancels the paste - a handler on the field, on an ancestor or on the document, inline or added -
// answers this event by cancelling it, and a synthetic paste inserts nothing of its own.
const isPasteCancelledInPage = ([selector, text]: readonly [string, string]): boolean => {
  const field: Element | null = document.querySelector(selector)
  if (field === null) {
    return false
  }
  const clipboardData: DataTransfer = new DataTransfer()
  clipboardData.setData('text/plain', text)
  const paste: ClipboardEvent = new ClipboardEvent('paste', {
    bubbles: true,
    cancelable: true,
    composed: true,
    clipboardData,
  })
  field.dispatchEvent(paste)
  return paste.defaultPrevented
}

const fieldVisit = async (
  page: Page,
  place: FindingPlace,
  marked: MarkedField,
  carry: CarryFinding,
): Promise<Visit> => {
  const selector: string = `[${FIELD_ATTRIBUTE}="${String(marked.index)}"]`
  const isCancelled: boolean = await page.evaluate(isPasteCancelledInPage, [selector, PASTED_TEXT] as const)
  const defects: readonly string[] = pasteDefects(marked.field, isCancelled)
  if (defects.length === 0) {
    return EMPTY_VISIT
  }
  const facts: TargetFacts | undefined = await targetFacts(page, selector)
  return merged(
    defects.map(
      (defect: string): Visit =>
        carry(findingAt(place, 'paste-blocked', `${facts?.label ?? 'a field'} ${defect}`), facts),
    ),
  )
}

/**
 * Judge every sign-in field on the page: it accepts a pasted value, and a password field leaves autofill on.
 * @param page the page, after everything else on it was judged.
 * @param place where findings are made.
 * @param carry how a finding on an element is judged against the exemptions above it.
 * @returns the visit carrying the findings.
 */
export const pasteVisit = async (page: Page, place: FindingPlace, carry: CarryFinding): Promise<Visit> => {
  const fields: readonly AuthenticationField[] = await page.evaluate(fieldsInPage, FIELD_ATTRIBUTE)
  const signIn: readonly MarkedField[] = fields.flatMap(
    (field: AuthenticationField, index: number): readonly MarkedField[] =>
      isAuthenticationField(field) ? [{ field, index }] : [],
  )
  const visits: readonly Visit[] = await signIn.reduce(
    async (earlier: Promise<readonly Visit[]>, marked: MarkedField): Promise<readonly Visit[]> => [
      ...(await earlier),
      await fieldVisit(page, place, marked, carry),
    ],
    Promise.resolve<readonly Visit[]>([]),
  )
  return merged(visits)
}
