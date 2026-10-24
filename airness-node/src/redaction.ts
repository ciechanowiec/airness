// What a finding or a log may say about a URL. A registry URL can carry credentials in its userinfo or its
// query, and an error a fetch throws repeats the URL it was given, so every URL is reduced to its scheme,
// host and path before it reaches a result file or a build log.

// A URL as written in free text: a scheme, `://`, then everything up to whitespace or a quote.
const URL_IN_TEXT: RegExp = /(?<![\d+.a-z-])[a-z][\d+.a-z-]*:\/\/[^\s"'<>`]+/giu

const SCHEME_END: string = '://'

// What a URL that does not parse still gives away: userinfo before the host, and a query or fragment.
const withoutUserinfo = (written: string): string => {
  const authorityStart: number = written.indexOf(SCHEME_END) + SCHEME_END.length
  const rest: string = written.slice(authorityStart)
  const slash: number = rest.indexOf('/')
  const userinfoEnd: number = (slash === -1 ? rest : rest.slice(0, slash)).lastIndexOf('@')
  return userinfoEnd === -1 ? written : written.slice(0, authorityStart) + rest.slice(userinfoEnd + 1)
}

const withoutQuery = (written: string): string => written.split(/[?#]/u, 1)[0] ?? ''

const redactUrl = (written: string): string => {
  try {
    const url: URL = new URL(written)
    return `${url.protocol}//${url.host}${url.pathname}`
  } catch {
    return withoutQuery(withoutUserinfo(written))
  }
}

/**
 * Remove the userinfo, the query and the fragment of every URL in a text.
 * @param text a finding, an error message or a URL.
 * @returns the same text, each URL reduced to its scheme, host and path.
 */
export const redactUrls = (text: string): string =>
  text.replaceAll(URL_IN_TEXT, (written: string): string => redactUrl(written))
