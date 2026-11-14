// The ESLint configuration of a Payload node project: the node layer's, plus the blocks keyed to Payload's
// layout. The managed `eslint.config.mjs` of a Payload project re-exports it, so an editor applies the Payload
// rules where it applies the node ones.
import nodeConfig from '@airness/node/eslint'
import { NO_MOCK_PROPERTIES } from '@airness/node/eslint-core'
import { asRecord, isArray } from '@airness/node/json-shapes'
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
// Every TypeScript source of the project, in each extension the Payload source rules read.
const ENVIRONMENT_READ_FILES: readonly string[] = ['src/**/*.{ts,tsx,mts,cts}']

const ENVIRONMENT_READ_EXEMPTIONS: readonly string[] = [VALIDATED_ENVIRONMENT_MODULE, 'src/proxy.ts']

const RESTRICTED_SYNTAX: string = 'no-restricted-syntax'

const INLINE_CONFIG_FUNCTION: Readonly<Record<string, string>> = {
  selector: 'ArrowFunctionExpression, FunctionExpression',
  message:
    'No inline functions in collection, global, field or block configs. Define access and pure rules in ' +
    'src/access or src/lib, where the unit suite covers them, and a hook or validator that needs the request ' +
    'in src/hooks; then import it by reference.',
}

const UNTHREADED_REQUEST_MESSAGE: string =
  'Do not destructure payload out of the request or bind req.payload to a name. Call ' +
  'req.payload.<operation>({ ..., req }) instead, so the Payload source rules see the request-scoped instance ' +
  "and require req to be passed, which keeps the call inside the hook's transaction."

// The names the Payload source rules read as the request: `req`, as Payload's own examples spell it, and
// `request`, as a handler that spells its parameter out does. One pattern for both, so the selectors below
// cannot cover one spelling and miss the other.
const REQUEST_NAME: string = '/^(?:req|request)$/'

// `req.payload`, `request.payload` and `<object>.req.payload`, in the dotted and the bracketed spelling.
const REQUEST_PAYLOAD: string =
  "MemberExpression:matches([property.name='payload'], [property.value='payload'])" +
  `:matches([object.name=${REQUEST_NAME}], [object.property.name=${REQUEST_NAME}])`

// What may wrap the value without changing the instance it is: optional chaining and the type-only expressions.
const TRANSPARENT_WRAPPER: string =
  ':matches(ChainExpression, TSAsExpression, TSNonNullExpression, TSSatisfiesExpression)'

// The request-scoped instance as the value a declaration or an assignment binds, bare or wrapped.
const boundRequestPayload = (binding: string, field: string): string =>
  `${binding} > ${REQUEST_PAYLOAD}.${field}, ${binding} > ${TRANSPARENT_WRAPPER}.${field} > ${REQUEST_PAYLOAD}`

// `payload` taken out of the request loses the receiver the Payload source rules recognise, so a call through it
// ran unjudged: outside the hook's transaction, and, under any name but `payload`, past every Local API rule.
// Banning every way of taking it out keeps those rules' text reading true without scope analysis: destructuring,
// in the parameter form Payload's own examples use and in the declaration form, and binding the instance to a
// name, `const cms = req.payload` or `cms = req.payload`.
const UNTHREADED_REQUEST_SYNTAX: readonly Readonly<Record<string, string>>[] = [
  {
    selector: `ObjectPattern > Property[key.name=${REQUEST_NAME}] > ObjectPattern > Property[key.name='payload']`,
    message: UNTHREADED_REQUEST_MESSAGE,
  },
  {
    selector: `VariableDeclarator[init.name=${REQUEST_NAME}] > ObjectPattern > Property[key.name='payload']`,
    message: UNTHREADED_REQUEST_MESSAGE,
  },
  {
    selector: `VariableDeclarator[init.property.name=${REQUEST_NAME}] > ObjectPattern > Property[key.name='payload']`,
    message: UNTHREADED_REQUEST_MESSAGE,
  },
  { selector: boundRequestPayload('VariableDeclarator', 'init'), message: UNTHREADED_REQUEST_MESSAGE },
  { selector: boundRequestPayload('AssignmentExpression', 'right'), message: UNTHREADED_REQUEST_MESSAGE },
]

