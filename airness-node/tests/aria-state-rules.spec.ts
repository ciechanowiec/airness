import { describe, expect, it } from 'vitest'
import {
  type AriaStateProbe,
  ariaStateDefects,
  currentLinkDefects,
  type NavigationProbe,
  type NavLink,
  type TablistState,
  type TabState,
} from '@/aria-state-rules'

const NOTHING: AriaStateProbe = { expanded: [], tablists: [] }

describe('ariaStateDefects: expanded controls', () => {
  it('accepts an expanded control whose controlled elements are rendered', () => {
    expect(
      ariaStateDefects({
        ...NOTHING,
        expanded: [
          {
            mark: 0,
            controlled: [
              { id: 'menu', isRendered: true },
              { id: 'backdrop', isRendered: true },
            ],
          },
        ],
      }),
    ).toEqual([])
  })

  it('reports each controlled element an expanded control leaves unrendered', () => {
    expect(
      ariaStateDefects({
        ...NOTHING,
        expanded: [
          {
            mark: 3,
            controlled: [
              { id: 'menu', isRendered: false },
              { id: 'shown', isRendered: true },
              { id: 'backdrop', isRendered: false },
            ],
          },
        ],
      }),
    ).toEqual([
      { mark: 3, message: 'aria-expanded is "true", but #menu, which its aria-controls names, is not rendered' },
      { mark: 3, message: 'aria-expanded is "true", but #backdrop, which its aria-controls names, is not rendered' },
    ])
  })

  it('judges nothing an expanded control names that the document lacks, which arrives as no element', () => {
    expect(ariaStateDefects({ ...NOTHING, expanded: [{ mark: 0, controlled: [] }] })).toEqual([])
  })
})

const tab = (mark: number, isSelected: boolean, panel: string, isRendered: boolean): TabState => ({
  mark,
  isSelected,
  panels: [{ id: panel, isRendered }],
})

const tabs = (entries: readonly TabState[], overrides: Partial<TablistState> = {}): AriaStateProbe => ({
  ...NOTHING,
  tablists: [{ mark: 0, isMultiselectable: false, tabs: entries, ...overrides }],
})

describe('ariaStateDefects: tab lists', () => {
  it('accepts one selected tab whose panel alone is rendered', () => {
    expect(ariaStateDefects(tabs([tab(1, true, 'one', true), tab(2, false, 'two', false)]))).toEqual([])
  })

  it('reports a tab list with no selected tab, and one with several', () => {
    expect([
      ariaStateDefects(tabs([tab(1, false, 'one', false), tab(2, false, 'two', false)])),
      ariaStateDefects(tabs([tab(1, true, 'one', true), tab(2, true, 'two', true)])),
    ]).toEqual([
      [{ mark: 0, message: 'no tab of this tab list carries aria-selected="true"' }],
      [{ mark: 0, message: '2 tabs of this single-select tab list carry aria-selected="true"' }],
    ])
  })

  it('reports the selected tab whose panel is hidden and an unselected tab whose panel shows', () => {
    expect(ariaStateDefects(tabs([tab(1, true, 'one', false), tab(2, false, 'two', true)]))).toEqual([
      { mark: 1, message: 'the tab is selected, but its panel #one is not rendered' },
      { mark: 2, message: 'the tab is not selected, but its panel #two is rendered' },
    ])
  })

  it('accepts one panel every tab names, rendered for the selected one', () => {
    expect(ariaStateDefects(tabs([tab(1, false, 'panel', true), tab(2, true, 'panel', true)]))).toEqual([])
  })

  it('accepts tabs with no panel in the document, as a panel mounted only when selected leaves them', () => {
    expect(
      ariaStateDefects(
        tabs([
          { mark: 1, isSelected: true, panels: [] },
          { mark: 2, isSelected: false, panels: [] },
        ]),
      ),
    ).toEqual([])
  })

  it('judges neither a multi-selectable tab list nor an empty one', () => {
    expect([
      ariaStateDefects(
        tabs([tab(1, true, 'one', true), tab(2, true, 'two', true)], {
          isMultiselectable: true,
        }),
      ),
      ariaStateDefects(tabs([])),
    ]).toEqual([[], []])
  })
})

const PAGE: string = 'https://example.test/en/about'

