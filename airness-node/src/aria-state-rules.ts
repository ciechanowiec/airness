// The rules that judge whether the ARIA states a page declares at rest agree with what it renders, and
// whether its navigation marks the page a reader is on. axe checks that each state is valid on its element;
// these check that the state is true. Each reports only a definite contradiction.
//
// Deliberately not judged: `aria-expanded="false"` with its controlled element rendered, because a collapsed
// region may legitimately keep a preview in view; an `aria-controls` naming an id the document lacks, which
// axe judges; a multi-selectable tab list; elements a role owns through `aria-owns`; and `aria-pressed`,
// `aria-checked` and `aria-selected` outside a tab list, whose visual counterpart no measurement here reads.

/** An element an `aria-controls` names, as the page rendered it. Ids the document lacks are left out. */
export interface ControlledElement {
  readonly id: string
  /** Whether the browser renders it: not `display: none`, `hidden`, `visibility: hidden` or in a closed disclosure. */
  readonly isRendered: boolean
}

/** A rendered element that declares `aria-expanded="true"` and an `aria-controls`. */
export interface ExpandedControl {
  /** The index the sweep marked the element with, which a defect names it by. */
  readonly mark: number
  readonly controlled: readonly ControlledElement[]
}

/** One `tab` of a rendered `tablist`. */
export interface TabState {
  readonly mark: number
  readonly isSelected: boolean
  /** The panels its `aria-controls` names. */
  readonly panels: readonly ControlledElement[]
}

/** A rendered `tablist` and the tabs it holds. */
export interface TablistState {
  readonly mark: number
  readonly isMultiselectable: boolean
  readonly tabs: readonly TabState[]
}

/** What the page declares at rest, as the sweep read it (rule `aria-state`). */
export interface AriaStateProbe {
  readonly expanded: readonly ExpandedControl[]
  readonly tablists: readonly TablistState[]
}

/** A defect on one marked element. */
export interface AriaDefect {
  readonly mark: number
  readonly message: string
}

const expandedDefects = (control: ExpandedControl): readonly AriaDefect[] =>
  control.controlled
    .filter((element: ControlledElement): boolean => !element.isRendered)
    .map(
      (element: ControlledElement): AriaDefect => ({
        mark: control.mark,
        message: `aria-expanded is "true", but #${element.id}, which its aria-controls names, is not rendered`,
      }),
    )

const selectionDefects = (tablist: TablistState): readonly AriaDefect[] => {
  const selected: number = tablist.tabs.filter((tab: TabState): boolean => tab.isSelected).length
  if (selected === 1) {
    return []
  }
  return [
    {
      mark: tablist.mark,
      message:
        selected === 0
          ? 'no tab of this tab list carries aria-selected="true"'
          : `${String(selected)} tabs of this single-select tab list carry aria-selected="true"`,
    },
  ]
}

// A panel several tabs name, as one panel whose content the selected tab swaps in, belongs to the selected
// tab: it is rendered for it, not for the others.
const panelDefects = (tablist: TablistState): readonly AriaDefect[] => {
  const selectedPanels: ReadonlySet<string> = new Set<string>(
    tablist.tabs
      .filter((tab: TabState): boolean => tab.isSelected)
      .flatMap((tab: TabState): readonly string[] => tab.panels.map((panel: ControlledElement): string => panel.id)),
  )
  return tablist.tabs.flatMap((tab: TabState): readonly AriaDefect[] =>
    tab.panels.flatMap((panel: ControlledElement): readonly AriaDefect[] => {
      if (tab.isSelected && !panel.isRendered) {
        return [{ mark: tab.mark, message: `the tab is selected, but its panel #${panel.id} is not rendered` }]
      }
      return !tab.isSelected && panel.isRendered && !selectedPanels.has(panel.id)
        ? [{ mark: tab.mark, message: `the tab is not selected, but its panel #${panel.id} is rendered` }]
        : []
    }),
  )
}

const tablistDefects = (tablist: TablistState): readonly AriaDefect[] =>
  tablist.isMultiselectable || tablist.tabs.length === 0 ? [] : [...selectionDefects(tablist), ...panelDefects(tablist)]

/**
 * Where the states a page declares contradict what it renders (rule `aria-state`).
 *
 * An expanded control's controlled elements are rendered. A single-select tab list has exactly one selected
 * tab, the panel that tab controls is rendered, and the panels of the other tabs are not.
 * @param probe what the page declares and renders.
 * @returns one defect per contradiction.
 */
export const ariaStateDefects = (probe: AriaStateProbe): readonly AriaDefect[] => [
  ...probe.expanded.flatMap((control: ExpandedControl): readonly AriaDefect[] => expandedDefects(control)),
  ...probe.tablists.flatMap((tablist: TablistState): readonly AriaDefect[] => tablistDefects(tablist)),
]

