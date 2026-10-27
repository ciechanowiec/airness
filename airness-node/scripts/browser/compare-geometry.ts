// Compare two snapshots `geometry-probe.ts` wrote on different architectures. They must agree within
// the tolerance every geometry rule allows, or a verdict measured on one machine would not hold on the
// other.
import { readFileSync } from 'node:fs'
import { instability } from '#harness/browser-rules'
import { isArray, readKey } from '#harness/json-shapes'

const ARGUMENT_OFFSET: number = 2
const [first, second]: readonly (string | undefined)[] = process.argv.slice(ARGUMENT_OFFSET)
if (first === undefined || second === undefined) {
  throw new Error('Expected two snapshot files')
}

const numberAt = (value: unknown, key: string): number => {
  const read: unknown = readKey(value, key)
  if (typeof read !== 'number' || !Number.isFinite(read)) {
    throw new TypeError(`Invalid geometry number ${key}`)
  }
  return read
}
const read = (file: string): Parameters<typeof instability>[0] => {
  const raw: unknown = JSON.parse(readFileSync(file, 'utf8'))
  const nodes: unknown = readKey(raw, 'nodes')
  if (!isArray(nodes) || nodes.length === 0) {
    throw new TypeError('The geometry snapshot has no measured nodes')
  }
  return {
    nodes: nodes.map((node: unknown) => {
      const label: unknown = readKey(node, 'label')
      const rects: unknown = readKey(node, 'rects')
      if (typeof label !== 'string' || !isArray(rects)) {
        throw new TypeError('Invalid geometry node')
      }
      return {
        label,
        rects: rects.map((rect: unknown) => ({
          left: numberAt(rect, 'left'),
          top: numberAt(rect, 'top'),
          right: numberAt(rect, 'right'),
          bottom: numberAt(rect, 'bottom'),
        })),
      }
    }),
  }
}

const difference: string | undefined = instability(read(first), read(second))
if (difference !== undefined) {
  throw new Error(`the two architectures measured different geometry: ${difference}`)
}
console.info('the two architectures agree within the geometry tolerance')
