/**
 * The directive a policy states for one name, so a test can assert about script without matching the
 * whole header and breaking on an unrelated addition to it.
 * @param policy - the Content-Security-Policy header value.
 * @param name - the directive name, such as `script-src`.
 * @returns the directive with its sources, or an empty string when the policy omits it.
 */
export const directiveOf = (policy: string, name: string): string =>
  policy
    .split(';')
    .map((part: string): string => part.trim())
    .find((part: string): boolean => part.startsWith(`${name} `)) ?? ''
