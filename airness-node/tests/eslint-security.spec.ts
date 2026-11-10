import { Linter } from 'eslint'
import tseslint from 'typescript-eslint'
import { describe, expect, it } from 'vitest'
import { SECURITY_RESTRICTIONS } from '@/eslint-security.js'

// The two output-boundary restrictions, linted on their own: a value reaching console output or a
// caller's response through any non-call syntax is reported, and a call is the boundary that stops it.

const linter: Linter = new Linter({ configType: 'flat' })

const tagsOf = (code: string): readonly string[] =>
  linter
    .verify(
      code,
      [
        {
          files: ['**/*.ts'],
          languageOptions: { parser: tseslint.parser },
          rules: { 'no-restricted-syntax': ['error', ...SECURITY_RESTRICTIONS] },
        },
      ],
      'route.ts',
    )
    .map((message: Linter.LintMessage): string => /^\[([a-z-]+)\]/.exec(message.message)?.[1] ?? message.message)

describe('sensitive-data-logged', () => {
  it.each([
    'console.log(token)',
    'console.error(user.token)',
    'console.warn(user?.apiKey)',
    `console.info(\`token: \${token}\`)`,
    `console.debug(\`key: \${user.apiKey}\`)`,
    "console.log('token: ' + token)",
    "console.log('token: ' + user.password)",
    'console.log({ token })',
    'console.log({ value: user.secret })',
    'console.log({ value: token })',
    'console.log({ nested: { value: user.secret } })',
    "console.log(user.token ?? 'none')",
    'console.log(fallback || password)',
    "console.log(isVerbose ? token : 'hidden')",
    "console.log(isVerbose ? 'hidden' : user.privateKey)",
    `console.log(\`header \${isVerbose ? token : 0}\`)`,
    'console.log([token])',
    'console.log(token as string)',
    'console.log(token!)',
    'console.log(accessToken)',
    'console.log(session.refreshToken)',
    'console.log(clientSecret)',
    'console.log(config.dbPassword)',
    'console.log(stripeApiKey)',
    'console.log(signingPrivateKey)',
    'console.log(oauth2Token)',
    `console.log(\`header \${user.idToken}\`)`,
    'console.log(user["token"])',
    "console.log(user['password'])",
    "console.log(user?.['refreshToken'])",
    'console.log(user[`apiKey`])',
  ])('reports %s', (code: string) => {
    expect(tagsOf(code)).toEqual(['sensitive-data-logged'])
  })

  it.each([
    'console.log(redact(token))',
    `console.log(\`token: \${redact(token)}\`)`,
    "console.log('token: ' + mask(user.token))",
    'console.log({ value: redact(user.secret) })',
    "console.log(isVerbose ? redact(token) : 'hidden')",
    "console.log(token ? 'set' : 'unset')",
    'console.log(token === expected)',
    "console.log({ token: 'redacted' })",
    'console.log(token.length)',
    'logger.info(token)',
    'console.log(tokenCount)',
    'console.log(passwordPolicy)',
    'console.log(user.tokenType)',
    'console.log(Token)',
    "console.log(user['tokenType'])",
    ['console.log(user[`$', '{prefix}Token`])'].join(''),
    "console.log(redact(user['token']))",
  ])('leaves the call boundary and non-values alone: %s', (code: string) => {
    expect(tagsOf(code)).toEqual([])
  })
})

describe('leaks-error-message', () => {
  it.each([
    'Response.json(error.message)',
    'Response.json({ error: error.message })',
    "NextResponse.json({ 'message': err.stack })",
    'new Response(error.stack)',
    "Response.json({ error: error instanceof Error ? error.message : 'Unknown error' })",
    "Response.json({ error: error instanceof Error ? 'Unknown error' : error.message })",
    `Response.json({ error: \`Failed: \${error.message}\` })`,
    "Response.json({ error: 'Failed: ' + error.message })",
    "Response.json({ error: error.message ?? 'Failed' })",
    "Response.json({ error: error?.message || 'Failed' })",
    'Response.json({ error: { detail: error.message } })',
    'Response.json({ body: { message: error.message } })',
    'Response.json({ error: [error.stack] })',
    'Response.json({ error: error.message as string })',
    `Response.json(\`Failed: \${error.message}\`)`,
    "new Response(exception.message ?? 'Failed')",
    "function handle() { return { error: error instanceof Error ? error.message : 'x' } }",
    `const handle = () => ({ message: \`Failed: \${error.message}\` })`,
  ])('reports %s', (code: string) => {
    expect(tagsOf(code)).toEqual(['leaks-error-message'])
  })

  it.each([
    'Response.json({ error: publicError(error.message) })',
    'Response.json(publicError(error.message))',
    "Response.json({ error: error instanceof Error ? sanitise(error.message) : 'x' })",
    `Response.json({ error: \`Failed: \${sanitise(error.message)}\` })`,
    "Response.json({ error: error.message ? 'Failed' : 'Unknown' })",
    "Response.json({ error: 'Failed' })",
    'Response.json({ data: items })',
    "function handle() { return { error: 'Failed' } }",
  ])('leaves the call boundary and non-values alone: %s', (code: string) => {
    expect(tagsOf(code)).toEqual([])
  })
})
