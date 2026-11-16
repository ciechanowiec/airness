/** What the top of a list page says. */
export interface PageIntroProperties {
  /** The small line above the heading, such as `Category`. */
  readonly eyebrow: string
  readonly title: string
  /** A sentence under the heading, or an empty text to show none. */
  readonly description: string
}

/**
 * The top of a list page: its heading and a sentence about what it lists.
 * @param properties the eyebrow, the title and the description.
 * @returns the introduction.
 */
export function PageIntro(properties: Readonly<PageIntroProperties>) {
  const { eyebrow, title, description } = properties

  return (
    <div className="flex flex-col gap-3 py-10 md:py-14">
      <p className="font-semibold text-primary text-sm uppercase tracking-widest">{eyebrow}</p>
      <h1 className="text-balance font-semibold font-serif text-4xl leading-tight md:text-5xl">{title}</h1>
      {description === '' ? null : <p className="max-w-measure text-base-content/70 text-lg">{description}</p>}
    </div>
  )
}
