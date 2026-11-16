// Slugs: the readable, URL-safe key a post or a category is addressed by, as in `/posts/my-first-post`.

/** The longest slug the collections store. */
export const SLUG_MAX_LENGTH: number = 80

/** Lowercase ASCII letters, digits and hyphens; where the hyphens may stand is checked apart, without backtracking. */
const SLUG_CHARACTERS: RegExp = /^[\da-z-]+$/u
const HYPHEN: string = '-'

const DIACRITICS: RegExp = /\p{M}/gu
const SEPARATORS: RegExp = /[^\da-z]+/gu

/**
 * Whether a value is a slug a collection may store.
 * @param value the value an editor or a hook supplied.
 * @returns true for a slug of at most {@link SLUG_MAX_LENGTH} characters.
 */
const isSlug = (value: unknown): value is string =>
  typeof value === 'string' &&
  value.length <= SLUG_MAX_LENGTH &&
  SLUG_CHARACTERS.test(value) &&
  !value.startsWith(HYPHEN) &&
  !value.endsWith(HYPHEN) &&
  !value.includes(`${HYPHEN}${HYPHEN}`)

/**
 * The validation of a slug field, in the shape Payload calls it: true, or the message an editor reads.
 * @param value the value the field holds.
 * @returns true for a slug, or why the value is not one.
 */
export const validateSlug = (value: unknown): string | true =>
  isSlug(value)
    ? true
    : `Use lowercase letters, digits and single hyphens, at most ${String(SLUG_MAX_LENGTH)} characters.`

// Every run of separators is one hyphen by now, so each edge holds at most one.
const withoutEdgeHyphens = (text: string): string => {
  const start: number = text.startsWith(HYPHEN) ? 1 : 0
  const end: number = text.endsWith(HYPHEN) ? text.length - 1 : text.length
  return start < end ? text.slice(start, end) : ''
}

/**
 * The slug of a title: accents dropped, every run of other characters a single hyphen, cut to the longest slug.
 * @param title the title, such as `Drafts & previews`.
 * @returns the slug, such as `drafts-previews`, or an empty text when the title holds no letter or digit.
 */
export const slugify = (title: string): string =>
  withoutEdgeHyphens(
    title
      .normalize('NFKD')
      .replaceAll(DIACRITICS, '')
      .toLowerCase()
      .replaceAll(SEPARATORS, '-')
      .slice(0, SLUG_MAX_LENGTH),
  )
