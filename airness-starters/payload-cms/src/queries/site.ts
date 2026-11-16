import type { Payload } from 'payload'

import { type SiteIdentity, toSiteIdentity } from '@/lib/views'
import type { SiteSetting } from '@/payload-types'
import { payloadClient } from './payload'

/** One depth populates the categories the navigation links. */
const SETTINGS_DEPTH: number = 1

/**
 * What every page shows about the site, from the site settings an editor keeps.
 * @returns the site's identity.
 */
export const findSiteIdentity = async (): Promise<SiteIdentity> => {
  const payload: Payload = await payloadClient()
  const settings: SiteSetting = await payload.findGlobal({
    slug: 'site-settings',
    depth: SETTINGS_DEPTH,
    overrideAccess: false,
  })
  return toSiteIdentity(settings)
}
