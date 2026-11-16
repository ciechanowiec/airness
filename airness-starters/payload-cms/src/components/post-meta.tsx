import { CalendarDays, Clock } from 'lucide-react'

import { formatPublicationDate } from '@/lib/dates'

/** What the line under a post's title states. */
export interface PostMetaProperties {
  /** When the post was published, or undefined for a draft that never was. */
  readonly publishedAt?: string | undefined
  readonly readingMinutes: number
}

/**
 * When a post was published and how long it takes to read.
 * @param properties the publication date and the reading time.
 * @returns the line.
 */
export function PostMeta(properties: Readonly<PostMetaProperties>) {
  const { publishedAt, readingMinutes } = properties

  return (
    <p className="flex flex-wrap items-center gap-x-4 gap-y-1 text-base-content/70 text-sm">
      {publishedAt === undefined ? (
        <span>Not published yet</span>
      ) : (
        <span className="inline-flex items-center gap-1.5">
          <CalendarDays aria-hidden="true" className="size-4" />
          <time dateTime={publishedAt}>{formatPublicationDate(publishedAt)}</time>
        </span>
      )}
      <span className="inline-flex items-center gap-1.5">
        <Clock aria-hidden="true" className="size-4" />
        {`${String(readingMinutes)} min read`}
      </span>
    </p>
  )
}
