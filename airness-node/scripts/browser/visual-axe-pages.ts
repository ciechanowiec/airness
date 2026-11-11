// The visual fixture's pages for the axe rules the sweep's scan turns on beyond axe's defaults: the AAA
// rules, the target size measured at 44 CSS pixels, and the experimental rules. Each page holds one rule's
// defect, or the same markup repaired; the pinned sweep's own axe scan judges them.

/** A fixture page's body with its defect, and the same markup repaired. */
interface AxeVariant {
  readonly defect: string
  readonly valid: string
}

// A table of four rows of four cells, so axe judges it as large. The defect writes the header row as data.
const LEAGUE_HEADER: readonly string[] = ['Team', 'Won', 'Drawn', 'Lost']
const LEAGUE_BODY: readonly (readonly string[])[] = [
  ['North', '3', '1', '0'],
  ['South', '2', '1', '1'],
  ['East', '1', '0', '3'],
]
const dataCells = (row: readonly string[]): string => row.map((cell: string): string => `<td>${cell}</td>`).join('')
const dataRows = (rows: readonly (readonly string[])[]): string =>
  rows.map((row: readonly string[]): string => `<tr>${dataCells(row)}</tr>`).join('')
const headerCells = (row: readonly string[]): string =>
  row.map((cell: string): string => `<th scope="col">${cell}</th>`).join('')

const RESULTS_HEADER: string = '<tr><th scope="col">Team</th><th scope="col">Won</th><th scope="col">Lost</th></tr>'
const RESULTS_ROW: string = '<tr><td>North</td><td>3</td><td>1</td></tr>'
const SCHEDULE: string = '<p>Saturday brings three league matches across the city, the first kicking off at noon.</p>'

/** Each page's body in both modes, by its route. */
export const AXE_PAGES: Readonly<Record<string, AxeVariant>> = {
  // 4.5:1 passes AA and fails the 7:1 of AAA.
  'contrast-enhanced': {
    defect: '<p className="mid-grey">Mid grey text</p>',
    valid: '<p className="mid-grey deep">Mid grey text</p>',
  },
  // A 24px button passes AA and fails the 44px of AAA. The repair keeps a link inside a sentence below
  // 44px, which the inline exception passes, and a skip link clipped to a pixel until focus reveals it,
  // which no pointer can reach and so is no target to size.
  'target-size': {
    defect: '<button type="button" className="tight">Tight</button>',
    valid:
      '<a className="skip-link" href="#sized">Skip to the sized button</a>' +
      '<button type="button" id="sized" className="tight roomy">Tight</button>' +
      '<p>Read the <a className="inline-link" href="/">fixture rules</a> in a sentence.</p>',
  },
  // A refresh after more than twenty hours passes AA and fails AAA, which allows none.
  'meta-refresh': {
    defect: '<meta httpEquiv="refresh" content="72001" /><p>Refreshes tomorrow.</p>',
    valid: '<p>Refreshes tomorrow.</p>',
  },
  // Two links sharing one name that lead to different places.
  'link-purpose': {
    defect: '<ul><li><a href="/">Details</a></li><li><a href="/night">Details</a></li></ul>',
    valid: '<ul><li><a href="/">Home details</a></li><li><a href="/night">Night details</a></li></ul>',
  },
  // A short bold paragraph standing where a heading belongs.
  'heading-paragraph': {
    defect: `<div><p className="lead">Fixture schedule</p>${SCHEDULE}</div>`,
    valid: `<div><h2 className="lead">Fixture schedule</h2>${SCHEDULE}</div>`,
  },
  // A data table larger than three by three whose cells no header names.
  'table-headers': {
    defect: `<table><caption>League table</caption><tbody>${dataRows([LEAGUE_HEADER, ...LEAGUE_BODY])}</tbody></table>`,
    valid:
      `<table><caption>League table</caption><thead><tr>${headerCells(LEAGUE_HEADER)}</tr></thead>` +
      `<tbody>${dataRows(LEAGUE_BODY)}</tbody></table>`,
  },
  // A first row spanning the table, standing where its caption belongs.
  'table-caption': {
    defect: `<table><tbody><tr><td colSpan={3}>Results</td></tr>${RESULTS_HEADER}${RESULTS_ROW}</tbody></table>`,
    valid: `<table><caption>Results</caption><thead>${RESULTS_HEADER}</thead><tbody>${RESULTS_ROW}</tbody></table>`,
  },
}

/** The styles of these pages. */
export const AXE_STYLES: string = `
.mid-grey { color: #767676; }
.mid-grey.deep { color: #545454; }
.tight { min-width: 0; min-height: 0; height: 24px; padding: 0 4px; }
.tight.roomy { min-width: 44px; min-height: 44px; height: auto; }
.inline-link { display: inline; min-width: 0; min-height: 0; }
.skip-link:not(:focus) { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden;
  clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
.skip-link:focus { display: inline-block; min-height: 44px; padding: 12px; outline: 2px solid #111827; }
.lead { font-size: 1.25em; font-weight: 700; }
`

/** The route of the orientation page, which is written apart from the others. */
export const ORIENTATION_ROUTE: string = 'orientation-lock'

// The orientation page loads a stylesheet of its own, because axe reads an orientation lock from any rule
// of any stylesheet the page loads: in the shared one it would lock every page. In the defect mode the
// layout is turned a quarter in portrait, which forces a phone held upright to be turned to read it.
/** The orientation page's module, which imports its own stylesheet. */
export const ORIENTATION_PAGE: string = [
  "import './orientation.css'",
  '',
  'export default function Page(): React.JSX.Element {',
  `  return <main><h1>${ORIENTATION_ROUTE}</h1><div className="turned-layout"><p>Read upright</p></div></main>`,
  '}',
  '',
].join('\n')

/** The orientation page's stylesheet in each mode. */
export const ORIENTATION_STYLES: Readonly<Record<'defects' | 'valid', string>> = {
  defects: '@media (orientation: portrait) { .turned-layout { transform: rotate(90deg); } }\n',
  valid: '@media (orientation: portrait) { .turned-layout { max-width: 100%; } }\n',
}