// Where a Payload project writes the handlers its `endpoints:` entries serve, which `require-endpoint-module` holds
// them to: the one place a request's body is the module's own input.
const PAYLOAD_ENDPOINT_FILES: readonly string[] = ['src/endpoints/**']

const REQUEST_DATA_MESSAGE: string =
  'Read req.data only in an endpoint handler under src/endpoints. It is the raw request body, shaped by the ' +
  'transport: the parsed body over REST, { query, variables } over GraphQL, nothing over the Local API. Read the ' +
  "operation's own arguments instead, data in a collection or field hook and args.data in beforeOperation or " +
  'afterOperation, or a value an operation hook of the same request set on req.context.'

// `req.data`, `request.data` and `<object>.req.data`, dotted, bracketed or behind a type-only expression.
const REQUEST_DATA: string =
  "MemberExpression:matches([property.name='data'], [property.value='data'])" +
  `:matches([object.name=${REQUEST_NAME}], [object.property.name=${REQUEST_NAME}], ` +
  `[object.expression.name=${REQUEST_NAME}])`

// A hook outside an endpoint reads its operation's data from its own arguments, because `req.data` holds whatever
// the transport parsed: a root `afterError` hook reading a sign-in's email from it records the email over REST and
// nothing over GraphQL, where the credentials travel in the operation's variables. Every way of reading it is
// banned: the property itself, destructuring it out of the request in a parameter, a declaration or an
// assignment, and `Reflect.get`, so that no spelling reads the body the rule exists to keep out of reach.
const REQUEST_DATA_SYNTAX: readonly Readonly<Record<string, string>>[] = [
  { selector: REQUEST_DATA, message: REQUEST_DATA_MESSAGE },
  {
    selector: `ObjectPattern > Property[key.name=${REQUEST_NAME}] > ObjectPattern > Property[key.name='data']`,
    message: REQUEST_DATA_MESSAGE,
  },
  {
    selector:
      `:matches(VariableDeclarator[init.name=${REQUEST_NAME}], ` +
      `VariableDeclarator[init.property.name=${REQUEST_NAME}], ` +
      `AssignmentExpression[right.name=${REQUEST_NAME}]) > ObjectPattern > Property[key.name='data']`,
    message: REQUEST_DATA_MESSAGE,
  },
  {
    selector:
      "CallExpression[callee.object.name='Reflect'][callee.property.name='get'][arguments.1.value='data']" +
      `:matches([arguments.0.name=${REQUEST_NAME}], [arguments.0.property.name=${REQUEST_NAME}])`,
    message: REQUEST_DATA_MESSAGE,
  },
]

// The node layer's restricted-syntax setting for every file, which a block naming the rule replaces, so the
// Payload block extends it rather than dropping the selectors it carries.
/** One block of the configuration, as ESLint reads it. */
type ConfigBlock = (typeof nodeConfig)[number]

