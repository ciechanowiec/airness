import type { CollectionConfig } from 'payload'

// The `auth` setting is `true` or an options object; the cookie settings belong to the object.
type CookieSettings = NonNullable<Exclude<CollectionConfig['auth'], boolean | undefined>['cookies']>

/**
 * The attributes of the session cookie.
 *
 * Payload leaves the cookie off `Secure` by default, so a browser that requests the site over plain HTTP, as one
 * does when someone types the address without `https://`, sends the session token in clear text to whatever
 * answers before the redirect. A production build marks it `Secure`, which keeps it off every plain-HTTP request.
 * The development server serves plain HTTP, so marking it there would leave a developer unable to sign in; the
 * mode is a parameter for the same reason it is one in `src/lib/security-headers.ts`, so both answers are tested.
 * @param isDevelopment - whether this is a development server rather than a production build.
 * @returns the cookie settings of the auth collection.
 */
export const sessionCookie = (isDevelopment: boolean): CookieSettings => ({ sameSite: 'Lax', secure: !isDevelopment })
