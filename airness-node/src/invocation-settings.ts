import { isArray } from './json-shapes.js'

/** Service preparation and process wrapping are application facts, with no control over gate selection. */
export interface InvocationSettings {
  readonly pretest: readonly string[]
  readonly testWrapper: readonly string[]
}

const command = (raw: unknown, key: string): readonly string[] => {
  if (raw === undefined) {
    return []
  }
  if (!(isArray(raw) && raw.every((entry: unknown): entry is string => typeof entry === 'string'))) {
    throw new Error(`airness.${key} must be an array of command arguments`)
  }
  if (raw.length > 0 && (raw[0] ?? '').trim().length === 0) {
    throw new Error(`airness.${key} must name an executable`)
  }
  return raw
}

/** Refuse malformed setup rather than silently running tests without the requested isolation. */
export const invocationSettings = (raw: Readonly<Record<string, unknown>>): InvocationSettings => ({
  pretest: command(raw['pretest'], 'pretest'),
  testWrapper: command(raw['testWrapper'], 'testWrapper'),
})
