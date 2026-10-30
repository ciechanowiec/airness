import path from 'node:path'
import { withPayload } from '@payloadcms/next/withPayload'

export default withPayload({ turbopack: { root: path.resolve('..') } })
