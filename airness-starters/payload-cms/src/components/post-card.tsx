import Image from 'next/image'
import Link from 'next/link'

import type { CoverImage, PostSummary } from '@/lib/views'
import { CategoryChip } from './category-chip'
import { PostMeta } from './post-meta'

/** What a post card shows, and how prominently. */
export interface PostCardProperties {
  readonly post: PostSummary
  /** A featured card spans the width of the page, its image beside its text from a tablet up. */
  readonly featured?: boolean
  /** The heading level that fits the card under the page's own headings. */
  readonly headingLevel?: 'h2' | 'h3'
}

/** The classes and image sizes of one way of laying a card out. */
interface CardLayout {
  readonly card: string
  readonly cover: string
  readonly body: string
  readonly title: string
  /** The width the image takes at each breakpoint, so the browser downloads no larger image than it shows. */
  readonly sizes: string
}

const STANDARD: CardLayout = {
  card: '',
  cover: 'rounded-t-box',
  body: '',
  title: 'text-2xl',
  sizes: '(min-width: 1024px) 22rem, (min-width: 640px) 45vw, 100vw',
}

const FEATURED: CardLayout = {
  card: 'md:flex-row',
  cover: 'rounded-t-box md:w-3/5 md:shrink-0 md:rounded-tr-none md:rounded-l-box',
  body: 'md:justify-center md:p-8',
  title: 'text-3xl md:text-4xl',
  sizes: '(min-width: 1024px) 40rem, 100vw',
}

function Cover(properties: Readonly<{ cover: CoverImage | undefined; layout: CardLayout }>) {
  const { cover, layout } = properties

  return (
    <div className={`aspect-video overflow-hidden bg-base-200 ${layout.cover}`}>
      {cover === undefined ? null : (
        <Image
          alt={cover.alt}
          className="size-full object-cover transition-transform duration-500 group-hover:scale-105"
          height={cover.height}
          sizes={layout.sizes}
          src={cover.url}
          width={cover.width}
        />
      )}
    </div>
  )
}

/**
 * A post as a list shows it: cover, category, title, excerpt and the line under it. The whole card is one link to
 * the post, so a reader tabs to each post once, and its focus ring follows the card's own rounded edge.
 * @param properties the post, whether it is featured, and its heading level.
 * @returns the card.
 */
export function PostCard(properties: Readonly<PostCardProperties>) {
  const { post, featured = false, headingLevel: Heading = 'h3' } = properties
  const layout: CardLayout = featured ? FEATURED : STANDARD

  return (
    <article className="flex w-full">
      <Link
        className={`group flex w-full flex-col rounded-box border border-base-300 bg-base-100 transition-shadow hover:shadow-lg ${layout.card}`}
        href={post.href}
      >
        <Cover cover={post.cover} layout={layout} />
        <div className={`flex flex-1 flex-col gap-3 p-5 ${layout.body}`}>
          {post.category === undefined ? null : (
            <div>
              <CategoryChip category={post.category} linked={false} />
            </div>
          )}
          <Heading
            className={`text-balance font-semibold font-serif leading-tight group-hover:text-primary ${layout.title}`}
          >
            {post.title}
          </Heading>
          <p className="max-w-measure text-base-content/70">{post.excerpt}</p>
          <div className="mt-auto pt-2">
            <PostMeta publishedAt={post.publishedAt} readingMinutes={post.readingMinutes} />
          </div>
        </div>
      </Link>
    </article>
  )
}
