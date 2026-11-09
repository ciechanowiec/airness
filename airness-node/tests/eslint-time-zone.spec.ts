import { Linter } from 'eslint'
import tseslint from 'typescript-eslint'
import { describe, expect, it } from 'vitest'
import { TIME_ZONE_RESTRICTIONS } from '@/eslint-time-zone.js'

// Date formatting linted on its own: every spelling that leaves the zone to the runtime is reported once,
// and every spelling that names it, or whose options syntax cannot read, is passed over.

const linter: Linter = new Linter({ configType: 'flat' })

const tagsOf = (code: string): readonly string[] =>
  linter
    .verify(
      code,
      [
        {
          files: ['**/*.ts'],
          languageOptions: { parser: tseslint.parser },
          rules: { 'no-restricted-syntax': ['error', ...TIME_ZONE_RESTRICTIONS] },
        },
      ],
      'format.ts',
    )
    .map((message: Linter.LintMessage): string => /^\[([a-z-]+)\]/u.exec(message.message)?.[1] ?? message.message)

describe('implicit-time-zone', () => {
  it.each([
    "new Intl.DateTimeFormat('en', { day: 'numeric', month: 'long' })",
    "Intl.DateTimeFormat('en', { day: 'numeric', month: 'long' })",
    "new Intl.DateTimeFormat('en', { day: 'numeric', month: 'long' }).format(date)",
    "new Intl.DateTimeFormat('en', { day: 'numeric' }).formatToParts(date)",
    "new Intl.DateTimeFormat('en', { day: 'numeric' }).formatRange(start, end)",
    "new Intl['DateTimeFormat']('en', { day: 'numeric' })",
    "new globalThis.Intl.DateTimeFormat('en', { day: 'numeric' })",
    "new window['Intl'].DateTimeFormat('en', { day: 'numeric' })",
    "new Intl.DateTimeFormat('en')",
    'new Intl.DateTimeFormat()',
    'Intl.DateTimeFormat(locale)',
    'new Intl.DateTimeFormat(locale, undefined)',
    "new Intl.DateTimeFormat('en', {})",
    "new Intl.DateTimeFormat('en', { day: 'numeric' } satisfies Intl.DateTimeFormatOptions)",
    "new Intl.DateTimeFormat('en', { day: 'numeric' } as const)",
    "new Intl.DateTimeFormat('en', { day: 'numeric', timeZone: undefined })",
    "new Intl.DateTimeFormat('en', { [timeZone]: 'UTC', day: 'numeric' })",
    "new Intl.DateTimeFormat('en', { day: 'numeric', extra: { timeZone: 'UTC' } })",
    "date.toLocaleDateString('en', { day: 'numeric', month: 'long' })",
    "date.toLocaleTimeString('en', { hour: '2-digit' })",
    "date?.toLocaleDateString('en', { day: 'numeric' })",
    "date['toLocaleDateString']('en', { day: 'numeric' })",
    'date.toLocaleDateString()',
    "date.toLocaleTimeString('en')",
    "new Date(value).toLocaleDateString('en')",
    'date.toLocaleDateString(locale, undefined)',
    "date.toLocaleString('en', { day: 'numeric', month: 'long' })",
    "date.toLocaleString('en', { dateStyle: 'medium' })",
    "date.toLocaleString('en', { 'hour': 'numeric' })",
    "date?.toLocaleString('en', { weekday: 'long' })",
    "date['toLocaleString']('en', { year: 'numeric' })",
    "date.toLocaleString('en', { timeZoneName: 'short' })",
    "date.toLocaleString('en', { hour12: false, minute: '2-digit' } satisfies Intl.DateTimeFormatOptions)",
  ])('reports %s', (code: string) => {
    expect(tagsOf(code)).toEqual(['implicit-time-zone'])
  })

  it.each([
    "new Intl.DateTimeFormat('en', { day: 'numeric', timeZone: 'UTC' })",
    "Intl.DateTimeFormat('en', { day: 'numeric', timeZone: zone })",
    "new Intl.DateTimeFormat('en', { day: 'numeric', timeZone })",
    "new Intl.DateTimeFormat('en', { day: 'numeric', 'timeZone': 'Europe/Warsaw' })",
    "new Intl.DateTimeFormat('en', { day: 'numeric', ['timeZone']: 'UTC' })",
    "new Intl.DateTimeFormat('en', { timeZone: 'UTC', day: 'numeric' } satisfies Intl.DateTimeFormatOptions)",
    "new Intl.DateTimeFormat('en', { ...base, day: 'numeric' })",
    "new Intl.DateTimeFormat('en', options)",
    "new Intl.DateTimeFormat('en', optionsFor(locale))",
    'new Intl.DateTimeFormat(...formatArguments)',
    'new Intl.DateTimeFormat(locale, ...rest)',
    'new Intl.DateTimeFormat().resolvedOptions().timeZone',
    "Intl.DateTimeFormat('en').resolvedOptions().locale",
    "Intl.DateTimeFormat.supportedLocalesOf(['en'])",
    "new Intl.NumberFormat('en', { style: 'percent' })",
    "new Intl.RelativeTimeFormat('en', { numeric: 'auto' })",
    "date.toLocaleDateString('en', { day: 'numeric', timeZone: 'UTC' })",
    "date.toLocaleTimeString('en', { hour: '2-digit', timeZone: zone })",
    "date.toLocaleDateString('en', options)",
    "date.toLocaleString('en', { day: 'numeric', timeZone: 'UTC' })",
    'amount.toLocaleString()',
    "amount.toLocaleString('en')",
    "amount.toLocaleString('en', { style: 'currency', currency: 'EUR' })",
    "amount.toLocaleString('en', { maximumFractionDigits: 2 })",
    "duration.toLocaleString('en', { unit: 'second', style: 'unit' })",
    "items.toLocaleString('en', options)",
    "date.toLocaleString('en', { ...base, day: 'numeric' })",
  ])('passes %s', (code: string) => {
    expect(tagsOf(code)).toEqual([])
  })
})
