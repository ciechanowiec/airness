/** What the footer shows. */
export interface SiteFooterProperties {
  readonly siteName: string
  /** A sentence about the site, or an empty text to show none. */
  readonly note: string
}

/**
 * The band at the bottom of every page: the site's name and the editors' note. The navigation stays in the header.
 * @param properties the site's name and the note.
 * @returns the footer.
 */
export function SiteFooter(properties: Readonly<SiteFooterProperties>) {
  const { siteName, note } = properties

  return (
    <footer className="mt-24 border-base-300 border-t bg-base-200">
      <div className="mx-auto flex max-w-6xl flex-col gap-2 px-4 py-10 sm:px-6">
        <span className="font-semibold font-serif text-lg">{siteName}</span>
        {note === '' ? null : <p className="max-w-measure text-base-content/70 text-sm">{note}</p>}
      </div>
    </footer>
  )
}
