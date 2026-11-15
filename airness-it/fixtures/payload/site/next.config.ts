import { withPayload } from '@payloadcms/next/withPayload'
import type { NextConfig } from 'next'

const config: NextConfig = withPayload({ poweredByHeader: false })

export default config
