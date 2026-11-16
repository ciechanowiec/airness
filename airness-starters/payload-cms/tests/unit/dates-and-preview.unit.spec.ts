import { describe, expect, it } from 'vitest'

import { formatPublicationDate } from '@/lib/dates'
import { postLivePreviewPath, postPreviewPath } from '@/lib/preview'

describe('formatPublicationDate', () => {
  it('writes the day, the month by name and the year', () => {
    expect(formatPublicationDate('2026-03-04T09:00:00.000Z')).toBe('4 March 2026')
  })

  it('reads the date in UTC, whatever the server zone', () => {
    expect(formatPublicationDate('2026-03-04T23:30:00.000-05:00')).toBe('5 March 2026')
  })
})

describe('postPreviewPath', () => {
  it('previews a post at its slug', () => {
    expect(postPreviewPath({ slug: 'welcome' })).toBe('/preview/posts/welcome')
  })

  it('encodes a slug an editor has not saved as a slug yet', () => {
    expect(postPreviewPath({ slug: 'a b' })).toBe('/preview/posts/a%20b')
  })

  it('falls back to the preview root for a post without a slug', () => {
    expect(postPreviewPath({ title: 'Untitled' })).toBe('/preview/posts')
  })
})

describe('postLivePreviewPath', () => {
  it('shows the post being edited beside the editor at its preview address', () => {
    expect(postLivePreviewPath({ data: { slug: 'welcome' } })).toBe('/preview/posts/welcome')
  })
})
