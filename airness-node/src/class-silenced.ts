// How the resting scan finds a class the stylesheet's order silences (rule `class-silenced`). An element that
// carries two utility classes setting the same property under the same condition renders only the one whose
// rule comes later in the stylesheet, and that order is whatever the CSS build happened to write, not anything
// the markup shows. A component library's default class and a caller's own, joined as plain strings by Radix
// `asChild` or a hand-written template, meet that way: `text-sm text-lg` renders at the size of whichever rule
// the build wrote last, and the other class does nothing at all.
//
// The browser side reads every readable stylesheet through the CSSOM into an index of rules, and then removes
// each candidate class from its element for as long as one style read takes. Everything that decides a verdict
// is here: which classes are utility-shaped, which pairs meet under one condition, and what the toggles prove.
//
// WHY THE INDEX COMPARES EVERY DECLARATION. Two utilities may declare one property and still both matter: in
// Tailwind v4, `text-lg leading-tight` both declare `line-height`, and `text-lg` still sets the font size. So does
// a class whose other declaration only a rule under another condition replaces: in
// `text-4xl leading-tight md:text-5xl`, `md:text-5xl` replaces the font size of `text-4xl` at desktop width only,
// and `text-4xl` sizes the heading on a phone. A class is a candidate only when its rivals on the element override
// each of its declarations under the very condition the class declares it.
//
// WHY THE TOGGLE STILL DECIDES. An index cannot tell whether a condition holds at the viewport judged, nor see
// what reading a stylesheet through the CSSOM missed. A class is reported only when taking it off the element
// changes none of the element's computed style, while taking off the class that silences it does, which also
// proves that the condition both rules share holds there.

/** The rule this module judges. */
export const CLASS_SILENCED_RULE: string = 'class-silenced'

/** One declaration of a rule, by the property name its stylesheet serializes it under. */
export interface StyleDeclaration {
  readonly property: string
  readonly isImportant: boolean
}

/** One selector of a style rule, flattened out of any nesting, with the at-rules it stands inside. */
export interface StyleEntry {
  readonly selector: string
  /** The preludes of the enclosing at-rules, outermost first: `@layer utilities`, `@media (width >= 48rem)`. */
  readonly conditions: readonly string[]
  readonly declarations: readonly StyleDeclaration[]
  /** Its position in the cascade's document order, across every stylesheet the page reads. */
  readonly order: number
}

/** An element carrying two classes or more, and the entries that name one of them and may apply to it. */
export interface ClassedElement {
  /** Its position among the page's classed elements, which the toggle finds it again by. */
  readonly ordinal: number
  /** The `class` attribute verbatim, so the toggle refuses an element that changed in between. */
  readonly classAttribute: string
  readonly classes: readonly string[]
  /** Positions in the index's entries. */
  readonly entries: readonly number[]
}

/** What the browser read of a page's stylesheets. */
export interface StyleIndex {
  readonly entries: readonly StyleEntry[]
  readonly elements: readonly ClassedElement[]
  /** The addresses of the stylesheets another origin serves, whose rules cannot be read. */
  readonly unreadable: readonly string[]
}

/** A class another class on the same element may silence, before the toggles confirm it. */
export interface SilencingPair {
  readonly ordinal: number
  readonly classAttribute: string
  readonly silenced: string
  readonly silencer: string
  /** The properties both declare under the same condition and importance. */
  readonly properties: readonly string[]
  /** The pseudo-element both rules style, such as `::before`, or empty for the element itself. */
  readonly pseudoElement: string
  /** The custom properties the element's rules declare, which a computed style lists in no engine reliably. */
  readonly customProperties: readonly string[]
}

/** What taking each class of a pair off its element did to the element's computed style. */
export interface PairToggle {
  readonly silencedMatters: boolean
  readonly silencerMatters: boolean
}

// A class selector's identifier, as the CSSOM serializes it: escapes, word characters and non-ASCII.
const IDENTIFIER: string = String.raw`(?:\\[\da-f]{1,6}\s?|\\[^\n\da-f]|[\w\P{ASCII}-])+`
const CLASS_HEAD: RegExp = new RegExp(String.raw`^\.(${IDENTIFIER})`, 'iu')
const CLASS_TOKENS: RegExp = new RegExp(String.raw`\.(${IDENTIFIER})`, 'giu')
const ESCAPE: RegExp = /\\(?:([\dA-F]{1,6})\s?|(.))/giu
const PSEUDO_NAME: RegExp = /^::?[a-z][\w-]*/iu
const TRAILING_PSEUDO_ELEMENT: RegExp = /::[a-z][\w-]*$/iu
const HEXADECIMAL: number = 16
const CUSTOM_PROPERTY: string = '--'

