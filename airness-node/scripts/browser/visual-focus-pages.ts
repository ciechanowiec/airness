// The pages of the visual fixture that the obscured-focus rule judges (WCAG 2.4.12): a fixed header over the
// whole of a short link and over the top of a tall one, and the same pages with their content clear of it.

/**
 * A page whose fixed header covers its link in the defect mode and clears it in the valid one.
 * @param mode which form to render.
 * @param link the focusable link the page holds.
 * @returns the page module's source.
 */
export const pinnedModule = (mode: 'defects' | 'valid', link: string): string =>
  [
    'export default function Page(): React.JSX.Element {',
    '  return <>',
    '    <header className="pinned">Pinned bar</header>',
    `    <main className="${mode === 'defects' ? 'under' : 'clear'}">`,
    `      ${link}<h1>Focus obscured</h1></main>`,
    '  </>',
    '}',
    '',
  ].join('\n')

/** Each obscured-focus page's link, by its route. */
export const PINNED_PAGES: Readonly<Record<string, string>> = {
  'focus-obscured': '<a href="/">Hidden link</a>',
  'focus-partly-obscured': '<a href="/" className="tall-link">Half-hidden link</a>',
}

/** The fixed header and the offsets that put it over the link or clear of it. */
export const PINNED_STYLES: string = `
.pinned { position: fixed; top: 0; left: 0; right: 0; height: 120px; background: #ffffff; color: #111827; }
.under { padding-top: 16px; }
.clear { padding-top: 140px; }
.tall-link { padding: 140px 0 24px; }
`
