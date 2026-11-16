import type { CollectionConfig } from 'payload'

import { adminsOrEditors, anyone } from '@/access'
import { fillSlugFromTitle } from '@/hooks/slug'
import { SLUG_MAX_LENGTH, validateSlug } from '@/lib/slug'

/** The longest category title, short enough for a navigation link. */
const TITLE_MAX_LENGTH: number = 40
/** The longest description, about two lines under a category's heading. */
const DESCRIPTION_MAX_LENGTH: number = 240

/** The topics posts are filed under. Each has a page of its own at `/categories/<slug>`. */
export const Categories: CollectionConfig = {
  slug: 'categories',
  admin: {
    useAsTitle: 'title',
    defaultColumns: ['title', 'slug', 'updatedAt'],
  },
  access: {
    read: anyone, // the topics are part of the public site
    create: adminsOrEditors,
    update: adminsOrEditors,
    delete: adminsOrEditors,
  },
  fields: [
    {
      name: 'title',
      type: 'text',
      required: true,
      maxLength: TITLE_MAX_LENGTH,
    },
    {
      name: 'slug',
      type: 'text',
      required: true,
      unique: true,
      index: true,
      maxLength: SLUG_MAX_LENGTH,
      validate: validateSlug,
      hooks: { beforeValidate: [fillSlugFromTitle] },
      admin: {
        position: 'sidebar',
        description: 'The address of the category page. Left empty, it is made from the title.',
      },
    },
    {
      name: 'description',
      type: 'textarea',
      maxLength: DESCRIPTION_MAX_LENGTH,
    },
  ],
}
