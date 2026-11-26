// The browser half of rule `class-silenced`: it reads the page's stylesheets through the CSSOM into an index of
// the rules that name a class of a classed element, and takes each candidate class off its element for as long
// as one computed-style read takes. The verdict is `class-silenced.ts`'s.
//
// WHY THE TOGGLE LEAVES NO TRACE. Each class is removed and its `class` attribute written back verbatim inside
// one task, so no frame is drawn between the two and nothing the page schedules runs in between. Transitions
// are stilled for every resting measurement, so a style read cannot start one, and the element is found again
// only when its `class` attribute is still the one the index read.
//
// WHY THE PAGE CODE IS ASSEMBLED FROM SOURCE TEXT. Playwright ships a page function to the browser as its own
// source, so each step below reaches the others only through the `tools` it is handed, as in `layout.ts`.
import type { Page } from '@playwright/test'
import { type FindingPlace, findingAt } from './browser-findings.js'
import {
  CLASS_SILENCED_RULE,
  type ClassedElement,
  confirmedSilencing,
  type PairToggle,
  type SilencingPair,
  type StyleDeclaration,
  type StyleEntry,
  type StyleIndex,
  silencedMessage,
  silencingCandidates,
  unreadableNotes,
} from './class-silenced.js'
import { type TargetFacts, targetFacts } from './layout.js'
import { carried } from './sweep-interaction.js'
import { EMPTY_VISIT, merged, type Visit } from './sweep-shared.js'

const MARK_ATTRIBUTE: string = 'data-airness-class-silenced'

/** Where a rule stands: the selectors of the style rules around it, and the at-rules it is inside. */
interface Scope {
  readonly selectors: readonly string[] | undefined
  readonly conditions: readonly string[]
}

/** An entry before its place in the cascade is counted. */
type FlatEntry = Omit<StyleEntry, 'order'>

/** The steps of the page code, each handed the others. */
interface IndexTools {
  readonly classedElements: () => readonly Element[]
  readonly rulesOf: (sheet: CSSStyleSheet) => readonly CSSRule[] | undefined
  readonly preludeOf: (rule: CSSRule) => string
  readonly declarationsOf: (style: CSSStyleDeclaration) => readonly StyleDeclaration[]
  readonly appendedTo: (parts: readonly string[], character: string, isComma: boolean) => readonly string[]
  readonly selectorsOf: (
    selectorText: string,
    parents: readonly string[] | undefined,
    tools: IndexTools,
  ) => readonly string[]
  readonly entriesOfRule: (rule: CSSRule, scope: Scope, tools: IndexTools) => readonly FlatEntry[]
  readonly entriesOfAtRule: (rule: CSSRule, scope: Scope, tools: IndexTools) => readonly FlatEntry[]
  readonly entriesOf: (rules: readonly CSSRule[], scope: Scope, tools: IndexTools) => readonly FlatEntry[]
  readonly unreadableOf: (sheets: readonly CSSStyleSheet[], tools: IndexTools) => readonly string[]
  readonly canApply: (element: Element, selector: string) => boolean
  readonly classTokensOf: (selector: string) => readonly string[]
  readonly styleOf: (element: Element, pair: SilencingPair) => string
  readonly doesMatter: (element: Element, className: string, pair: SilencingPair, tools: IndexTools) => boolean
}

// Every element carrying two classes or more, in document order, inside a stack layer's marked view when the
// page has one. The attribute is spelt out because this runs in the page: `browser-scope.ts` sets it.
const classedElements = (): readonly Element[] => {
  const views: readonly Element[] = Array.from(document.querySelectorAll('[data-airness-view]'))
  return [document.body, ...Array.from(document.body.querySelectorAll('[class]'))].filter(
    (element: Element): boolean =>
      element.classList.length > 1 && (views.length === 0 || views.some((view: Element) => view.contains(element))),
  )
}

// A stylesheet another origin serves without CORS refuses its rules to script.
const rulesOf = (sheet: CSSStyleSheet): readonly CSSRule[] | undefined => {
  try {
    return Array.from(sheet.cssRules)
  } catch {
    return undefined
  }
}

const preludeOf = (rule: CSSRule): string => rule.cssText.slice(0, Math.max(0, rule.cssText.indexOf('{'))).trim()