// For every node block that sets the rule, a Payload block for the same files that extends its setting with the
// Payload selectors. A block naming the rule replaces the setting of every block before it for the files it
// matches, so one Payload block of its own would drop the node layer's file-specific selectors - the CloudFront
// runtime's, say - wherever the two overlap. Following the node blocks in their own order, the extensions resolve
// for each file to the same node setting as before, with the selectors added. The node blocks themselves stay
// first and untouched, because the harness admits a layer only as an extension of the node layer's own blocks.
//
// Each extension is followed by its twin for every file but the endpoint handlers, which adds the request-body
// selectors. A file outside src/endpoints resolves to the twin, and an endpoint handler, which the twin ignores, to
// the extension before it, so the body stays readable exactly where it is the module's own input.
const payloadSyntaxBlocks: readonly ConfigBlock[] = nodeConfig.flatMap((block: ConfigBlock): readonly ConfigBlock[] => {
  const setting: unknown = block.rules?.[RESTRICTED_SYNTAX]
  if (!isArray(setting)) {
    return []
  }
  const extended: readonly unknown[] = [...setting.slice(1), ...UNTHREADED_REQUEST_SYNTAX]
  return [
    {
      ...(block.files !== undefined && { files: block.files }),
      ...(block.ignores !== undefined && { ignores: block.ignores }),
      rules: { [RESTRICTED_SYNTAX]: ['error', ...extended] },
    },
    {
      ...(block.files !== undefined && { files: block.files }),
      ignores: [...(block.ignores ?? []), ...PAYLOAD_ENDPOINT_FILES],
      rules: { [RESTRICTED_SYNTAX]: ['error', ...extended, ...REQUEST_DATA_SYNTAX] },
    },
  ]
})

const baseRestrictedSyntax = (): readonly unknown[] => {
  const setting: unknown = nodeConfig
    .filter((block: ConfigBlock): boolean => block.files === undefined)
    .map((block: ConfigBlock): unknown => block.rules?.[RESTRICTED_SYNTAX])
    .findLast((value: unknown): boolean => isArray(value))
  return isArray(setting) ? setting.slice(1) : []
}

const IMMUTABLE_DATA: string = 'functional/immutable-data'

// A hook's per-request state. Payload hands every hook the same `req.context` object and documents writing to it
// as the way one hook passes a value to the next and stops a loop it would otherwise start. That is the
// framework's contract rather than a data structure the project owns, so a rule that reported it would make every
// hook spend a suppression on the framework's own idiom. Written as `req.context.*.**` rather than
// `req.context.**`, because the plugin reads `**` as any depth including none: the shorter pattern would also
// admit `req.context = {}`, and replacing the bag is not writing into it.
const REQUEST_CONTEXT: string = 'req.context.*.**'

// The hook directories' block: the node layer's immutability setting, its carve-outs kept and the request
// context added. A block that sets this rule replaces its options, so the node layer's accessors are read from
// its own block rather than restated, and its plugin is mounted with them. A node configuration without that
// block fails loudly, since a hook block built from nothing would silently drop the node layer's carve-outs.
const hookContextBlock = (files: readonly string[]): ConfigBlock => {
  const immutability: ConfigBlock | undefined = nodeConfig.find(
    (block: ConfigBlock): boolean =>
      block.rules?.['functional/no-let'] !== undefined && block.rules[IMMUTABLE_DATA] !== undefined,
  )
  const setting: unknown = immutability?.rules?.[IMMUTABLE_DATA]
  const options: Record<string, unknown> = asRecord(isArray(setting) ? setting[1] : undefined)
  const accessors: unknown = options['ignoreAccessorPattern']
  if (immutability?.plugins === undefined || !isArray(accessors)) {
    throw new Error(`the node layer's ESLint configuration holds no ${IMMUTABLE_DATA} block to extend for hooks`)
  }
  return {
    files: [...files],
    plugins: immutability.plugins,
    rules: { [IMMUTABLE_DATA]: ['error', { ...options, ignoreAccessorPattern: [...accessors, REQUEST_CONTEXT] }] },
  }
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
    rules: {
      [RESTRICTED_SYNTAX]: [
        'error',
        ...baseRestrictedSyntax(),
        ...UNTHREADED_REQUEST_SYNTAX,
        ...REQUEST_DATA_SYNTAX,
        INLINE_CONFIG_FUNCTION,
      ],
    },
  },
  // The environment surface stays auditable in one validated, unit-tested module. Every TypeScript extension
  // is governed, so a page or a module-format file cannot read a variable the environment gate never sees.
  {
    files: [...ENVIRONMENT_READ_FILES],
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

const config: readonly ConfigBlock[] = [...nodeConfig, ...payloadSyntaxBlocks, ...payloadBlocks]

export default config
