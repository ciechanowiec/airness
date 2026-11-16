import { ArrowRight } from 'lucide-react'
import Link from 'next/link'

/**
 * What a reader sees at an address the site has no page for, with the two ways back into it.
 * @returns the message.
 */
export function NotFoundMessage() {
  return (
    <div className="flex flex-col items-start gap-6 py-16 md:py-24">
      <p className="font-semibold text-primary text-sm uppercase tracking-widest">Page not found</p>
      <h1 className="max-w-2xl text-balance font-semibold font-serif text-4xl leading-tight md:text-5xl">
        This page has moved, or never existed.
      </h1>
      <p className="max-w-measure text-base-content/70 text-lg">
        Check the address, or carry on from the home page or the list of every post.
      </p>
      <div className="flex flex-wrap gap-3">
        <Link className="btn btn-primary min-h-11" href="/">
          Go to the home page
        </Link>
        <Link className="btn btn-ghost min-h-11 gap-2" href="/posts">
          See all posts
          <ArrowRight aria-hidden="true" className="size-4" />
        </Link>
      </div>
    </div>
  )
}
