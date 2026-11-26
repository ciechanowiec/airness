import { describe, expect, it } from 'vitest'
import {
  type ClassedElement,
  confirmedSilencing,
  type PairToggle,
  type SilencingPair,
  type StyleEntry,
  type StyleIndex,
  silencedMessage,
  silencingCandidates,
  unreadableNotes,
} from '@/class-silenced'

const UTILITIES: readonly string[] = ['@layer utilities']

// A declaration written with a trailing `!` is `!important`.
const rule = (
  selector: string,
  order: number,
  properties: readonly string[],
  conditions: readonly string[] = [],
): StyleEntry => ({
  selector,
  order,
  conditions,
  declarations: properties.map((property: string) =>
    property.endsWith('!') ? { property: property.slice(0, -1), isImportant: true } : { property, isImportant: false },
  ),
})

const element = (classAttribute: string, entries: readonly number[], ordinal: number = 0): ClassedElement => ({
  ordinal,
  classAttribute,
  classes: classAttribute.split(' '),
  entries,
})

// One element carrying the classes, styled by every rule given.
const pageOf = (
  classAttribute: string,
  entries: readonly StyleEntry[],
  unreadable: readonly string[] = [],
): StyleIndex => ({
  entries,
  elements: [
    element(
      classAttribute,
      entries.map((_entry: StyleEntry, at: number): number => at),
    ),
  ],
  unreadable,
})

const described = (index: StyleIndex): readonly string[] =>
  silencingCandidates(index).map(
    (pair: SilencingPair): string => `${pair.silenced} < ${pair.silencer}: ${pair.properties.join(', ')}`,
  )

// What the browser would find on taking each class off: only the named classes change nothing.
const toggled = (pairs: readonly SilencingPair[], withoutEffect: readonly string[]): readonly PairToggle[] =>
  pairs.map(
    (pair: SilencingPair): PairToggle => ({
      silencedMatters: !withoutEffect.includes(pair.silenced),
      silencerMatters: !withoutEffect.includes(pair.silencer),
    }),
  )

const confirmed = (index: StyleIndex, withoutEffect: readonly string[]): readonly string[] => {
  const pairs: readonly SilencingPair[] = silencingCandidates(index)
  return confirmedSilencing(pairs, toggled(pairs, withoutEffect)).map((pair: SilencingPair): string =>
    silencedMessage(pair, index.unreadable),
  )
}

// The rendered dialog description of the fixture: `cn('text-sm text-muted', className)` joined with the caller's
// `text-lg leading-relaxed` through `asChild`, in the order Tailwind v4 wrote the rules.
const DIALOG_DESCRIPTION: StyleIndex = pageOf('text-sm text-muted text-lg leading-relaxed', [
  rule('.text-lg', 0, ['font-size', 'line-height'], UTILITIES),
  rule('.text-sm', 1, ['font-size', 'line-height'], UTILITIES),
  rule('.leading-relaxed', 2, ['--tw-leading', 'line-height'], UTILITIES),
  rule('.text-muted', 3, ['color'], UTILITIES),
])

const DIALOG_FINDING: string =
  'text-lg has no effect: text-sm on the same element sets font-size and line-height under the same condition ' +
  "and wins by stylesheet order; remove one of the two classes, or let the component merge its default with the caller's class"

// A title class beside a larger one, where a component rule between them also styles the title.
const styledBeside = (component: string): readonly string[] =>
  described(
    pageOf('title-lg title', [
      rule('.title-lg', 0, ['font-size']),
      rule(component, 1, ['font-size']),
      rule('.title', 2, ['font-size']),
    ]),
  )

// Two Tailwind group variants written with one pseudo suffix.
const groupVariants = (suffix: string): readonly string[] =>
  described(
    pageOf('group-hover:underline group-hover:no-underline', [
      rule(String.raw`.group-hover\:underline${suffix}`, 0, ['text-decoration-line']),
      rule(String.raw`.group-hover\:no-underline${suffix}`, 1, ['text-decoration-line']),
    ]),
  )

