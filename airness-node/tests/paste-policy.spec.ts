import { describe, expect, it } from 'vitest'
import { isAuthenticationField, pasteDefects } from '@/paste-policy'

const AUTHENTICATION_TOKENS: readonly string[] = ['username', 'current-password', 'new-password', 'one-time-code']

const CANCELS: string = 'cancels a paste, so a password manager or a copied code cannot fill it'
const AUTOFILL_OFF: string = 'turns autocomplete off, so a password manager is told not to fill it'

describe('a sign-in field', () => {
  it('is a password field, whatever its autocomplete', () => {
    expect([
      isAuthenticationField({ type: 'password', autocomplete: '' }),
      isAuthenticationField({ type: 'PASSWORD', autocomplete: 'off' }),
    ]).toEqual([true, true])
  })

  it('is any field whose autocomplete names a username, a password or a one-time code', () => {
    expect(
      AUTHENTICATION_TOKENS.map((token: string): boolean =>
        isAuthenticationField({ type: 'text', autocomplete: token }),
      ),
    ).toEqual([true, true, true, true])
  })

  it('reads the token in any position, any case and any spacing, as a section or a webauthn hint spells it', () => {
    expect([
      isAuthenticationField({ type: 'email', autocomplete: 'section-login username' }),
      isAuthenticationField({ type: 'text', autocomplete: '  One-Time-Code ' }),
      isAuthenticationField({ type: 'text', autocomplete: 'username webauthn' }),
    ]).toEqual([true, true, true])
  })

  it('is not an ordinary field', () => {
    expect([
      isAuthenticationField({ type: 'text', autocomplete: '' }),
      isAuthenticationField({ type: 'email', autocomplete: 'email' }),
      isAuthenticationField({ type: 'search', autocomplete: 'off' }),
    ]).toEqual([false, false, false])
  })
})

describe('paste defects', () => {
  it('fail a sign-in field that cancels a paste', () => {
    expect([
      pasteDefects({ type: 'password', autocomplete: 'current-password' }, true),
      pasteDefects({ type: 'text', autocomplete: 'one-time-code' }, true),
    ]).toEqual([[CANCELS], [CANCELS]])
  })

  it('fail a password field that turns autocomplete off, however it is spelled', () => {
    expect([
      pasteDefects({ type: 'password', autocomplete: 'off' }, false),
      pasteDefects({ type: 'password', autocomplete: ' OFF ' }, false),
    ]).toEqual([[AUTOFILL_OFF], [AUTOFILL_OFF]])
  })

  it('report both when a password field does both', () => {
    expect(pasteDefects({ type: 'password', autocomplete: 'off' }, true)).toEqual([CANCELS, AUTOFILL_OFF])
  })

  it('pass a sign-in field that accepts a paste with autofill on', () => {
    expect([
      pasteDefects({ type: 'password', autocomplete: 'new-password' }, false),
      pasteDefects({ type: 'text', autocomplete: 'username' }, false),
    ]).toEqual([[], []])
  })

  it('ignore a field that is not part of signing in, even one that cancels a paste or turns autofill off', () => {
    expect([
      pasteDefects({ type: 'text', autocomplete: 'off' }, true),
      pasteDefects({ type: 'email', autocomplete: 'email' }, true),
    ]).toEqual([[], []])
  })
})