// The declarations as the stylesheet serializes them, so a shorthand keeps its own name rather than the longhands
// a declaration block lists: `padding` and `padding-inline` stay two properties. A piece of a value cut at a
// semicolon inside a string, as in a data URL, starts with no property name and is passed over; the block
// itself says whether a declaration is important, under the name it was written as.
const declarationsOf = (style: CSSStyleDeclaration): readonly StyleDeclaration[] =>
  style.cssText.split(';').flatMap((piece: string): readonly StyleDeclaration[] => {
    const property: string | undefined = /^\s*(--[\w-]+|-?[a-z][a-z-]*)\s*:/u.exec(piece)?.[1]
    return property === undefined
      ? []
      : [{ property, isImportant: style.getPropertyPriority(property) === 'important' }]
  })

// A selector list split at its top-level commas, with a nested rule's `&` replaced by the rule around it.
// A selector list's parts, the character added to the last, or a new part begun at a top-level comma.
const appendedTo = (parts: readonly string[], character: string, isComma: boolean): readonly string[] =>
  isComma ? [...parts, ''] : [...parts.slice(0, -1), `${parts.at(-1) ?? ''}${character}`]

const selectorsOf = (
  selectorText: string,
  parents: readonly string[] | undefined,
  tools: IndexTools,
): readonly string[] => {
  interface Split {
    readonly depth: number
    readonly previous: string
    readonly parts: readonly string[]
  }
  const depthChange: Readonly<Record<string, number>> = { '(': 1, '[': 1, ')': -1, ']': -1 }
  const split: Split = Array.from(selectorText).reduce(
    (state: Split, character: string): Split => {
      const isLiteral: boolean = state.previous === '\\'
      const depth: number = state.depth + (isLiteral ? 0 : (depthChange[character] ?? 0))
      return {
        depth,
        previous: isLiteral ? '' : character,
        parts: tools.appendedTo(state.parts, character, !isLiteral && depth === 0 && character === ','),
      }
    },
    { depth: 0, previous: '', parts: [''] },
  )
  const own: readonly string[] = split.parts.map((part: string) => part.trim()).filter((part: string) => part !== '')
  if (parents === undefined) {
    return own
  }
  const parent: string = parents.length === 1 ? (parents[0] ?? '') : `:is(${parents.join(', ')})`
  return own.map((selector: string) =>
    selector.includes('&') ? selector.split('&').join(parent) : `${parent} ${selector}`,
  )
}

const entriesOfRule = (rule: CSSRule, scope: Scope, tools: IndexTools): readonly FlatEntry[] => {
  if (!(rule instanceof CSSStyleRule)) {
    return tools.entriesOfAtRule(rule, scope, tools)
  }
  const selectors: readonly string[] = tools.selectorsOf(rule.selectorText, scope.selectors, tools)
  const declarations: readonly StyleDeclaration[] = tools.declarationsOf(rule.style)
  return [
    ...selectors.map((selector: string): FlatEntry => ({ selector, conditions: scope.conditions, declarations })),
    ...tools.entriesOf(Array.from(rule.cssRules), { ...scope, selectors }, tools),
  ]
}

// An imported stylesheet and a grouping at-rule add their prelude to the conditions of what they hold.
// Declarations written directly inside an at-rule nested in a style rule apply to that style rule.
const entriesOfAtRule = (rule: CSSRule, scope: Scope, tools: IndexTools): readonly FlatEntry[] => {
  const inner: Scope = { ...scope, conditions: [...scope.conditions, tools.preludeOf(rule)] }
  if (rule instanceof CSSImportRule) {
    const sheet: CSSStyleSheet | null = rule.styleSheet
    return sheet === null ? [] : tools.entriesOf(tools.rulesOf(sheet) ?? [], inner, tools)
  }
  if (rule instanceof CSSGroupingRule) {
    return tools.entriesOf(Array.from(rule.cssRules), inner, tools)
  }
  const style: unknown = 'style' in rule ? rule.style : undefined
  return scope.selectors !== undefined && style instanceof CSSStyleDeclaration
    ? scope.selectors.map(
        (selector: string): FlatEntry => ({
          selector,
          conditions: scope.conditions,
          declarations: tools.declarationsOf(style),
        }),
      )
    : []
}

const entriesOf = (rules: readonly CSSRule[], scope: Scope, tools: IndexTools): readonly FlatEntry[] =>
  rules.flatMap((rule: CSSRule): readonly FlatEntry[] => tools.entriesOfRule(rule, scope, tools))

