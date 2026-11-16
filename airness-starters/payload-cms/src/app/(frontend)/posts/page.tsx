import type { Metadata } from 'next'
import { notFound } from 'next/navigation'

import { PageIntro } from '@/components/page-intro'
import { Pagination } from '@/components/pagination'
import { PostGrid } from '@/components/post-grid'
import { pageNumberFrom } from '@/lib/pagination'
import { findPublishedPosts, type PostPage } from '@/queries/posts'

const BASE_PATH: string = '/posts'

export const metadata: Metadata = {
  title: 'All posts',
  description: 'Every published post, newest first.',
}

interface PostsPageProperties {
  readonly searchParams: Promise<Readonly<Record<string, string | string[] | undefined>>>
}

export default async function PostsPage(properties: Readonly<PostsPageProperties>) {
  const { searchParams } = properties
  const parameters: Readonly<Record<string, string | string[] | undefined>> = await searchParams
  const page: number = pageNumberFrom(parameters['page'])
  const result: PostPage = await findPublishedPosts({ page })
  if (page > result.totalPages) {
    notFound()
  }

  return (
    <>
      <PageIntro description="Every published post, newest first." eyebrow="Archive" title="All posts" />
      <PostGrid
        emptyMessage="Nothing has been published yet. Published posts appear here, newest first."
        headingLevel="h2"
        posts={result.posts}
      />
      <Pagination basePath={BASE_PATH} current={page} total={result.totalPages} />
    </>
  )
}