// How the rule reads a link's relation to the page, observed through what it reports: a link to the current
// page is reported without aria-current, a link elsewhere is reported with it, and an undecided one never is.
const relation = (href: string, rawHref: string = href, url: string = PAGE): string => {
  const isReported = (current: string | null): boolean =>
    currentLinkDefects({ url, locales: ['en', 'pl'], links: [{ mark: 0, rawHref, href, current }] }).length > 0
  if (isReported(null)) {
    return 'current'
  }
  return isReported('page') ? 'elsewhere' : 'undecided'
}

describe('the relation of a navigation link to the page', () => {
  it('calls a link to the same path and query current, a trailing slash aside', () => {
    expect([
      relation('https://example.test/en/about', '/en/about'),
      relation('https://example.test/en/about/', '/en/about/'),
      relation('https://example.test/en/about', 'about', 'https://example.test/en/about/'),
      relation('https://example.test/', '/', 'https://example.test/'),
      relation('https://example.test/list?page=2', '?page=2', 'https://example.test/list?page=2'),
    ]).toEqual(['current', 'current', 'current', 'current', 'current'])
  })

  it('leaves in-page links undecided, written as a fragment or with one', () => {
    expect([
      relation('https://example.test/en/about#team', '#team'),
      relation('https://example.test/en/about#team', '/en/about#team'),
      relation('https://example.test/en/about', ''),
    ]).toEqual(['undecided', 'undecided', 'undecided'])
  })

  it('leaves the same path with another query undecided', () => {
    expect(relation('https://example.test/list?page=1', '/list?page=1', 'https://example.test/list')).toBe('undecided')
  })

  it('leaves a path that differs only by one of the page languages undecided', () => {
    expect([
      relation('https://example.test/about', '/about'),
      relation('https://example.test/pl/about', '/pl/about'),
      relation('https://example.test/en', '/en', 'https://example.test/'),
      relation('https://example.test/en-GB/about', '/en-GB/about'),
    ]).toEqual(['undecided', 'undecided', 'undecided', 'undecided'])
  })

  it('calls another path, another origin and another scheme elsewhere', () => {
    expect([
      relation('https://example.test/en/contact', '/en/contact'),
      relation('https://example.test/', '/'),
      relation('https://other.test/en/about', 'https://other.test/en/about'),
      relation('mailto:team@example.test', 'mailto:team@example.test'),
      relation('https://example.test/fr/about', '/fr/about'),
    ]).toEqual(['elsewhere', 'elsewhere', 'elsewhere', 'elsewhere', 'elsewhere'])
  })

  it('leaves an unparsable page or link undecided', () => {
    expect([relation('not a url', 'not a url'), relation('https://example.test/', '/', 'not a url')]).toEqual([
      'undecided',
      'undecided',
    ])
  })
})

const link = (mark: number, rawHref: string, current: string | null): NavLink => ({
  mark,
  rawHref,
  href: new URL(rawHref, PAGE).href,
  current,
})

const navigation = (links: readonly NavLink[]): NavigationProbe => ({ url: PAGE, locales: ['en'], links })

describe('currentLinkDefects', () => {
  it('accepts a navigation that marks the current page and nothing else', () => {
    expect(
      currentLinkDefects(
        navigation([link(0, '/en', null), link(1, '/en/about', 'page'), link(2, '/en/contact', 'false')]),
      ),
    ).toEqual([])
  })

  it('accepts a navigation with no link to the current page', () => {
    expect(currentLinkDefects(navigation([link(0, '/en/contact', null), link(1, '/en/blog', null)]))).toEqual([])
  })

  it('reports a link to the current page without aria-current, with it false, or with another value', () => {
    expect(
      currentLinkDefects(
        navigation([link(0, '/en/about', null), link(1, '/en/about/', 'false'), link(2, '/en/about', 'true')]),
      ),
    ).toEqual([
      { mark: 0, message: 'it links to the current page without aria-current="page"' },
      { mark: 1, message: 'it links to the current page without aria-current="page"' },
      { mark: 2, message: 'it links to the current page with aria-current="true" instead of "page"' },
    ])
  })

  it('reports aria-current="page" on a link to another page, and judges no other value there', () => {
    expect(currentLinkDefects(navigation([link(0, '/en/contact', 'page'), link(1, '/en/blog', 'true')]))).toEqual([
      {
        mark: 0,
        message: 'it carries aria-current="page" but links to https://example.test/en/contact, not the current page',
      },
    ])
  })

  it('judges no undecided link either way', () => {
    expect(
      currentLinkDefects(navigation([link(0, '#main', 'page'), link(1, '/about', null), link(2, '/about', 'page')])),
    ).toEqual([])
  })
})