const unreadableOf = (sheets: readonly CSSStyleSheet[], tools: IndexTools): readonly string[] =>
  sheets.flatMap((sheet: CSSStyleSheet): readonly string[] => {
    const rules: readonly CSSRule[] | undefined = tools.rulesOf(sheet)
    if (rules === undefined) {
      return [sheet.href ?? 'an inline stylesheet']
    }
    const imported: readonly CSSStyleSheet[] = rules.flatMap((rule: CSSRule): readonly CSSStyleSheet[] =>
      rule instanceof CSSImportRule && rule.styleSheet !== null ? [rule.styleSheet] : [],
    )
    return tools.unreadableOf(imported, tools)
  })

// Whether a rule may style the element in some state: its selector matches as it stands, or with every pseudo
// part removed, which can only widen the match. A selector the browser cannot read is taken to apply, so a class
// some rule might style as a component is never judged a utility.
const canApply = (element: Element, selector: string): boolean => {
  const widened = (text: string): string => {
    const inner: string = text.replaceAll(/(?<!\\)::?[a-z][\w-]*\([^()]*\)/giu, '')
    return inner === text ? text.replaceAll(/(?<!\\)::?[a-z][\w-]*/giu, '') : widened(inner)
  }
  try {
    return element.matches(selector) || element.matches(widened(selector).trim() || '*')
  } catch {
    return true
  }
}

// The class identifiers a selector names, as the CSSOM serializes them, which is how `CSS.escape` writes them.
const classTokensOf = (selector: string): readonly string[] =>
  Array.from(
    selector.matchAll(/\.((?:\\[\da-f]{1,6}\s?|\\[^\n\da-f]|[\w\-\P{ASCII}])+)/giu),
    (match: RegExpExecArray): string => match[1] ?? '',
  )

// The element's whole computed style, or the pseudo-element's both rules style, with the custom properties its
// rules declare, which not every engine lists among the properties of a computed style.
const styleOf = (element: Element, pair: SilencingPair): string => {
  const style: CSSStyleDeclaration = getComputedStyle(element, pair.pseudoElement === '' ? null : pair.pseudoElement)
  return [
    ...Array.from(style, (name: string): string => `${name}:${style.getPropertyValue(name)}`),
    ...pair.customProperties.map((name: string): string => `${name}:${style.getPropertyValue(name)}`),
  ].join(';')
}

const doesMatter = (element: Element, className: string, pair: SilencingPair, tools: IndexTools): boolean => {
  const original: string = element.getAttribute('class') ?? ''
  const before: string = tools.styleOf(element, pair)
  element.classList.remove(className)
  try {
    return tools.styleOf(element, pair) !== before
  } finally {
    element.setAttribute('class', original)
  }
}

const indexDocument = (tools: IndexTools): StyleIndex => {
  const sheets: readonly CSSStyleSheet[] = Array.from(document.styleSheets)
  const flat: readonly FlatEntry[] = sheets
    .flatMap((sheet: CSSStyleSheet): readonly FlatEntry[] => {
      const media: string = sheet.media.mediaText
      const conditions: readonly string[] = media === '' ? [] : [`@media ${media}`]
      return tools.entriesOf(tools.rulesOf(sheet) ?? [], { selectors: undefined, conditions }, tools)
    })
    .filter((entry: FlatEntry): boolean => entry.declarations.length > 0)
  const tokens: readonly (readonly string[])[] = flat.map((entry: FlatEntry) => tools.classTokensOf(entry.selector))
  const described: readonly ClassedElement[] = tools
    .classedElements()
    .map((element: Element, ordinal: number): ClassedElement => {
      const escaped: ReadonlySet<string> = new Set(
        Array.from(element.classList, (name: string): string => CSS.escape(name)),
      )
      return {
        ordinal,
        classAttribute: element.getAttribute('class') ?? '',
        classes: Array.from(element.classList),
        entries: tokens.flatMap((named: readonly string[], at: number): readonly number[] =>
          named.some((token: string) => escaped.has(token)) && tools.canApply(element, flat[at]?.selector ?? '')
            ? [at]
            : [],
        ),
      }
    })
  // Only an element two of whose classes some rule names can hold a pair.
  const elements: readonly ClassedElement[] = described.filter(
    (element: ClassedElement): boolean =>
      element.classes.filter((name: string): boolean =>
        element.entries.some((at: number): boolean => (tokens[at] ?? []).includes(CSS.escape(name))),
      ).length > 1,
  )
  const referenced: readonly number[] = [
    ...new Set(elements.flatMap((element: ClassedElement) => element.entries)),
  ].sort((first: number, second: number) => first - second)
  return {
    entries: referenced.flatMap((at: number): readonly StyleEntry[] => {
      const entry: FlatEntry | undefined = flat[at]
      return entry === undefined ? [] : [{ ...entry, order: at }]
    }),
    elements: elements.map((element: ClassedElement) => ({
      ...element,
      entries: element.entries.map((at: number) => referenced.indexOf(at)),
    })),
    unreadable: tools.unreadableOf(sheets, tools),
  }
}

