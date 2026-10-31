// The fragments an htmx page requests in place of a whole page: rendered independently by the server, and so
// routes of the application, but reached by no link a crawl follows. A page names each with an `hx-get`
// attribute. Only a GET is ever requested, so asking for one changes nothing the application keeps.

/** The elements that name a fragment, in both the spellings htmx reads. */
export const FRAGMENT_SELECTOR: string = '[hx-get], [data-hx-get]'

/** What one fragment request answered. A status of 0 means it did not answer at all. */
export interface FragmentAnswer {
  readonly url: string
  readonly status: number
  readonly contentType: string | undefined
}

/** The fragments that answered as fragments, and what failed on the server. */
export interface FragmentVerdict {
  /** The paths of the fragments that answered with HTML, which cover the route that renders them. */
  readonly credited: readonly string[]
  /** One sentence per fragment the server failed to render. */
  readonly problems: readonly string[]
}

const FIRST_SUCCESS: number = 200
const FIRST_REDIRECT: number = 300
const FIRST_SERVER_ERROR: number = 500

/**
 * The distinct fragment addresses a page names on its own origin.
 * @param values the `hx-get` values, as the page rendered them.
 * @param pageUrl the page's address, against which a relative value resolves.
 * @param origin the application's origin.
 * @returns the absolute addresses, query included, in the order first named.
 */
export const fragmentUrls = (values: readonly string[], pageUrl: string, origin: string): readonly string[] => [
  ...new Set<string>(
    values.flatMap((value: string): readonly string[] => {
      if (value.trim() === '') {
        return []
      }
      try {
        const url: URL = new URL(value, pageUrl)
        return url.origin === origin ? [url.href] : []
      } catch {
        return []
      }
    }),
  ),
]

const isRendered = (answer: FragmentAnswer): boolean =>
  answer.status >= FIRST_SUCCESS &&
  answer.status < FIRST_REDIRECT &&
  answer.contentType?.toLowerCase().includes('text/html') === true

const problemOf = (answer: FragmentAnswer): readonly string[] => {
  if (answer.status === 0) {
    return [`htmx fragment ${answer.url} did not answer`]
  }
  return answer.status >= FIRST_SERVER_ERROR ? [`htmx fragment ${answer.url} answered ${String(answer.status)}`] : []
}

/**
 * Judge the fragments a page requested. One that rendered HTML covers its route. A server error, or no answer,
 * is a defect. A redirect or a refusal, such as one asking for parameters only an interaction supplies, covers
 * nothing and is no defect: the route stays for the project's own specification to reach.
 * @param answers what each request answered.
 * @returns the credited paths and the problems.
 */
export const judgeFragments = (answers: readonly FragmentAnswer[]): FragmentVerdict => ({
  credited: [
    ...new Set<string>(
      answers
        .filter((answer: FragmentAnswer): boolean => isRendered(answer))
        .map((answer: FragmentAnswer): string => new URL(answer.url).pathname),
    ),
  ],
  problems: answers.flatMap((answer: FragmentAnswer): readonly string[] => problemOf(answer)),
})
