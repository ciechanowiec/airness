import { getPayload, type Payload } from 'payload'

import config from '@/payload.config'

/**
 * The application's Payload instance, which every query of the public site reads through. Payload keeps one per
 * process, so each call returns the same instance once the first has started it.
 * @returns the instance.
 */
export const payloadClient = async (): Promise<Payload> => await getPayload({ config })