/** What the toggle is told: the candidates and the attribute that marks each element for its finding. */
interface ToggleRequest {
  readonly pairs: readonly SilencingPair[]
  readonly attribute: string
}

// An element whose `class` changed since the index read it is left alone, and no finding is made on it.
const toggleDocument = (tools: IndexTools, request: ToggleRequest): readonly PairToggle[] => {
  for (const marked of document.querySelectorAll(`[${CSS.escape(request.attribute)}]`)) {
    marked.removeAttribute(request.attribute)
  }
  const elements: readonly Element[] = tools.classedElements()
  return request.pairs.map((pair: SilencingPair): PairToggle => {
    const element: Element | undefined = elements[pair.ordinal]
    if (element?.getAttribute('class') !== pair.classAttribute) {
      return { silencedMatters: true, silencerMatters: false }
    }
    const toggle: PairToggle = {
      silencedMatters: tools.doesMatter(element, pair.silenced, pair, tools),
      silencerMatters: tools.doesMatter(element, pair.silencer, pair, tools),
    }
    element.setAttribute(request.attribute, String(pair.ordinal))
    return toggle
  })
}

const clearMarks = (attribute: string): void => {
  for (const marked of document.querySelectorAll(`[${CSS.escape(attribute)}]`)) {
    marked.removeAttribute(attribute)
  }
}

const STEPS: Readonly<Record<keyof IndexTools, unknown>> = {
  classedElements,
  rulesOf,
  preludeOf,
  declarationsOf,
  appendedTo,
  selectorsOf,
  entriesOfRule,
  entriesOfAtRule,
  entriesOf,
  unreadableOf,
  canApply,
  classTokensOf,
  styleOf,
  doesMatter,
}

const toolsSource: string = `{${Object.entries(STEPS)
  .map(([name, step]: readonly [string, unknown]): string => `${name}: ${String(step)}`)
  .join(', ')}}`

/**
 * Judge the page as it stands by rule `class-silenced`: read its stylesheets, take each candidate class off its
 * element and put it back, and report the classes the toggles confirm are silenced.
 * @param page the page, settled and stilled.
 * @param place where its findings go.
 * @returns the findings, each judged against the exemptions above its element, and a note naming any
 *   stylesheet another origin serves when no finding names it.
 */
export const classSilencedVisit = async (page: Page, place: FindingPlace): Promise<Visit> => {
  const index: StyleIndex = await page.evaluate<StyleIndex>(`(${String(indexDocument)})(${toolsSource})`)
  const pairs: readonly SilencingPair[] = silencingCandidates(index)
  const request: ToggleRequest = { pairs, attribute: MARK_ATTRIBUTE }
  const toggles: readonly PairToggle[] =
    pairs.length === 0
      ? []
      : await page.evaluate<readonly PairToggle[]>(
          `(${String(toggleDocument)})(${toolsSource}, ${JSON.stringify(request)})`,
        )
  const confirmed: readonly SilencingPair[] = confirmedSilencing(pairs, toggles)
  const visits: readonly Visit[] = await Promise.all(
    confirmed.map(async (pair: SilencingPair): Promise<Visit> => {
      const facts: TargetFacts | undefined = await targetFacts(page, `[${MARK_ATTRIBUTE}="${String(pair.ordinal)}"]`)
      const message: string = `${facts?.label ?? 'an element'}: ${silencedMessage(pair, index.unreadable)}`
      return carried(findingAt(place, CLASS_SILENCED_RULE, message), facts)
    }),
  )
  await page.evaluate(clearMarks, MARK_ATTRIBUTE)
  return merged([
    ...visits,
    { ...EMPTY_VISIT, notes: confirmed.length === 0 ? unreadableNotes(place.location, index.unreadable) : [] },
  ])
}
