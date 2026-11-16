import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { ArticleBody } from '@/components/article-body'
import type { PostArticle } from '@/lib/views'

type Content = PostArticle['content']
type Node = Content['root']['children'][number]

const text = (value: string): Node => ({
  type: 'text',
  version: 1,
  text: value,
  format: 0,
  detail: 0,
  mode: 'normal',
  style: '',
})

const linkTo = (doc: Readonly<Record<string, unknown>>, label: string): Node => ({
  type: 'link',
  version: 3,
  children: [text(label)],
  direction: 'ltr',
  format: '',
  indent: 0,
  fields: { linkType: 'internal', newTab: false, doc },
  id: label,
})

const contentOf = (...children: readonly Node[]): Content => ({
  root: {
    type: 'root',
    version: 1,
    direction: 'ltr',
    format: '',
    indent: 0,
    children: [
      {
        type: 'heading',
        tag: 'h2',
        version: 1,
        children: [text('A section')],
        direction: 'ltr',
        format: '',
        indent: 0,
      },
      { type: 'paragraph', version: 1, children: [...children], direction: 'ltr', format: '', indent: 0 },
    ],
  },
})

describe('ArticleBody', () => {
  it('renders the headings and paragraphs of the rich text', () => {
    render(<ArticleBody content={contentOf(text('Some words.'))} />)
    expect(screen.getByRole('heading', { level: 2 }).textContent).toBe('A section')
    expect(screen.getByText('Some words.')).toBeInstanceOf(HTMLElement)
  })

  it('links a linked post at its own page', () => {
    render(<ArticleBody content={contentOf(linkTo({ relationTo: 'posts', value: { slug: 'my post' } }, 'a post'))} />)
    expect(screen.getByRole('link', { name: 'a post' }).getAttribute('href')).toBe('/posts/my%20post')
  })

  it('links a linked category at its own page', () => {
    render(
      <ArticleBody content={contentOf(linkTo({ relationTo: 'categories', value: { slug: 'guides' } }, 'guides'))} />,
    )
    expect(screen.getByRole('link', { name: 'guides' }).getAttribute('href')).toBe('/categories/guides')
  })

  it.each([
    ['a document the site has no page for', { relationTo: 'media', value: { slug: 'x' } }],
    ['a document the read did not populate', { relationTo: 'posts', value: 4 }],
    ['a document without a slug', { relationTo: 'posts', value: { title: 'No slug' } }],
  ])('links %s at the home page', (_case: string, doc: Readonly<Record<string, unknown>>) => {
    render(<ArticleBody content={contentOf(linkTo(doc, 'elsewhere'))} />)
    expect(screen.getByRole('link', { name: 'elsewhere' }).getAttribute('href')).toBe('/')
  })
})
