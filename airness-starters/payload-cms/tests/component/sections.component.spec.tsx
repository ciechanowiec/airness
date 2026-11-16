import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { ArticleHeader } from '@/components/article-header'
import { CategoryList } from '@/components/category-list'
import { EmptyState } from '@/components/empty-state'
import { Hero } from '@/components/hero'
import { NotFoundMessage } from '@/components/not-found-message'
import { PageIntro } from '@/components/page-intro'
import { BARE_POST, GUIDES, POST } from './fixtures'

describe('Hero', () => {
  it('heads the page with the tagline and leads into every post', () => {
    render(<Hero description="About the site." eyebrow="My Site" tagline="Notes and guides" />)
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('Notes and guides')
    expect(screen.getByRole('link', { name: 'Browse all posts' }).getAttribute('href')).toBe('/posts')
  })
})

describe('PageIntro', () => {
  it('heads the page with its title and says what it lists', () => {
    render(<PageIntro description="Help, step by step." eyebrow="Category" title="Guides" />)
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('Guides')
    expect(screen.getByText('Help, step by step.')).toBeInstanceOf(HTMLParagraphElement)
  })

  it('shows no description when there is none', () => {
    render(<PageIntro description="" eyebrow="Archive" title="All posts" />)
    expect(screen.getAllByRole('paragraph').map((paragraph) => paragraph.textContent)).toEqual(['Archive'])
  })
})

describe('ArticleHeader', () => {
  it('heads the article with the title and shows the cover', () => {
    render(<ArticleHeader post={POST} />)
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe(POST.title)
    expect(screen.getByRole('img', { name: 'A typewriter' }).getAttribute('loading')).toBe('eager')
  })

  it('draws a draft without cover or category with neither', () => {
    render(<ArticleHeader post={BARE_POST} />)
    expect(screen.queryByRole('img')).toBeNull()
    expect(screen.queryByRole('link')).toBeNull()
  })
})

describe('CategoryList', () => {
  it('links each category with its description', () => {
    render(
      <CategoryList
        categories={[GUIDES, { ...GUIDES, title: 'Design', slug: 'design', href: '/d', description: '' }]}
      />,
    )
    const links: readonly HTMLElement[] = within(screen.getByRole('list')).getAllByRole('link')
    expect(links.map((link) => [link.getAttribute('href'), link.textContent])).toEqual([
      ['/categories/guides', 'GuidesStep-by-step help.'],
      ['/d', 'Design'],
    ])
  })

  it('draws nothing when there is no category', () => {
    render(<CategoryList categories={[]} />)
    expect(screen.queryByRole('list')).toBeNull()
  })
})

describe('EmptyState', () => {
  it('says what is missing and why', () => {
    render(<EmptyState message="Publish a post to see it here." title="No posts yet" />)
    expect(screen.getByRole('heading', { name: 'No posts yet' }).nextElementSibling?.textContent).toBe(
      'Publish a post to see it here.',
    )
  })
})

describe('NotFoundMessage', () => {
  it('leads back to the home page and to every post', () => {
    render(<NotFoundMessage />)
    expect(screen.getAllByRole('link').map((link) => link.getAttribute('href'))).toEqual(['/', '/posts'])
  })
})