describe('class-silenced candidates', () => {
  it('pair each overridden utility with every later one that sets a property it sets, under the same condition', () => {
    expect(described(DIALOG_DESCRIPTION)).toEqual([
      'text-lg < text-sm: font-size, line-height',
      'text-lg < leading-relaxed: line-height',
    ])
  })

  it('carry the custom properties the element rules declare, for the toggle to compare', () => {
    expect(silencingCandidates(DIALOG_DESCRIPTION).map((pair: SilencingPair) => pair.customProperties)).toEqual([
      ['--tw-leading'],
      ['--tw-leading'],
    ])
  })

  it('pass a class whose other declaration only a rule under another condition replaces, as text-4xl is', () => {
    const page: StyleIndex = pageOf('text-4xl leading-tight md:text-5xl', [
      rule('.text-4xl', 0, ['font-size', 'line-height'], UTILITIES),
      rule('.leading-tight', 1, ['--tw-leading', 'line-height'], UTILITIES),
      rule(String.raw`.md\:text-5xl`, 2, ['font-size', 'line-height'], [...UTILITIES, '@media (width >= 48rem)']),
    ])
    expect(described(page)).toEqual([])
  })

  it('pass a class that keeps a declaration no rival sets, as tone tone-accent is', () => {
    const page: StyleIndex = pageOf('tone tone-accent', [
      rule('.tone', 0, ['padding', 'color']),
      rule('.tone-accent', 1, ['color']),
    ])
    expect(described(page)).toEqual([])
  })

  it('count a declaration an important rival overrides, beside one a later rival wins by order', () => {
    const page: StyleIndex = pageOf('tone pad-forced pad-tight', [
      rule('.tone', 0, ['padding', 'color']),
      rule('.pad-forced', 1, ['color!']),
      rule('.pad-tight', 2, ['padding']),
    ])
    expect(described(page)).toEqual(['tone < pad-tight: padding'])
  })

  it('keep a class whose declaration only an earlier rival sets, while pairing that rival under it', () => {
    const page: StyleIndex = pageOf('tone pad-early pad-tight', [
      rule('.pad-early', 0, ['color']),
      rule('.tone', 1, ['padding', 'color']),
      rule('.pad-tight', 2, ['padding']),
    ])
    expect(described(page)).toEqual(['pad-early < tone: color'])
  })

  it('find the plain stylesheet pair, with no framework and no at-rule', () => {
    const page: StyleIndex = pageOf('size-large size-small', [
      rule('.size-large', 0, ['font-size']),
      rule('.size-small', 1, ['font-size']),
    ])
    expect(described(page)).toEqual(['size-large < size-small: font-size'])
  })

  it('pass two classes set under different at-rules, as hidden md:flex is', () => {
    const page: StyleIndex = pageOf('hidden md:flex', [
      rule('.hidden', 0, ['display'], UTILITIES),
      rule(String.raw`.md\:flex`, 1, ['display'], [...UTILITIES, '@media (width >= 48rem)']),
    ])
    expect(described(page)).toEqual([])
  })

  it('pass two classes set under different pseudo suffixes, as text-sm hover:text-lg is', () => {
    const page: StyleIndex = pageOf('text-sm hover:text-lg', [
      rule(String.raw`.hover\:text-lg:hover`, 0, ['font-size'], UTILITIES),
      rule('.text-sm', 1, ['font-size'], UTILITIES),
    ])
    expect(described(page)).toEqual([])
  })

  it('pass a shorthand and its longhand, as p-4 px-2 is, which the narrower one wins on purpose', () => {
    const page: StyleIndex = pageOf('p-4 px-2', [
      rule('.p-4', 0, ['padding'], UTILITIES),
      rule('.px-2', 1, ['padding-inline'], UTILITIES),
    ])
    expect(described(page)).toEqual([])
  })

  it('pass an override its author marked important, which wins by importance rather than by order', () => {
    const page: StyleIndex = pageOf('!text-xl text-sm', [
      rule(String.raw`.\!text-xl`, 0, ['font-size!', 'line-height!'], UTILITIES),
      rule('.text-sm', 1, ['font-size', 'line-height'], UTILITIES),
    ])
    expect(described(page)).toEqual([])
  })

  it('pair two important declarations, which order decides again', () => {
    const page: StyleIndex = pageOf('!text-xl !text-sm', [
      rule(String.raw`.\!text-xl`, 0, ['font-size!'], UTILITIES),
      rule(String.raw`.\!text-sm`, 1, ['font-size!'], UTILITIES),
    ])
    expect(described(page)).toEqual(['!text-xl < !text-sm: font-size'])
  })

  it('pass a class a component rule also styles on the element, whatever selects it beside the class', () => {
    expect([
      styledBeside('.card .title'),
      styledBeside('.card > .title'),
      styledBeside('p.title'),
      styledBeside('.title.big'),
    ]).toEqual([[], [], [], []])
  })

  it('pair the same classes when no component rule styles them there', () => {
    const page: StyleIndex = pageOf('title-lg title', [
      rule('.title-lg', 0, ['font-size']),
      rule('.title', 2, ['font-size']),
    ])
    expect(described(page)).toEqual(['title-lg < title: font-size'])
  })

  it('read escaped class names as the element carries them, in every escape a stylesheet writes', () => {
    const page: StyleIndex = pageOf('hover:text-lg hover:text-sm 2xl:p-4 2xl:p-2 w-1/2 w-2/3', [
      rule(String.raw`.hover\:text-lg:hover`, 0, ['font-size'], ['@media (hover: hover)']),
      rule(String.raw`.hover\:text-sm:hover`, 1, ['font-size'], ['@media (hover: hover)']),
      rule(String.raw`.\32 xl\:p-4`, 2, ['padding'], ['@media (width >= 96rem)']),
      rule(String.raw`.\32 xl\:p-2`, 3, ['padding'], ['@media (width >= 96rem)']),
      rule(String.raw`.w-1\/2`, 4, ['width']),
      rule(String.raw`.w-2\/3`, 5, ['width']),
    ])
    expect(described(page)).toEqual([
      'hover:text-lg < hover:text-sm: font-size',
      '2xl:p-4 < 2xl:p-2: padding',
      'w-1/2 < w-2/3: width',
    ])
  })

  it('take a functional pseudo-class as a suffix, and a selector that never closes its parenthesis as none', () => {
    expect([groupVariants(':is(:where(.group):hover *)'), groupVariants(':is(:where(.group):hover *')]).toEqual([
      ['group-hover:underline < group-hover:no-underline: text-decoration-line'],
      [],
    ])
  })

  it('name the pseudo-element both rules style, for the toggle to read', () => {
    const page: StyleIndex = pageOf('marker-dot marker-ring', [
      rule('.marker-dot::before', 0, ['content']),
      rule('.marker-ring::before', 1, ['content']),
    ])
    expect(silencingCandidates(page).map((pair: SilencingPair): string => pair.pseudoElement)).toEqual(['::before'])
  })

  it('judge one element of each component, and every element of different ones', () => {
    const entries: readonly StyleEntry[] = [rule('.a', 0, ['color']), rule('.b', 1, ['color'])]
    const page: StyleIndex = {
      entries,
      elements: [element('a b', [0, 1], 0), element('b a', [0, 1], 1), element('b a', [1], 2)],
      unreadable: [],
    }
    expect(silencingCandidates(page).map((pair: SilencingPair): number => pair.ordinal)).toEqual([0])
  })
})

