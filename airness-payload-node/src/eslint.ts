// The ESLint configuration of a Payload node project: the node layer's, plus the blocks keyed to Payload's
// layout. The managed `eslint.config.mjs` of a Payload project re-exports it, so an editor applies the Payload
// rules where it applies the node ones.
import nodeConfig from '@airness/node/eslint'
import { hookContextBlock, NO_MOCK_PROPERTIES } from '@airness/node/eslint-core'
import { isArray } from '@airness/node/json-shapes'
import { VALIDATED_ENVIRONMENT_MODULE } from './payload-environment.js'

// Where a Payload project writes its hooks, and therefore the one place `req.context` is written.
const PAYLOAD_HOOK_FILES: readonly string[] = ['src/hooks/**']

// The declarative wiring of a Payload project, by directory, so a new configuration folder is governed at once.
const PAYLOAD_CONFIG_FILES: readonly string[] = [
  'src/collections/**/*.ts',
  'src/globals/**/*.ts',
  'src/fields/**/*.ts',
  'src/blocks/**/*.ts',
]

// The modules that may read `process.env`: the validated module, and the proxy Next runs in the edge runtime,
// where the validated module is not reachable.
const ENVIRONMENT_READ_EXEMPTIONS: readonly string[] = [VALIDATED_ENVIRONMENT_MODULE, 'src/proxy.ts']

const RESTRICTED_SYNTAX: string = 'no-restricted-syntax'

const INLINE_CONFIG_FUNCTION: Readonly<Record<string, string>> = {
  selector: 'ArrowFunctionExpression, FunctionExpression',
  message:
    'No inline functions in collection, global, field or block configs. Define access and pure rules in ' +
    'src/access or src/lib, where the unit suite covers them, and a hook or validator that needs the request ' +
    'in src/hooks; then import it by reference.',
}

// The node layer's restricted-syntax setting for every file, which a block naming the rule replaces, so the
// Payload block extends it rather than dropping the selectors it carries.
/** One block of the configuration, as ESLint reads it. */
type ConfigBlock = (typeof nodeConfig)[number]

const baseRestrictedSyntax = (): readonly unknown[] => {
  const setting: unknown = nodeConfig
    .filter((block: ConfigBlock): boolean => block.files === undefined)
    .map((block: ConfigBlock): unknown => block.rules?.[RESTRICTED_SYNTAX])
    .findLast((value: unknown): boolean => isArray(value))
  return isArray(setting) ? setting.slice(1) : []
}

const payloadBlocks: readonly ConfigBlock[] = [
  // Payload consumes an eagerly built configuration; its boot-time writes belong to payload-source-rules.
  { files: ['src/payload.config.ts'], rules: { 'unicorn/no-top-level-side-effects': 'off' } },
  // `payload migrate:create` writes `<date>_<time>_<name>.ts`, which is snake case.
  {
    files: ['src/migrations/**/*.ts'],
    rules: {
      'unicorn/filename-case': [
        'error',
        { cases: { camelCase: true, pascalCase: true, kebabCase: true, snakeCase: true } },
      ],
    },
  },
  hookContextBlock(PAYLOAD_HOOK_FILES),
  // A function inlined in declarative wiring has no unit-test seam, so it escapes the test gates.
  {
    files: [...PAYLOAD_CONFIG_FILES],
    rules: { [RESTRICTED_SYNTAX]: ['error', ...baseRestrictedSyntax(), INLINE_CONFIG_FUNCTION] },
  },
  // The environment surface stays auditable in one validated, unit-tested module.
  {
    files: ['src/**/*.ts'],
    ignores: [...ENVIRONMENT_READ_EXEMPTIONS],
    rules: {
      'no-restricted-properties': [
        'error',
        ...NO_MOCK_PROPERTIES,
        {
          object: 'process',
          property: 'env',
          message: `Read environment variables only in ${VALIDATED_ENVIRONMENT_MODULE}, where they are validated, and consume the typed values.`,
        },
      ],
    },
  },
]

const config: readonly ConfigBlock[] = [...nodeConfig, ...payloadBlocks]

export default config