const unescaped = (identifier: string): string =>
  identifier.replaceAll(ESCAPE, (_escape: string, hexadecimal: string | undefined, character: string | undefined) =>
    hexadecimal === undefined ? (character ?? '') : String.fromCodePoint(Number.parseInt(hexadecimal, HEXADECIMAL)),
  )

/**
 * Where the parenthesis that opens a text closes.
 * @param text a text starting with `(`.
 * @returns the position of the matching `)`, or undefined when it never closes.
 */
const closingParenthesis = (text: string): number | undefined => {
  const scan: { readonly depth: number; readonly at: number | undefined } = Array.from(text).reduce(
    (state: { readonly depth: number; readonly at: number | undefined }, character: string, position: number) => {
      if (state.at !== undefined) {
        return state
      }
      const depth: number = state.depth + (character === '(' ? 1 : 0) - (character === ')' ? 1 : 0)
      return { depth, at: depth === 0 ? position : undefined }
    },
    { depth: 0, at: undefined },
  )
  return scan.at
}

/**
 * Whether a selector's remainder is pseudo-classes and pseudo-elements alone, such as `:hover` or `::before`.
 * @param text what follows the class.
 * @returns true for an empty remainder or one made of pseudo selectors only.
 */
const isPseudoSuffix = (text: string): boolean => {
  if (text === '') {
    return true
  }
  const name: RegExpExecArray | null = PSEUDO_NAME.exec(text)
  if (name === null) {
    return false
  }
  const rest: string = text.slice(name[0].length)
  if (!rest.startsWith('(')) {
    return isPseudoSuffix(rest)
  }
  const close: number | undefined = closingParenthesis(rest)
  return close !== undefined && isPseudoSuffix(rest.slice(close + 1))
}

/** A utility-shaped selector: one class, then pseudo selectors only. */
interface UtilityShape {
  readonly className: string
  readonly suffix: string
}

/**
 * The class and pseudo suffix of a utility-shaped selector, such as `.text-lg` or `.hover\:text-lg:hover`.
 * @param selector one selector of a style rule.
 * @returns the shape, or undefined for a selector with a combinator, a type, an attribute or a second class.
 */
const utilityShapeOf = (selector: string): UtilityShape | undefined => {
  const head: RegExpExecArray | null = CLASS_HEAD.exec(selector.trim())
  const identifier: string | undefined = head?.[1]
  if (head === null || identifier === undefined) {
    return undefined
  }
  const suffix: string = selector.trim().slice(head[0].length)
  return isPseudoSuffix(suffix) ? { className: unescaped(identifier), suffix } : undefined
}

const mentionedClasses = (selector: string): readonly string[] =>
  Array.from(selector.matchAll(CLASS_TOKENS), (match: RegExpExecArray): string => unescaped(match[1] ?? ''))

/** An entry through which a utility-shaped class applies to an element. */
interface ShapedEntry {
  readonly className: string
  readonly suffix: string
  readonly entry: StyleEntry
}

/**
 * The entries of each utility-shaped class of an element. A class is utility-shaped on an element when every
 * rule that names it and applies there is a selector of that one class and pseudo selectors; a class some
 * component rule also styles there, such as `.card .title`, is not.
 * @param index the page's stylesheet index.
 * @param element the element.
 * @returns the entries, of utility-shaped classes only.
 */
const shapedEntriesOf = (index: StyleIndex, element: ClassedElement): readonly ShapedEntry[] => {
  const entries: readonly StyleEntry[] = element.entries.flatMap((position: number): readonly StyleEntry[] => {
    const entry: StyleEntry | undefined = index.entries[position]
    return entry === undefined ? [] : [entry]
  })
  return element.classes.flatMap((className: string): readonly ShapedEntry[] => {
    const naming: readonly StyleEntry[] = entries.filter((entry: StyleEntry): boolean =>
      mentionedClasses(entry.selector).includes(className),
    )
    const shaped: readonly ShapedEntry[] = naming.flatMap((entry: StyleEntry): readonly ShapedEntry[] => {
      const shape: UtilityShape | undefined = utilityShapeOf(entry.selector)
      return shape?.className === className ? [{ className, suffix: shape.suffix, entry }] : []
    })
    return shaped.length === naming.length ? shaped : []
  })
}

const isSameCondition = (first: ShapedEntry, second: ShapedEntry): boolean =>
  first.suffix === second.suffix &&
  first.entry.conditions.length === second.entry.conditions.length &&
  first.entry.conditions.every((condition: string, at: number): boolean => condition === second.entry.conditions[at])

