import type { Metadata } from 'next'
import { notFound } from 'next/navigation'

import { ArticleBody } from '@/components/article-body'
import { ArticleHeader } from '@/components/article-header'
import type { PostArticle } from '@/lib/views'
import { findPublishedPost } from '@/queries/posts'

interface PostPageProperties {
  readonly params: Promise<{ readonly slug: string }>
}

export async function generateMetadata(properties: Readonly<PostPageProperties>): Promise<Metadata> {
  const { slug } = await properties.params
  const article: PostArticle | undefined = await findPublishedPost(slug)
  if (article === undefined) {
    return {}
  }
  const { summary } = article
  return {
    title: summary.title,
    description: summary.excerpt,
    openGraph: {
      type: 'article',
      title: summary.title,
      description: summary.excerpt,
      ...(summary.publishedAt !== undefined && { publishedTime: summary.publishedAt }),
      ...(summary.cover !== undefined && { images: [{ url: summary.cover.url, alt: summary.cover.alt }] }),
    },
  }
}

export default async function PostPage(properties: Readonly<PostPageProperties>) {
  const { slug } = await properties.params
  const article: PostArticle | undefined = await findPublishedPost(slug)
  if (article === undefined) {
    notFound()
  }

  return (
    <article className="py-10 md:py-14">
      <ArticleHeader post={article.summary} />
      <div className="mt-12">
        <ArticleBody content={article.content} />
      </div>
    </article>
  )
}
