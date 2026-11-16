import { ArrowRight } from 'lucide-react'
import Link from 'next/link'

/** What the hero says. */
export interface HeroProperties {
  /** The small line above the heading. */
  readonly eyebrow: string
  readonly tagline: string
  readonly description: string
}

/**
 * The opening of the home page: the site's tagline as the page's heading, the description, and a way into the
 * list of every post.
 * @param properties the eyebrow, the tagline and the description.
 * @returns the hero.
 */
export function Hero(properties: Readonly<HeroProperties>) {
  const { eyebrow, tagline, description } = properties

  return (
    <section className="flex flex-col items-start gap-6 py-12 md:py-20">
      <p className="font-semibold text-primary text-sm uppercase tracking-widest">{eyebrow}</p>
      <h1 className="max-w-3xl text-balance font-semibold font-serif text-4xl leading-tight md:text-6xl">{tagline}</h1>
      <p className="max-w-measure text-base-content/70 text-lg">{description}</p>
      <Link className="btn btn-primary min-h-11 gap-2" href="/posts">
        Browse all posts
        <ArrowRight aria-hidden="true" className="size-4" />
      </Link>
    </section>
  )
}