// The properties both declare with the same importance: where only one is `!important`, it wins by importance,
// which its author chose, and not by the order the build wrote.
const sharedProperties = (first: StyleEntry, second: StyleEntry): readonly string[] => [
  ...new Set<string>(
    first.declarations
      .filter((declaration: StyleDeclaration): boolean =>
        second.declarations.some(
          (other: StyleDeclaration): boolean =>
            other.property === declaration.property && other.isImportant === declaration.isImportant,
        ),
      )
      .map((declaration: StyleDeclaration): string => declaration.property),
  ),
]

/** One meeting of two classes' entries: the later one wins every property they share. */
interface Meeting {
  readonly loser: ShapedEntry
  readonly winner: ShapedEntry
  readonly properties: readonly string[]
}

const meetingOf = (first: ShapedEntry, second: ShapedEntry): Meeting | undefined => {
  const properties: readonly string[] = sharedProperties(first.entry, second.entry)
  if (first.className === second.className || !isSameCondition(first, second) || properties.length === 0) {
    return undefined
  }
  const isFirstLater: boolean = first.entry.order > second.entry.order
  return { loser: isFirstLater ? second : first, winner: isFirstLater ? first : second, properties }
}

const meetingsOf = (shaped: readonly ShapedEntry[]): readonly Meeting[] =>
  shaped.flatMap((first: ShapedEntry, at: number): readonly Meeting[] =>
    shaped.slice(at + 1).flatMap((second: ShapedEntry): readonly Meeting[] => {
      const meeting: Meeting | undefined = meetingOf(first, second)
      return meeting === undefined ? [] : [meeting]
    }),
  )

// Whether a declaration of an entry loses to a rival class under the same condition: to a later declaration of
// the same importance, or to an `!important` one over a normal one.
const isOverridden = (shaped: readonly ShapedEntry[], own: ShapedEntry, declaration: StyleDeclaration): boolean =>
  shaped.some(
    (rival: ShapedEntry): boolean =>
      rival.className !== own.className &&
      isSameCondition(rival, own) &&
      rival.entry.declarations.some(
        (other: StyleDeclaration): boolean =>
          other.property === declaration.property &&
          (other.isImportant === declaration.isImportant ? rival.entry.order > own.entry.order : other.isImportant),
      ),
  )

/**
 * Whether the other classes of an element override everything a class declares, each under the condition the
 * class declares it. A class that keeps one declaration of its own still sets something somewhere: in
 * `text-4xl leading-tight md:text-5xl`, `text-4xl` sets the phone's font size, although at desktop width
 * `md:text-5xl` replaces it and `leading-tight` its line height, so that removing it changes nothing there.
 * @param shaped the entries of the element's utility-shaped classes.
 * @param className the class.
 * @returns true when no declaration of the class survives its rivals under its own condition.
 */
const isCovered = (shaped: readonly ShapedEntry[], className: string): boolean =>
  shaped
    .filter((own: ShapedEntry): boolean => own.className === className)
    .every((own: ShapedEntry): boolean =>
      own.entry.declarations.every((declaration: StyleDeclaration): boolean => isOverridden(shaped, own, declaration)),
    )

const customPropertiesOf = (shaped: readonly ShapedEntry[]): readonly string[] => [
  ...new Set<string>(
    shaped.flatMap((entry: ShapedEntry): readonly string[] =>
      entry.entry.declarations
        .map((declaration: StyleDeclaration): string => declaration.property)
        .filter((property: string): boolean => property.startsWith(CUSTOM_PROPERTY)),
    ),
  ),
]

const elementCandidates = (index: StyleIndex, element: ClassedElement): readonly SilencingPair[] => {
  const shaped: readonly ShapedEntry[] = shapedEntriesOf(index, element)
  const meetings: readonly Meeting[] = meetingsOf(shaped).filter((meeting: Meeting): boolean =>
    isCovered(shaped, meeting.loser.className),
  )
  const keys: readonly string[] = [
    ...new Set<string>(
      meetings.map((meeting: Meeting): string => `${meeting.loser.className} ${meeting.winner.className}`),
    ),
  ]
  return keys.flatMap((key: string): readonly SilencingPair[] => {
    const same: readonly Meeting[] = meetings.filter(
      (meeting: Meeting): boolean => `${meeting.loser.className} ${meeting.winner.className}` === key,
    )
    const [first] = same
    return first === undefined
      ? []
      : [
          {
            ordinal: element.ordinal,
            classAttribute: element.classAttribute,
            silenced: first.loser.className,
            silencer: first.winner.className,
            properties: [...new Set<string>(same.flatMap((meeting: Meeting): readonly string[] => meeting.properties))],
            pseudoElement: TRAILING_PSEUDO_ELEMENT.exec(first.loser.suffix)?.[0] ?? '',
            customProperties: customPropertiesOf(shaped),
          },
        ]
  })
}

