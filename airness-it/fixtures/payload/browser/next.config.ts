import path from 'node:path'
import { withPayload } from '@payloadcms/next/withPayload'

export default withPayload({ poweredByHeader: false, turbopack: { root: path.resolve('..') } })
