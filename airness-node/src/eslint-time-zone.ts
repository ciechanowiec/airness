// Date formatting that names the time zone it formats in.
//
// Intl formats an instant in the zone its options name and otherwise in the zone of the runtime: the
// server's on a server render, each visitor's in a browser. The same instant then reads as a different
// calendar day or hour depending on where it is formatted, which shows a wrong day west of UTC and makes a
// server-rendered page disagree with its own hydration. So every formatter of a date names its zone.
//
// The rule reads syntax, not types, and claims only what syntax decides:
// - `Intl.DateTimeFormat`, with or without `new`, and `toLocaleDateString` and `toLocaleTimeString`, which
//   exist only on a date, are reported when they have no options, `undefined` options, or an inline options
//   object without a `timeZone` property. The zone of a `DateTimeFormat` is fixed when it is made, so its
//   `format`, `formatToParts` and `formatRange` need no rule of their own.
// - `toLocaleString` is shared with numbers, big integers, arrays and the Temporal types, so it is reported
//   only when its inline options object names a date or time field, which no number or array option is. A
//   call without options is passed over, because only the receiver's type tells a date from a number.
// - Options passed as a variable, a call or a spread cannot be read here and are passed over, and so is an
//   inline object that spreads another one in, which may carry the zone.
// - A formatter made only to read `resolvedOptions()` formats nothing, so the idiom that reads the
//   runtime's own zone, `new Intl.DateTimeFormat().resolvedOptions().timeZone`, is passed over.
import type { RestrictedSyntax } from './eslint-security.js'

const MESSAGE: string =
  "[implicit-time-zone] Pass an explicit timeZone option, for example { timeZone: 'UTC' } or the zone the " +
  "date belongs to. Without it the date is formatted in the runtime's zone, so a server render and each " +
  "visitor's browser can show a different day or time."

const union = (selectors: readonly string[]): string => `:matches(${selectors.join(', ')})`

// A member named either way: `a.name` or `a['name']`.
const memberNamed = (path: string, name: string): string =>
  union([`[${path}.property.name=${name}]`, `[${path}.property.value=${name}]`])

// `Intl` itself, or `Intl` reached through a global object: `globalThis.Intl`, `window.Intl`.
const INTL: string = union(["[callee.object.name='Intl']", memberNamed('callee.object', "'Intl'")])

// A formatter made but only asked for its resolved options.
const READ_ONLY: string = ":not(MemberExpression[property.name='resolvedOptions'] > .object)"

const DATE_TIME_FORMAT: string = `:matches(NewExpression, CallExpression)${INTL}${memberNamed('callee', "'DateTimeFormat'")}${READ_ONLY}`

const DATE_METHOD: string = `CallExpression${memberNamed('callee', '/^toLocale(?:Date|Time)String$/')}`

const LOCALE_STRING: string = `CallExpression${memberNamed('callee', "'toLocaleString'")}`

// The formatters whose options always concern a date.
const DATE_FORMATTERS: readonly string[] = [DATE_TIME_FORMAT, DATE_METHOD]

// No options at all, or `undefined` in their place. A spread first argument may supply them, so it is passed
// over.
const NO_OPTIONS: string = union([
  ":not([arguments.1]):not([arguments.0.type='SpreadElement'])",
  "[arguments.1.type='Identifier'][arguments.1.name='undefined']",
])

// The options argument written inline, also through `satisfies` or `as`.
const inlineOptions = (call: string): readonly string[] => [
  `${call} > ObjectExpression:nth-child(2)`,
  `${call} > TSSatisfiesExpression:nth-child(2) > ObjectExpression`,
  `${call} > TSAsExpression:nth-child(2) > ObjectExpression`,
]

const NAMED_ZONE: string = union([
  "Property[computed=false][key.name='timeZone']",
  "Property[key.type='Literal'][key.value='timeZone']",
])

// A `timeZone` property whose value is not `undefined`.
const STATED_ZONE: string = `${NAMED_ZONE}:not([value.type='Identifier'][value.name='undefined'])`

// An options object that states no zone of its own and spreads in none either.
const WITHOUT_ZONE: string = `:not(:has(> ${STATED_ZONE})):not(:has(> SpreadElement))`

const DATE_FIELD: string =
  '/^(?:dateStyle|timeStyle|weekday|era|year|month|day|dayPeriod|hour|minute|second|fractionalSecondDigits|' +
  'timeZoneName|hour12|hourCycle)$/'

const DATE_FIELD_PROPERTY: string = union([`Property[key.name=${DATE_FIELD}]`, `Property[key.value=${DATE_FIELD}]`])

const NAMES_DATE_FIELD: string = `:has(> ${DATE_FIELD_PROPERTY})`

/** Date formatting that leaves the time zone to the runtime. */
export const TIME_ZONE_RESTRICTIONS: readonly RestrictedSyntax[] = [
  { selector: union(DATE_FORMATTERS.map((call: string): string => `${call}${NO_OPTIONS}`)), message: MESSAGE },
  {
    selector: union(
      DATE_FORMATTERS.flatMap((call: string): readonly string[] => inlineOptions(call)).map(
        (options: string): string => `${options}${WITHOUT_ZONE}`,
      ),
    ),
    message: MESSAGE,
  },
  {
    selector: union(
      inlineOptions(LOCALE_STRING).map((options: string): string => `${options}${NAMES_DATE_FIELD}${WITHOUT_ZONE}`),
    ),
    message: MESSAGE,
  },
]