// Elements carrying the same classes, styled by the same rules, are instances of one component, and the
// toggle answers the same for each: the first stands for all of them.
const distinctElements = (elements: readonly ClassedElement[]): readonly ClassedElement[] => {
  const keyOf = (element: ClassedElement): string =>
    `${[...element.classes].sort((first: string, second: string): number => first.localeCompare(second)).join(' ')}|${element.entries.join(',')}`
  const keys: readonly string[] = elements.map((element: ClassedElement): string => keyOf(element))
  return elements.filter((element: ClassedElement, at: number): boolean => keys.indexOf(keyOf(element)) === at)
}

/**
 * The pairs of utility classes on one element that set a property under the same condition, where the later
 * rule wins by stylesheet order alone (rule `class-silenced`), and where the other classes override every
 * declaration of the silenced one under its own condition. Each still needs the toggle to confirm it.
 * @param index what the browser read of the page's stylesheets.
 * @returns one pair per silenced class and the class that may silence it, on one element of each component.
 */
export const silencingCandidates = (index: StyleIndex): readonly SilencingPair[] =>
  distinctElements(index.elements).flatMap((element: ClassedElement): readonly SilencingPair[] =>
    elementCandidates(index, element),
  )

/**
 * The pairs the toggles confirm: taking the silenced class off changed nothing on the element, and taking the
 * class that silences it off changed something. A class several others silence is reported once, under the one
 * sharing the most properties with it.
 * @param pairs the candidates.
 * @param toggles what the toggle found for each candidate, at the same position.
 * @returns the confirmed pairs, one per silenced class of an element.
 */
export const confirmedSilencing = (
  pairs: readonly SilencingPair[],
  toggles: readonly PairToggle[],
): readonly SilencingPair[] => {
  const confirmed: readonly SilencingPair[] = pairs.filter((_pair: SilencingPair, at: number): boolean => {
    const toggle: PairToggle | undefined = toggles[at]
    return toggle !== undefined && !toggle.silencedMatters && toggle.silencerMatters
  })
  const strongest = (pair: SilencingPair): SilencingPair => {
    const rivals: readonly SilencingPair[] = confirmed.filter(
      (other: SilencingPair): boolean => other.ordinal === pair.ordinal && other.silenced === pair.silenced,
    )
    return rivals.reduce(
      (best: SilencingPair, other: SilencingPair): SilencingPair =>
        other.properties.length > best.properties.length ? other : best,
      rivals[0] ?? pair,
    )
  }
  return confirmed.filter((pair: SilencingPair): boolean => strongest(pair) === pair)
}

const listed = (items: readonly string[]): string =>
  items.length <= 1 ? items.join('') : `${items.slice(0, -1).join(', ')} and ${items.at(-1) ?? ''}`

const unreadablePhrase = (unreadable: readonly string[]): string =>
  unreadable.length === 1
    ? `the stylesheet ${listed(unreadable)} comes from another origin and could not be read, so its rules were not judged`
    : `the stylesheets ${listed(unreadable)} come from another origin and could not be read, so their rules were not ` +
      'judged'

/**
 * The detail of a confirmed pair's finding, to follow the element's label.
 * @param pair the confirmed pair.
 * @param unreadable the stylesheets of the page that could not be read.
 * @returns the message, naming the repair.
 */
export const silencedMessage = (pair: SilencingPair, unreadable: readonly string[]): string =>
  [
    `${pair.silenced} has no effect: ${pair.silencer} on the same element sets ${listed(pair.properties)} under ` +
      'the same condition and wins by stylesheet order; remove one of the two classes, or let the component ' +
      "merge its default with the caller's class",
    ...(unreadable.length === 0 ? [] : [unreadablePhrase(unreadable)]),
  ].join('; ')

/**
 * What the summary is told about a page whose stylesheets could not all be read, when no finding says it.
 * @param location the route or story.
 * @param unreadable the stylesheets another origin serves.
 * @returns one note, or none when every stylesheet was read.
 */
export const unreadableNotes = (location: string, unreadable: readonly string[]): readonly string[] =>
  unreadable.length === 0 ? [] : [`${location}: ${CLASS_SILENCED_RULE}: ${unreadablePhrase(unreadable)}`]