/** A rendered link inside a navigation landmark. */
export interface NavLink {
  readonly mark: number
  /** Its `href` attribute as written. */
  readonly rawHref: string
  /** Its `href` resolved against the document, as the browser follows it. */
  readonly href: string
  /** Its `aria-current` attribute, or null when it has none. */
  readonly current: string | null
}

/** What the sweep read of a page's navigation (rule `aria-current`). */
export interface NavigationProbe {
  /** The page's own URL. */
  readonly url: string
  /** The page's language and the languages its `hreflang` alternates name, lower case. */
  readonly locales: readonly string[]
  readonly links: readonly NavLink[]
}

/** How a link relates to the page it is on. */
type LinkRelation = 'current' | 'elsewhere' | 'undecided'

const WEB_PROTOCOLS: ReadonlySet<string> = new Set<string>(['http:', 'https:'])

const parsed = (href: string): URL | undefined => (URL.canParse(href) ? new URL(href) : undefined)

// The root keeps its one slash; every other path loses its trailing ones.
const withoutTrailingSlash = (path: string): string =>
  path.length > 1 && path.endsWith('/') ? withoutTrailingSlash(path.slice(0, -1)) : path || '/'

const pathOf = (url: URL): string => withoutTrailingSlash(url.pathname)

const primaryTag = (tag: string): string => tag.toLowerCase().split('-', 1)[0] ?? ''

// A segment is a locale prefix when its primary language subtag is one of the page's: `en-GB` for `en`.
const isLocale = (segment: string, locales: readonly string[]): boolean =>
  segment !== '' && locales.some((locale: string): boolean => primaryTag(locale) === primaryTag(segment))

// The path without its first segment when that segment is one of the page's languages, as a locale prefix is.
const unprefixed = (path: string, locales: readonly string[]): string => {
  const [, first = '', ...rest]: readonly string[] = path.split('/')
  return isLocale(first, locales) ? withoutTrailingSlash(`/${rest.join('/')}`) : path
}

const isSameUnderLocales = (left: string, right: string, locales: readonly string[]): boolean =>
  unprefixed(left, locales) === unprefixed(right, locales)

const relationOf = (page: URL, target: URL, locales: readonly string[]): LinkRelation => {
  if (!WEB_PROTOCOLS.has(target.protocol) || target.origin !== page.origin) {
    return 'elsewhere'
  }
  const pagePath: string = pathOf(page)
  const targetPath: string = pathOf(target)
  if (pagePath !== targetPath) {
    return isSameUnderLocales(pagePath, targetPath, locales) ? 'undecided' : 'elsewhere'
  }
  return target.search === page.search && target.hash === '' ? 'current' : 'undecided'
}

/**
 * How a link relates to the page it is on.
 *
 * A link is to the current page when it resolves to the page's own origin, path and query, the fragment and a
 * trailing slash aside. It is elsewhere when it leaves the web, the origin or the path. Anything between is
 * undecided and judged neither way: a link written as a fragment, or with one to a section of this page, is an
 * in-page link; a link to the same path with another query may be the same page filtered; and a path that
 * differs only by one of the page's language prefixes may be the same page in another locale, or this one
 * before a redirect adds its prefix.
 * @param pageUrl the page's URL.
 * @param link the link's written and resolved `href`.
 * @param link.rawHref the `href` as written.
 * @param link.href the `href` resolved against the document.
 * @param locales the page's language and its alternates' languages.
 * @returns the relation.
 */
const linkRelation = (
  pageUrl: string,
  link: Pick<NavLink, 'rawHref' | 'href'>,
  locales: readonly string[],
): LinkRelation => {
  const page: URL | undefined = parsed(pageUrl)
  const target: URL | undefined = parsed(link.href)
  const written: string = link.rawHref.trim()
  if (page === undefined || target === undefined || written === '' || written.startsWith('#')) {
    return 'undecided'
  }
  return relationOf(page, target, locales)
}

const currentDefect = (link: NavLink, relation: LinkRelation): string | undefined => {
  if (relation === 'current' && link.current !== 'page') {
    return link.current === null || link.current === 'false'
      ? 'it links to the current page without aria-current="page"'
      : `it links to the current page with aria-current="${link.current}" instead of "page"`
  }
  return relation === 'elsewhere' && link.current === 'page'
    ? `it carries aria-current="page" but links to ${link.href}, not the current page`
    : undefined
}

/**
 * Where a page's navigation misstates which page is current (rule `aria-current`, WCAG 2.4.8 Location).
 *
 * Within a navigation landmark, a link to the current page carries `aria-current="page"`, and no link to
 * another page does. A navigation with no link to the current page states nothing and is not judged.
 * @param probe the page's URL, languages and navigation links.
 * @returns one defect per misstating link.
 */
export const currentLinkDefects = (probe: NavigationProbe): readonly AriaDefect[] =>
  probe.links.flatMap((link: NavLink): readonly AriaDefect[] => {
    const message: string | undefined = currentDefect(link, linkRelation(probe.url, link, probe.locales))
    return message === undefined ? [] : [{ mark: link.mark, message }]
  })
