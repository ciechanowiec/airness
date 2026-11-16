import { Eye } from 'lucide-react'
import type { Metadata } from 'next'
import { headers } from 'next/headers'
import { notFound } from 'next/navigation'
import type { TypedUser } from 'payload'

import { ArticleBody } from '@/components/article-body'
import { ArticleHeader } from '@/components/article-header'
import { LivePreviewRefresh } from '@/components/live-preview-refresh'
import { loadServerEnvironment } from '@/lib/environment'
import type { PostArticle } from '@/lib/views'
import { findPostDraft } from '@/queries/posts'
import { findSignedInUser } from '@/queries/session'

export const metadata: Metadata = {
  title: 'Preview',
  robots: { index: false, follow: false },
}

interface PostPreviewPageProperties {
  readonly params: Promise<{ readonly slug: string }>
}

// The admin panel shows this page beside the editor as its Live Preview, and its Preview button opens it in a new
// tab. It reads the post as the signed-in user may read it, so an editor sees the newest draft and anyone else sees
// at most what is published; an anonymous request and a post the user may not read both answer 404, which says
// nothing about whether a draft exists. It is the one page a frame may show, and only on this origin.
export default async function PostPreviewPage(properties: Readonly<PostPreviewPageProperties>) {
  const { slug } = await properties.params
  const user: TypedUser | undefined = await findSignedInUser(await headers())
  const article: PostArticle | undefined = user === undefined ? undefined : await findPostDraft(slug, user)
  if (article === undefined) {
    notFound()
  }

  return (
    <article className="py-10 md:py-14">
      <LivePreviewRefresh serverUrl={loadServerEnvironment().serverUrl} />
      <p className="alert mb-10 max-w-measure border-base-300 bg-base-200" role="status">
        <Eye aria-hidden="true" className="size-5 text-primary" />
        This is a preview of the newest draft. Visitors see the post only once it is published.
      </p>
      <ArticleHeader post={article.summary} />
      <div className="mt-12">
        <ArticleBody content={article.content} />
      </div>
    </article>
  )
}
