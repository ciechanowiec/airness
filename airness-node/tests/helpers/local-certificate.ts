// A certificate for 127.0.0.1, made and signed in process, so a spec can serve HTTPS on a local port and fetch
// can trust it: a real TLS server, with no key committed as a fixture and no tool beyond Node itself.
import { generateKeyPairSync, type KeyObject, sign } from 'node:crypto'
import { getCACertificates, setDefaultCACertificates } from 'node:tls'

/** A certificate and its private key, both PEM. */
export interface LocalCertificate {
  readonly cert: string
  readonly key: string
}

// The DER tags the certificate is written with.
const INTEGER: number = 0x02
const BIT_STRING: number = 0x03
const OCTET_STRING: number = 0x04
const OBJECT_IDENTIFIER: number = 0x06
const UTF8_STRING: number = 0x0c
const UTC_TIME: number = 0x17
const SEQUENCE: number = 0x30
const SET: number = 0x31
const VERSION_FIELD: number = 0xa0
const EXTENSIONS_FIELD: number = 0xa3
const IP_ADDRESS_NAME: number = 0x87

const VERSION_3: number = 2
const SERIAL_NUMBER: number = 1
const SHORT_LENGTH_LIMIT: number = 0x80
const BYTE_VALUES: number = 0x100
const MORE_BASE_128: number = 0x80
const FIRST_ARC_FACTOR: number = 40
const UNUSED_BITS_NONE: number = 0

const ECDSA_WITH_SHA256: string = '1.2.840.10045.4.3.2'
const COMMON_NAME: string = '2.5.4.3'
const SUBJECT_ALT_NAME: string = '2.5.29.17'
const BASIC_CONSTRAINTS: string = '2.5.29.19'
const BOOLEAN: number = 0x01
const BOOLEAN_TRUE: number = 0xff

const HOST: string = '127.0.0.1'
const GENERALIZED_TIME: number = 0x18

// A fixed validity rather than one read from Date: the test workers run on the harness's shifted clock, while
// OpenSSL judges a certificate by the machine's real time, so a window around Date.now would miss the real one.
// The start is a UTCTime, as a year before 2050 must be, and the end a GeneralizedTime, as a year after must be.
const VALID_FROM: string = '000101000000Z'
const VALID_UNTIL: string = '29991231235959Z'

const bytesOf = (value: number): readonly number[] =>
  value === 0 ? [] : [...bytesOf(Math.floor(value / BYTE_VALUES)), value % BYTE_VALUES]

// A length below 128 in its one byte, and a longer one as its byte count, marked, followed by its bytes.
const lengthOf = (length: number): Buffer =>
  Buffer.from(
    length < SHORT_LENGTH_LIMIT ? [length] : [SHORT_LENGTH_LIMIT + bytesOf(length).length, ...bytesOf(length)],
  )

const element = (tag: number, ...content: readonly Buffer[]): Buffer => {
  const body: Buffer = Buffer.concat(content)
  return Buffer.concat([Buffer.from([tag]), lengthOf(body.length), body])
}

// An arc in base 128, most significant digit first, every digit but the last marked as followed by another.
const base128 = (arc: number): readonly number[] => {
  const digitsOf = (rest: number): readonly number[] =>
    rest < MORE_BASE_128 ? [rest] : [...digitsOf(Math.floor(rest / MORE_BASE_128)), rest % MORE_BASE_128]
  const digits: readonly number[] = digitsOf(arc)
  return digits.map((digit: number, index: number): number =>
    index < digits.length - 1 ? digit + MORE_BASE_128 : digit,
  )
}

const objectIdentifier = (dotted: string): Buffer => {
  const [first = 0, second = 0, ...rest]: readonly number[] = dotted.split('.').map(Number)
  return element(
    OBJECT_IDENTIFIER,
    Buffer.from([first * FIRST_ARC_FACTOR + second, ...rest.flatMap((arc: number): readonly number[] => base128(arc))]),
  )
}

const distinguishedName = (commonName: string): Buffer =>
  element(
    SEQUENCE,
    element(SET, element(SEQUENCE, objectIdentifier(COMMON_NAME), element(UTF8_STRING, Buffer.from(commonName)))),
  )

const extension = (identifier: string, value: Buffer): Buffer =>
  element(SEQUENCE, objectIdentifier(identifier), element(OCTET_STRING, value))

const pem = (label: string, der: Buffer): string =>
  `-----BEGIN ${label}-----\n${(der.toString('base64').match(/.{1,64}/gu) ?? []).join('\n')}\n-----END ${label}-----\n`

/**
 * A self-signed certificate for 127.0.0.1, valid from 2000 to the end of 2999.
 * @returns the certificate and its key.
 */
export const localCertificate = (): LocalCertificate => {
  const { privateKey, publicKey }: { readonly privateKey: KeyObject; readonly publicKey: KeyObject } =
    generateKeyPairSync('ec', { namedCurve: 'P-256' })
  const algorithm: Buffer = element(SEQUENCE, objectIdentifier(ECDSA_WITH_SHA256))
  const subject: Buffer = distinguishedName(HOST)
  const tbs: Buffer = element(
    SEQUENCE,
    element(VERSION_FIELD, element(INTEGER, Buffer.from([VERSION_3]))),
    element(INTEGER, Buffer.from([SERIAL_NUMBER])),
    algorithm,
    subject,
    element(
      SEQUENCE,
      element(UTC_TIME, Buffer.from(VALID_FROM, 'ascii')),
      element(GENERALIZED_TIME, Buffer.from(VALID_UNTIL, 'ascii')),
    ),
    subject,
    publicKey.export({ type: 'spki', format: 'der' }),
    element(
      EXTENSIONS_FIELD,
      element(
        SEQUENCE,
        extension(BASIC_CONSTRAINTS, element(SEQUENCE, element(BOOLEAN, Buffer.from([BOOLEAN_TRUE])))),
        extension(
          SUBJECT_ALT_NAME,
          element(SEQUENCE, element(IP_ADDRESS_NAME, Buffer.from(HOST.split('.').map(Number)))),
        ),
      ),
    ),
  )
  const signature: Buffer = sign('sha256', tbs, privateKey)
  const certificate: Buffer = element(
    SEQUENCE,
    tbs,
    algorithm,
    element(BIT_STRING, Buffer.from([UNUSED_BITS_NONE]), signature),
  )
  return {
    cert: pem('CERTIFICATE', certificate),
    key: privateKey.export({ type: 'pkcs8', format: 'pem' }),
  }
}

/**
 * Run something while this process's TLS clients, fetch among them, trust a certificate besides their own.
 * @param certificate the certificate, PEM.
 * @param use what runs meanwhile.
 * @returns what it returned.
 */
export const trusting = async <T>(certificate: string, use: () => Promise<T>): Promise<T> => {
  const trusted: readonly string[] = getCACertificates('default')
  setDefaultCACertificates([...trusted, certificate])
  try {
    return await use()
  } finally {
    setDefaultCACertificates([...trusted])
  }
}
