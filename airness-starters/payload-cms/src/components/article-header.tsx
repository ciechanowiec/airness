import Image from 'next/image'

import type { PostSummary } from '@/lib/views'
import { CategoryChip } from './category-chip'
import { PostMeta } from './post-meta'

/** What an article's header shows. */
export interface ArticleHeaderProperties {
  readonly post: PostSummary
}

const COVER_SIZES: string = '(min-width: 1152px) 72rem, 100vw'

/**
 * The top of a post's page: its category, title, excerpt, the line under them and the cover. The cover is the
 * largest thing the page paints first, so it loads at once rather than when it scrolls into view.
 * @param properties the post.
 * @returns the header.
 */
export function ArticleHeader(properties: Readonly<ArticleHeaderProperties>) {
  const { post } = properties

  return (
    <header className="flex flex-col gap-8">
      <div className="mx-auto flex max-w-3xl flex-col items-center gap-5 text-center">
        {post.category === undefined ? null : <CategoryChip category={post.category} />}
        <h1 className="text-balance font-semibold font-serif text-4xl leading-tight md:text-5xl">{post.title}</h1>
        <p className="max-w-measure text-base-content/70 text-lg">{post.excerpt}</p>
        <PostMeta publishedAt={post.publishedAt} readingMinutes={post.readingMinutes} />
      </div>
      {post.cover === undefined ? null : (
        <Image
          alt={post.cover.alt}
          className="aspect-video w-full rounded-box object-cover"
          fetchPriority="high"
          height={post.cover.height}
          loading="eager"
          sizes={COVER_SIZES}
          src={post.cover.url}
          width={post.cover.width}
        />
      )}
    </header>
  )
}
