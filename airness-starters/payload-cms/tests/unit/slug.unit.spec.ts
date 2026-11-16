import { describe, expect, it } from 'vitest'

import { SLUG_MAX_LENGTH, slugify, validateSlug } from '@/lib/slug'

describe('slugify', () => {
  it('lowercases a title and joins its words with single hyphens', () => {
    expect(slugify('Writing  and Publishing')).toBe('writing-and-publishing')
  })

  it('drops accents rather than the letters that carry them', () => {
    expect(slugify('Crème brûlée à Łódź')).toBe('creme-brulee-a-odz')
  })

  it('turns every run of punctuation into one hyphen and trims the edges', () => {
    expect(slugify('  Drafts & previews: a guide!  ')).toBe('drafts-previews-a-guide')
  })

  it('cuts a long title to the longest slug, without a trailing hyphen', () => {
    const slug: string = slugify(`${'a'.repeat(SLUG_MAX_LENGTH - 1)} b`)
    expect(slug).toBe('a'.repeat(SLUG_MAX_LENGTH - 1))
  })

  it('makes an empty slug of a title with no letter or digit', () => {
    expect(slugify('!!! ---')).toBe('')
  })
})

describe('validateSlug', () => {
  it('accepts lowercase words joined by single hyphens', () => {
    expect(validateSlug('post-2026-recap')).toBe(true)
  })

  it.each([
    ['an uppercase letter', 'Post'],
    ['a leading hyphen', '-post'],
    ['a trailing hyphen', 'post-'],
    ['a double hyphen', 'my--post'],
    ['a space', 'my post'],
    ['a slash', 'my/post'],
    ['nothing', ''],
  ])('refuses %s', (_case: string, value: string) => {
    expect(validateSlug(value)).not.toBe(true)
  })

  it('refuses a slug longer than the longest it stores', () => {
    expect(validateSlug('a'.repeat(SLUG_MAX_LENGTH + 1))).not.toBe(true)
  })

  it('refuses a value that is not text', () => {
    expect(validateSlug(42)).not.toBe(true)
  })
})

describe('the slug message', () => {
  it('tells the editor what a slug may hold', () => {
    expect(validateSlug('Hello World')).toContain('lowercase letters, digits and single hyphens')
  })
})
