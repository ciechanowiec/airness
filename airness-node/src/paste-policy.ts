// What a sign-in field owes a reader who does not type their secret (rule `paste-blocked`): it accepts a
// pasted value, and a password field leaves autofill on. WCAG 3.3.8 and 3.3.9 ask that authentication needs
// no cognitive function test, and a password manager or a pasted one-time code is the mechanism that keeps a
// password from being one; a field that cancels the paste, or turns the manager away, removes it. The browser
// side dispatches a paste on each field and reports whether anything cancelled it; everything that decides a
// verdict is here.
//
// WHY A DISPATCHED PASTE. A handler that blocks pasting can sit on the field as a framework prop, on the field
// through `addEventListener`, inline as `onpaste="return false"`, or on any ancestor up to the document, and
// every one of them answers a bubbling, cancelable `paste` event by cancelling it. Dispatching the event is
// what reaches them all; reading the markup reaches only the inline one. A synthetic paste inserts nothing, so
// a field that accepts it is left as it was.

/** The `autocomplete` tokens that name a field as part of signing in. */
const AUTHENTICATION_TOKENS: ReadonlySet<string> = new Set<string>([
  'username',
  'current-password',
  'new-password',
  'one-time-code',
])

const PASSWORD_TYPE: string = 'password'

const OFF_TOKEN: string = 'off'

/** What the browser reports about one rendered, enabled input. */
export interface AuthenticationField {
  /** The input's `type`, as the element reports it: lower case, `text` when absent or unknown. */
  readonly type: string
  /** The `autocomplete` attribute verbatim, or empty without one. */
  readonly autocomplete: string
}

const tokensOf = (autocomplete: string): readonly string[] =>
  autocomplete
    .trim()
    .toLowerCase()
    .split(/\s+/u)
    .filter((token: string): boolean => token.length > 0)

/**
 * Whether an input takes part in signing in: a password field, or one whose `autocomplete` names a username,
 * a password or a one-time code, in any position among its tokens and in any case.
 * @param field the input.
 * @returns whether the paste rule applies to it.
 */
export const isAuthenticationField = (field: AuthenticationField): boolean =>
  field.type.toLowerCase() === PASSWORD_TYPE ||
  tokensOf(field.autocomplete).some((token: string): boolean => AUTHENTICATION_TOKENS.has(token))

/**
 * What is wrong with a sign-in field (rule `paste-blocked`).
 * @param field the input.
 * @param isPasteCancelled whether a dispatched paste came back cancelled.
 * @returns one sentence per defect, each to follow the field's label.
 */
export const pasteDefects = (field: AuthenticationField, isPasteCancelled: boolean): readonly string[] => {
  if (!isAuthenticationField(field)) {
    return []
  }
  const isAutofillOff: boolean =
    field.type.toLowerCase() === PASSWORD_TYPE && tokensOf(field.autocomplete).includes(OFF_TOKEN)
  return [
    ...(isPasteCancelled ? ['cancels a paste, so a password manager or a copied code cannot fill it'] : []),
    ...(isAutofillOff ? ['turns autocomplete off, so a password manager is told not to fill it'] : []),
  ]
}
