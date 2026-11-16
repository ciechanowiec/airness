import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { NavigationList } from '@/components/navigation-list'
import { SiteFooter } from '@/components/site-footer'
import { SiteHeader } from '@/components/site-header'
import { NAVIGATION } from './fixtures'

describe('NavigationList', () => {
  it('marks the link to the page shown as the current page', () => {
    render(<NavigationList currentPath="/categories/guides" label="Main" links={NAVIGATION} />)
    expect(screen.getByRole('link', { name: 'Guides' }).getAttribute('aria-current')).toBe('page')
  })

  it('marks no other link as current', () => {
    render(<NavigationList currentPath="/categories/guides" label="Main" links={NAVIGATION} />)
    expect(screen.getByRole('link', { name: 'All posts' }).hasAttribute('aria-current')).toBe(false)
  })

  it('names its landmark so two navigations of one page are told apart', () => {
    render(<NavigationList currentPath="/" label="Footer" links={NAVIGATION} />)
    expect(within(screen.getByRole('navigation', { name: 'Footer' })).getAllByRole('link')).toHaveLength(2)
  })
})

describe('SiteHeader', () => {
  it('links the home page from the site name', () => {
    render(<SiteHeader navigation={NAVIGATION} siteName="My Site" />)
    expect(screen.getByRole('link', { name: 'My Site' }).getAttribute('href')).toBe('/')
  })

  it('carries the main navigation', () => {
    render(<SiteHeader navigation={NAVIGATION} siteName="My Site" />)
    expect(
      within(screen.getByRole('navigation', { name: 'Main' }))
        .getAllByRole('link')
        .map((link) => link.getAttribute('href')),
    ).toEqual(['/posts', '/categories/guides'])
  })
})

describe('SiteFooter', () => {
  it('shows the editors note beside the site name', () => {
    render(<SiteFooter note="Made with care." siteName="My Site" />)
    expect(screen.getByRole('contentinfo').textContent).toBe('My SiteMade with care.')
  })

  it('shows no note when the settings leave it empty', () => {
    render(<SiteFooter note="" siteName="My Site" />)
    expect(within(screen.getByRole('contentinfo')).queryAllByRole('paragraph')).toHaveLength(0)
  })

  it('repeats no navigation, which the header carries', () => {
    render(<SiteFooter note="" siteName="My Site" />)
    expect(screen.queryByRole('navigation')).toBeNull()
  })
})
