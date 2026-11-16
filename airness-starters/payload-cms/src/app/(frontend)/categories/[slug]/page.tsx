import type { Metadata } from 'next'
import { notFound } from 'next/navigation'

import { PageIntro } from '@/components/page-intro'
import { Pagination } from '@/components/pagination'
import { PostGrid } from '@/components/post-grid'
import { pageNumberFrom } from '@/lib/pagination'
import type { CategorySummary } from '@/lib/views'
import { findCategory } from '@/queries/categories'
import { findPublishedPosts, type PostPage } from '@/queries/posts'

interface CategoryPageProperties {
  readonly params: Promise<{ readonly slug: string }>
  readonly searchParams: Promise<Readonly<Record<string, string | string[] | undefined>>>
}

export async function generateMetadata(properties: Readonly<CategoryPageProperties>): Promise<Metadata> {
  const { slug } = await properties.params
  const category: CategorySummary | undefined = await findCategory(slug)
  return category === undefined
    ? {}
    : { title: category.title, ...(category.description !== '' && { description: category.description }) }
}

export default async function CategoryPage(properties: Readonly<CategoryPageProperties>) {
  const { slug } = await properties.params
  const parameters: Readonly<Record<string, string | string[] | undefined>> = await properties.searchParams
  const page: number = pageNumberFrom(parameters['page'])
  const category: CategorySummary | undefined = await findCategory(slug)
  if (category === undefined) {
    notFound()
  }
  const result: PostPage = await findPublishedPosts({ page, categorySlug: category.slug })
  if (page > result.totalPages) {
    notFound()
  }

  return (
    <>
      <PageIntro description={category.description} eyebrow="Category" title={category.title} />
      <PostGrid
        emptyMessage="No post in this category has been published yet."
        headingLevel="h2"
        posts={result.posts}
      />
      <Pagination basePath={category.href} current={page} total={result.totalPages} />
    </>
  )
}
