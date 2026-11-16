import type { PostSummary } from '@/lib/views'
import { EmptyState } from './empty-state'
import { PostCard } from './post-card'

/** What a grid of posts shows. */
export interface PostGridProperties {
  readonly posts: readonly PostSummary[]
  /** What the grid says when it holds no post. */
  readonly emptyMessage: string
  /** The heading level of each card, under the page's own headings. */
  readonly headingLevel?: 'h2' | 'h3'
}

/**
 * Posts as cards in a grid, one column on a phone and three on a wide screen.
 * @param properties the posts, the message for an empty grid, and the cards' heading level.
 * @returns the grid, or an empty state when there is no post.
 */
export function PostGrid(properties: Readonly<PostGridProperties>) {
  const { posts, emptyMessage, headingLevel = 'h3' } = properties

  if (posts.length === 0) {
    return <EmptyState message={emptyMessage} title="Nothing here yet" />
  }
  return (
    <ul className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
      {posts.map((post: PostSummary) => (
        <li className="flex" key={post.slug}>
          <PostCard headingLevel={headingLevel} post={post} />
        </li>
      ))}
    </ul>
  )
}
