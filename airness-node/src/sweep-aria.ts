// The resting ARIA rules: whether the states a page declares agree with what it renders (rule
// `aria-state`), and whether its navigation marks the page a reader is on (rule `aria-current`). Both read
// the DOM once and drive nothing, so they run beside axe wherever a pass already scans a page at rest or
// with a disclosure open.
import type { Page } from '@playwright/test'
import {
  type AriaDefect,
  type AriaStateProbe,
  ariaStateDefects,
  type ControlledElement,
  currentLinkDefects,
  type NavigationProbe,
  type NavLink,
  type TablistState,
  type TabState,
} from './aria-state-rules.js'
import { type FindingPlace, findingAt } from './browser-findings.js'
import { type TargetFacts, targetFacts } from './layout.js'
import { carried } from './sweep-interaction.js'
import { type DomDocument, type DomElement, merged, type Visit } from './sweep-shared.js'

// The browser surface the page functions below touch, declared for the reason `settle.ts` records.
declare const document: DomDocument
declare const location: { readonly href: string }

const STATE_ATTRIBUTE: string = 'data-airness-aria-state'
const CURRENT_ATTRIBUTE: string = 'data-airness-aria-current'

/** The rules this module applies. */
export const ARIA_RESTING_RULES: readonly string[] = ['aria-state', 'aria-current']

// Marks every element a state defect could name, then reads the expanded controls and the tab lists.
// Rendering is read as the browser paints: `display: none`, `hidden`, `visibility: hidden` and the closed
// branch of a `<details>` are not rendered, a transparent element still is.
const readStateInPage = (attribute: string): AriaStateProbe => {
  for (const element of document.querySelectorAll(`[${CSS.escape(attribute)}]`)) {
    element.removeAttribute(attribute)
  }
  const isRendered = (element: DomElement): boolean => element.checkVisibility({ visibilityProperty: true })
  const controlledOf = (element: DomElement): readonly ControlledElement[] =>
    (element.getAttribute('aria-controls') ?? '').split(/\s+/u).flatMap((id: string): readonly ControlledElement[] => {
      const target: DomElement | null = id === '' ? null : document.querySelector(`#${CSS.escape(id)}`)
      return target === null ? [] : [{ id, isRendered: isRendered(target) }]
    })
  const expanded: readonly DomElement[] = Array.from(
    document.querySelectorAll('[aria-expanded="true"][aria-controls]'),
  ).filter((element: DomElement): boolean => isRendered(element))
  const tablists: readonly DomElement[] = Array.from(document.querySelectorAll('[role="tablist"]')).filter(
    (element: DomElement): boolean => isRendered(element),
  )
  const tabsOf = (tablist: DomElement): readonly DomElement[] =>
    Array.from(tablist.querySelectorAll('[role="tab"]')).filter(
      (tab: DomElement): boolean => tab.closest('[role="tablist"]') === tablist,
    )
  const subjects: readonly DomElement[] = [
    ...expanded,
    ...tablists,
    ...tablists.flatMap((tablist: DomElement): readonly DomElement[] => tabsOf(tablist)),
  ]
  for (const [index, element] of subjects.entries()) {
    if (subjects.indexOf(element) === index) {
      element.setAttribute(attribute, String(index))
    }
  }
  const markOf = (element: DomElement): number => subjects.indexOf(element)
  return {
    expanded: expanded.map((element: DomElement) => ({ mark: markOf(element), controlled: controlledOf(element) })),
    tablists: tablists.map(
      (tablist: DomElement): TablistState => ({
        mark: markOf(tablist),
        isMultiselectable: tablist.getAttribute('aria-multiselectable') === 'true',
        tabs: tabsOf(tablist).map(
          (tab: DomElement): TabState => ({
            mark: markOf(tab),
            isSelected: tab.getAttribute('aria-selected') === 'true',
            panels: controlledOf(tab),
          }),
        ),
      }),
    ),
  }
}

// Marks and reads every rendered link inside a navigation landmark, with the page's URL and languages.
const readNavigationInPage = (attribute: string): NavigationProbe => {
  for (const element of document.querySelectorAll(`[${CSS.escape(attribute)}]`)) {
    element.removeAttribute(attribute)
  }
  const links: readonly HTMLAnchorElement[] = Array.from(
    document.querySelectorAll<HTMLAnchorElement>('nav a[href], [role="navigation"] a[href]'),
  ).filter((link: HTMLAnchorElement): boolean => link.checkVisibility({ visibilityProperty: true }))
  for (const [index, link] of links.entries()) {
    link.setAttribute(attribute, String(index))
  }
  const alternates: readonly string[] = Array.from(
    document.querySelectorAll('link[rel~="alternate"][hreflang]'),
    (link: DomElement): string => link.getAttribute('hreflang') ?? '',
  )
  return {
    url: location.href,
    locales: [document.documentElement.lang, ...alternates]
      .map((locale: string): string => locale.toLowerCase())
      .filter((locale: string): boolean => locale !== '' && locale !== 'x-default'),
    links: links.map(
      (link: HTMLAnchorElement, index: number): NavLink => ({
        mark: index,
        rawHref: link.getAttribute('href') ?? '',
        href: link.href,
        current: link.getAttribute('aria-current'),
      }),
    ),
  }
}

/** A defect and the rule and attribute it was found by. */
interface RuledDefect {
  readonly rule: string
  readonly attribute: string
  readonly defect: AriaDefect
}

const defectVisit = async (page: Page, place: FindingPlace, found: RuledDefect): Promise<Visit> => {
  const facts: TargetFacts | undefined = await targetFacts(page, `[${found.attribute}="${String(found.defect.mark)}"]`)
  return carried(findingAt(place, found.rule, `${facts?.label ?? 'an element'}: ${found.defect.message}`), facts)
}

/**
 * Judge the page as it stands by the resting ARIA rules.
 *
 * A story's navigation is not judged by `aria-current`: a story renders its component on Storybook's own
 * preview page, whose URL no link in it is meant to name.
 * @param page the page, settled.
 * @param place where its findings go.
 * @param isStory whether the page is a story.
 * @returns the findings, each judged against the exemptions above its element.
 */
export const ariaVisit = async (page: Page, place: FindingPlace, isStory: boolean): Promise<Visit> => {
  const state: AriaStateProbe = await page.evaluate(readStateInPage, STATE_ATTRIBUTE)
  const navigation: readonly AriaDefect[] = isStory
    ? []
    : currentLinkDefects(await page.evaluate(readNavigationInPage, CURRENT_ATTRIBUTE))
  const found: readonly RuledDefect[] = [
    ...ariaStateDefects(state).map(
      (defect: AriaDefect): RuledDefect => ({ rule: 'aria-state', attribute: STATE_ATTRIBUTE, defect }),
    ),
    ...navigation.map(
      (defect: AriaDefect): RuledDefect => ({ rule: 'aria-current', attribute: CURRENT_ATTRIBUTE, defect }),
    ),
  ]
  return merged(
    await Promise.all(found.map(async (defect: RuledDefect): Promise<Visit> => await defectVisit(page, place, defect))),
  )
}
