import { getPayload, type Payload } from 'payload'
import config from '@/payload.config'

/** The Payload instance of the spec file, created once against the run's disposable services. */
export const payloadInstance: Promise<Payload> = getPayload({ config })
