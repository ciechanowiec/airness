import { describe, expect, it } from 'vitest'

import { type PageLinks, pageLinks, pageNumberFrom } from '@/lib/pagination'

describe('pageNumberFrom', () => {
  it('reads a positive whole number', () => {
    expect(pageNumberFrom('3')).toBe(3)
  })

  it.each([
    ['a missing parameter', undefined],
    ['zero', '0'],
    ['a negative number', '-2'],
    ['a fraction', '1.5'],
    ['text', 'two'],
    ['a leading zero', '02'],
    ['a number too large to be a page', '10000000'],
  ])('asks for the first page given %s', (_case: string, parameter: string | undefined) => {
    expect(pageNumberFrom(parameter)).toBe(1)
  })

  it('asks for the first page when the parameter is repeated', () => {
    expect(pageNumberFrom(['2', '3'])).toBe(1)
  })
})

describe('page addresses', () => {
  it('gives the first page the list address alone', () => {
    expect(pageLinks('/posts', 2, 2).previous?.href).toBe('/posts')
  })

  it('names any later page in the query', () => {
    expect(pageLinks('/categories/guides', 3, 4).next?.href).toBe('/categories/guides?page=4')
  })
})

describe('pageLinks', () => {
  it('links every page by number', () => {
    expect(pageLinks('/posts', 1, 3).pages.map((page) => page.href)).toEqual([
      '/posts',
      '/posts?page=2',
      '/posts?page=3',
    ])
  })

  it('links only the next page from the first one', () => {
    const links: PageLinks = pageLinks('/posts', 1, 3)
    expect(links.previous).toBeUndefined()
    expect(links.next).toEqual({ number: 2, href: '/posts?page=2' })
  })

  it('links both neighbours from a middle page', () => {
    const links: PageLinks = pageLinks('/posts', 2, 3)
    expect(links.previous).toEqual({ number: 1, href: '/posts' })
    expect(links.next).toEqual({ number: 3, href: '/posts?page=3' })
  })

  it('links only the previous page from the last one', () => {
    const links: PageLinks = pageLinks('/posts', 3, 3)
    expect(links.previous).toEqual({ number: 2, href: '/posts?page=2' })
    expect(links.next).toBeUndefined()
  })
})
