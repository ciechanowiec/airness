import { CategoryList } from '@/components/category-list'
import { EmptyState } from '@/components/empty-state'
import { Hero } from '@/components/hero'
import { PostCard } from '@/components/post-card'
import { PostGrid } from '@/components/post-grid'
import { SITE_NAME } from '@/lib/site'
import type { CategorySummary, PostSummary, SiteIdentity } from '@/lib/views'
import { findCategories } from '@/queries/categories'
import { findLeadingPosts } from '@/queries/posts'
import { findSiteIdentity } from '@/queries/site'

/** The lead post and the three newest after it, which fill one row of the grid. */
const LEADING_POSTS: number = 4

const SECTION_HEADING: string = 'mb-6 font-semibold font-serif text-3xl'

export default async function HomePage() {
  const [site, posts, categories]: readonly [SiteIdentity, readonly PostSummary[], readonly CategorySummary[]] =
    await Promise.all([findSiteIdentity(), findLeadingPosts(LEADING_POSTS), findCategories()])
  const [lead, ...latest]: readonly PostSummary[] = posts

  return (
    <>
      <Hero description={site.description} eyebrow={SITE_NAME} tagline={site.tagline} />
      {lead === undefined ? (
        <EmptyState message="Sign in to the admin panel and publish a post to see it here." title="No posts yet" />
      ) : (
        <PostCard featured={true} headingLevel="h2" post={lead} />
      )}
      {latest.length === 0 ? null : (
        <section className="mt-16">
          <h2 className={SECTION_HEADING}>Latest posts</h2>
          <PostGrid emptyMessage="" posts={latest} />
        </section>
      )}
      {categories.length === 0 ? null : (
        <section className="mt-16">
          <h2 className={SECTION_HEADING}>Browse by category</h2>
          <CategoryList categories={categories} />
        </section>
      )}
    </>
  )
}
