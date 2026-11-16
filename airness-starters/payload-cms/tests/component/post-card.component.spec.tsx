import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { PostCard } from '@/components/post-card'
import { PostGrid } from '@/components/post-grid'
import { BARE_POST, POST } from './fixtures'

describe('PostCard', () => {
  it('links the post from the whole card, once', () => {
    render(<PostCard post={POST} />)
    expect(screen.getAllByRole('link').map((link) => link.getAttribute('href'))).toEqual([POST.href])
  })

  it('names the category of the post without a link of its own', () => {
    render(<PostCard post={POST} />)
    expect(screen.getByText('Guides').tagName).toBe('SPAN')
  })

  it('shows the cover with its alternative text', () => {
    render(<PostCard post={POST} />)
    expect(screen.getByRole('img', { name: 'A typewriter' })).toBeInstanceOf(HTMLImageElement)
  })

  it('states the publication date and the reading time', () => {
    render(<PostCard post={POST} />)
    expect(screen.getByText('4 March 2026').getAttribute('datetime')).toBe(POST.publishedAt)
    expect(screen.getByText('4 min read')).toBeInstanceOf(HTMLElement)
  })

  it('draws a post without cover or category with neither', () => {
    render(<PostCard post={BARE_POST} />)
    expect(screen.queryByRole('img')).toBeNull()
    expect(screen.queryByText('Guides')).toBeNull()
  })

  it('says a post without a publication date is not published yet', () => {
    render(<PostCard post={BARE_POST} />)
    expect(screen.getByText('Not published yet')).toBeInstanceOf(HTMLElement)
  })

  it('heads a card at the level it is given', () => {
    render(<PostCard featured={true} headingLevel="h2" post={POST} />)
    expect(screen.getByRole('heading', { level: 2 }).textContent).toBe(POST.title)
  })

  it('lays a featured card out side by side from a tablet up', () => {
    render(<PostCard featured={true} post={POST} />)
    expect(screen.getByRole('link').className).toContain('md:flex-row')
  })
})

describe('PostGrid', () => {
  it('lists one card for each post', () => {
    render(<PostGrid emptyMessage="None" posts={[POST, BARE_POST]} />)
    const items: readonly HTMLElement[] = within(screen.getByRole('list')).getAllByRole('listitem')
    expect(items.map((item) => within(item).getByRole('heading').textContent)).toEqual([POST.title, BARE_POST.title])
  })

  it('says why it is empty when it holds no post', () => {
    render(<PostGrid emptyMessage="Nothing has been published yet." posts={[]} />)
    expect(screen.queryByRole('list')).toBeNull()
    expect(screen.getByText('Nothing has been published yet.')).toBeInstanceOf(HTMLElement)
  })
})