describe('class-silenced confirmation', () => {
  it('report the fixture class under the class sharing the most properties with it', () => {
    expect(confirmed(DIALOG_DESCRIPTION, ['text-lg'])).toEqual([DIALOG_FINDING])
  })

  it('pass text-lg leading-tight, which both still change the element in either order, before any toggle', () => {
    const page: StyleIndex = pageOf('text-lg leading-tight', [
      rule('.text-lg', 0, ['font-size', 'line-height'], UTILITIES),
      rule('.leading-tight', 1, ['--tw-leading', 'line-height'], UTILITIES),
    ])
    expect([described(page), confirmed(page, [])]).toEqual([[], []])
  })

  it('pass a BEM block and its modifier before any toggle, since the block keeps its padding', () => {
    const page: StyleIndex = pageOf('button button--primary', [
      rule('.button', 0, ['padding', 'background-color', 'color']),
      rule('.button--primary', 1, ['background-color', 'color']),
    ])
    expect([described(page), confirmed(page, [])]).toEqual([[], []])
  })

  it('pass a pair whose shared condition does not hold at the viewport judged, where neither class matters', () => {
    const page: StyleIndex = pageOf('md:text-lg md:text-sm', [
      rule(String.raw`.md\:text-lg`, 0, ['font-size'], ['@media (width >= 48rem)']),
      rule(String.raw`.md\:text-sm`, 1, ['font-size'], ['@media (width >= 48rem)']),
    ])
    expect(confirmed(page, ['md:text-lg', 'md:text-sm'])).toEqual([])
  })

  it('report the plain stylesheet pair', () => {
    const page: StyleIndex = pageOf('size-large size-small', [
      rule('.size-large', 0, ['font-size']),
      rule('.size-small', 1, ['font-size']),
    ])
    expect(confirmed(page, ['size-large'])).toEqual([
      'size-large has no effect: size-small on the same element sets font-size under the same condition and wins ' +
        "by stylesheet order; remove one of the two classes, or let the component merge its default with the caller's class",
    ])
  })

  it('pass a candidate the toggle did not answer for', () => {
    const pairs: readonly SilencingPair[] = silencingCandidates(DIALOG_DESCRIPTION)
    expect(confirmedSilencing(pairs, [])).toEqual([])
  })
})

describe('an unreadable stylesheet', () => {
  const Theme: string = 'http://127.0.0.1:4100/theme.css'

  it('is named in the detail of every finding on its page', () => {
    const page: StyleIndex = pageOf(
      'size-large size-small',
      [rule('.size-large', 0, ['font-size']), rule('.size-small', 1, ['font-size'])],
      [Theme],
    )
    expect(confirmed(page, ['size-large'])[0]?.split('; ').at(-1)).toBe(
      `the stylesheet ${Theme} comes from another origin and could not be read, so its rules were not judged`,
    )
  })

  it('is named in a note when no finding names it, and every stylesheet read leaves none', () => {
    const Fonts: string = 'http://127.0.0.1:4100/fonts.css'
    expect([unreadableNotes('/checkout', [Theme, Fonts]), unreadableNotes('/checkout', [])]).toEqual([
      [
        `/checkout: class-silenced: the stylesheets ${Theme} and ${Fonts} come from another origin and could not ` +
          'be read, so their rules were not judged',
      ],
      [],
    ])
  })
})
