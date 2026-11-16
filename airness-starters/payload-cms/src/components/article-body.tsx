import type { SerializedLinkNode } from '@payloadcms/richtext-lexical'
import {
  type JSXConverters,
  type JSXConvertersFunction,
  LinkJSXConverter,
  RichText,
} from '@payloadcms/richtext-lexical/react'

import type { PostArticle } from '@/lib/views'

/** What an article body renders. */
export interface ArticleBodyProperties {
  /** The rich text of a post, as Payload stores it. */
  readonly content: PostArticle['content']
}

const HOME: string = '/'
const SECTION_OF: Readonly<Record<string, string>> = { posts: '/posts', categories: '/categories' }

const slugOf = (doc: unknown): string | undefined =>
  typeof doc === 'object' && doc !== null && 'slug' in doc && typeof doc.slug === 'string' ? doc.slug : undefined

/**
 * The address of a post or a category an editor linked from the editor's link dialog, by the slug it is read at.
 * @param args the link node, whose `doc` names the linked document.
 * @param args.linkNode the link node.
 * @returns the address on this site, or the home page for a document the site has no page for.
 */
const internalDocToHref = ({ linkNode }: { readonly linkNode: SerializedLinkNode }): string => {
  const section: string | undefined = SECTION_OF[linkNode.fields.doc?.relationTo ?? '']
  const slug: string | undefined = slugOf(linkNode.fields.doc?.value)
  return section === undefined || slug === undefined ? HOME : `${section}/${encodeURIComponent(slug)}`
}

const converters: JSXConvertersFunction = ({ defaultConverters }): JSXConverters => ({
  ...defaultConverters,
  ...LinkJSXConverter({ internalDocToHref }),
})

/**
 * The rich text of a post, set in the serif typeface at a reading measure, its colours taken from the theme.
 * @param properties the rich text.
 * @returns the body.
 */
export function ArticleBody(properties: Readonly<ArticleBodyProperties>) {
  const { content } = properties

  return <RichText className="article-body mx-auto max-w-measure" converters={converters} data={content} />
}
