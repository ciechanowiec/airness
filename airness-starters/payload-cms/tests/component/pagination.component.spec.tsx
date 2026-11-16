import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { Pagination } from '@/components/pagination'

describe('Pagination', () => {
  it('draws nothing for a list of one page', () => {
    render(<Pagination basePath="/posts" current={1} total={1} />)
    expect(screen.queryByRole('navigation')).toBeNull()
  })

  it('marks the page shown rather than linking it', () => {
    render(<Pagination basePath="/posts" current={2} total={3} />)
    expect(screen.queryByRole('link', { name: 'Page 2' })).toBeNull()
    expect(screen.getByText('Page 2').getAttribute('aria-current')).toBe('page')
  })

  it('links every other page by number', () => {
    render(<Pagination basePath="/posts" current={2} total={3} />)
    expect(screen.getByRole('link', { name: 'Page 1' }).getAttribute('href')).toBe('/posts')
    expect(screen.getByRole('link', { name: 'Page 3' }).getAttribute('href')).toBe('/posts?page=3')
  })

  it('links the previous and the next page from a middle page', () => {
    render(<Pagination basePath="/categories/guides" current={2} total={3} />)
    expect(screen.getByRole('link', { name: 'Previous page' }).getAttribute('href')).toBe('/categories/guides')
    expect(screen.getByRole('link', { name: 'Next page' }).getAttribute('href')).toBe('/categories/guides?page=3')
  })

  it('offers no previous page from the first page', () => {
    render(<Pagination basePath="/posts" current={1} total={2} />)
    expect(screen.queryByRole('link', { name: 'Previous page' })).toBeNull()
  })

  it('offers no next page from the last page', () => {
    render(<Pagination basePath="/posts" current={2} total={2} />)
    expect(screen.queryByRole('link', { name: 'Next page' })).toBeNull()
  })
})
