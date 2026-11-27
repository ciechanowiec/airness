// The page of the visual contract whose answers the sweep judges beside its document: once it loads, it asks the
// application twice, as a page fetching its own data does. In the defects mode one answer declares no caching, and
// the other lets a shared cache store an answer to a request that carried authorization; repaired, the first
// declares that nothing may store it and the second keeps to the browser that asked.

import path from 'node:path'

/** One page's body, the same in both modes, whose defect lies in the answers it asks for. */
interface AnswerVariant {
  readonly defect: string
  readonly valid: string
}

/** The page, by its route. */
export const ANSWER_PAGES: Readonly<Record<string, AnswerVariant>> = {
  'answer-caching': { defect: '<Fetches />', valid: '<Fetches />' },
}

/** The client component that asks, by its name. */
export const ANSWER_COMPONENTS: Readonly<Record<string, string>> = {
  Fetches: `'use client'
import { useEffect } from 'react'

export function Fetches(): React.JSX.Element {
  useEffect((): void => {
    Promise.all([
      fetch('/answers/plain'),
      fetch('/answers/shared', { headers: { authorization: 'Bearer fixture-token' } }),
    ]).catch((): undefined => undefined)
  }, [])
  return <p>This page asks the application twice.</p>
}
`,
}

const ANSWER_CACHING: Readonly<Record<'defects' | 'valid', Readonly<Record<string, string | undefined>>>> = {
  defects: { plain: undefined, shared: 'public, max-age=60' },
  valid: { plain: 'no-store', shared: 'private, max-age=60' },
}

const answerRoute = (cacheControl: string | undefined): string => {
  const caching: string = cacheControl === undefined ? '' : `, 'cache-control': '${cacheControl}'`
  return [
    "export const dynamic = 'force-dynamic'",
    '',
    'export function GET(): Response {',
    `  return new Response('answered', { headers: { 'content-type': 'text/plain'${caching} } })`,
    '}',
    '',
  ].join('\n')
}

const answerRoutes = (mode: 'defects' | 'valid'): readonly (readonly [string, string])[] =>
  Object.entries(ANSWER_CACHING[mode]).map(
    ([name, cacheControl]: readonly [string, string | undefined]): readonly [string, string] => [
      `answers/${name}`,
      answerRoute(cacheControl),
    ],
  )

/**
 * Write each route handler the page asks under the app directory.
 * @param mode whether the answers hold their defects or are repaired.
 * @param appRoot the app directory of the generated application.
 * @param write writes one module's source to its file.
 */
export const writeAnswerRoutes = (
  mode: 'defects' | 'valid',
  appRoot: string,
  write: (file: string, source: string) => void,
): void => {
  const routes: readonly (readonly [string, string])[] = answerRoutes(mode)
  for (const [route, source] of routes) {
    write(path.join(appRoot, route, 'route.ts'), source)
  }
}
