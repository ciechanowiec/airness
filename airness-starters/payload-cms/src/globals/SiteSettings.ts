import type { GlobalConfig } from 'payload'

import { adminsOrEditors, anyone } from '@/access'

/** The longest tagline, which the home page shows as its heading. */
const TAGLINE_MAX_LENGTH: number = 120
/** The longest description, about the length search engines show. */
const DESCRIPTION_MAX_LENGTH: number = 240
/** The longest footer note. */
const FOOTER_NOTE_MAX_LENGTH: number = 200
/** How many categories the header lists, which still fit one line on a phone. */
const NAVIGATION_MAX_ROWS: number = 4

/** What editors change about the whole site without a release: its words and its navigation. */
export const SiteSettings: GlobalConfig = {
  slug: 'site-settings',
  label: 'Site settings',
  access: {
    read: anyone, // every page shows the settings
    update: adminsOrEditors,
  },
  fields: [
    {
      name: 'tagline',
      type: 'text',
      maxLength: TAGLINE_MAX_LENGTH,
      admin: { description: 'The heading of the home page.' },
    },
    {
      name: 'description',
      type: 'textarea',
      maxLength: DESCRIPTION_MAX_LENGTH,
      admin: { description: 'Introduces the site under the tagline and in search results.' },
    },
    {
      name: 'navigation',
      type: 'relationship',
      relationTo: 'categories',
      hasMany: true,
      maxRows: NAVIGATION_MAX_ROWS,
      admin: { description: 'The categories the header links, in this order.' },
    },
    {
      name: 'footerNote',
      type: 'text',
      maxLength: FOOTER_NOTE_MAX_LENGTH,
    },
  ],
}
