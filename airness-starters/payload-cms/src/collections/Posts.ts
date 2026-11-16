import type { CollectionConfig } from 'payload'

import { adminsOrEditors, publishedOrEditors } from '@/access'
import { stampPublicationDate } from '@/hooks/publication'
import { fillSlugFromTitle } from '@/hooks/slug'
import { postLivePreviewPath, postPreviewPath } from '@/lib/preview'
import { SLUG_MAX_LENGTH, validateSlug } from '@/lib/slug'

/** The longest title, which still fits a card heading in three lines. */
const TITLE_MAX_LENGTH: number = 120
/** The longest excerpt, which a card shows whole. */
const EXCERPT_MAX_LENGTH: number = 280
/** How many earlier versions of a post the history keeps. */
const VERSIONS_KEPT: number = 20
/** How long after an edit stops the draft is saved, which the Live Preview then shows. */
const AUTOSAVE_INTERVAL_MS: number = 800

/**
 * The articles of the site. A post is written as a draft, previewed, and published; only published posts reach a
 * visitor, while editors see every draft.
 */
export const Posts: CollectionConfig = {
  slug: 'posts',
  admin: {
    useAsTitle: 'title',
    defaultColumns: ['title', 'category', '_status', 'publishedAt'],
    preview: postPreviewPath,
    livePreview: { url: postLivePreviewPath },
  },
  versions: {
    drafts: { autosave: { interval: AUTOSAVE_INTERVAL_MS } },
    maxPerDoc: VERSIONS_KEPT,
  },
  access: {
    read: publishedOrEditors,
    readVersions: adminsOrEditors,
    create: adminsOrEditors,
    update: adminsOrEditors,
    delete: adminsOrEditors,
  },
  hooks: {
    beforeChange: [stampPublicationDate],
  },
  fields: [
    {
      name: 'title',
      type: 'text',
      required: true,
      maxLength: TITLE_MAX_LENGTH,
    },
    {
      name: 'excerpt',
      type: 'textarea',
      required: true,
      maxLength: EXCERPT_MAX_LENGTH,
      admin: { description: 'One or two sentences a card and search results show under the title.' },
    },
    {
      name: 'cover',
      type: 'upload',
      relationTo: 'media',
      required: true,
    },
    {
      name: 'content',
      type: 'richText',
      required: true,
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
        description: 'The address of the post. Left empty, it is made from the title.',
      },
    },
    {
      name: 'category',
      type: 'relationship',
      relationTo: 'categories',
      required: true,
      admin: { position: 'sidebar' },
    },
    {
      name: 'publishedAt',
      type: 'date',
      index: true,
      admin: {
        position: 'sidebar',
        description: 'Set when the post is first published, unless chosen here.',
      },
    },
    {
      name: 'featured',
      type: 'checkbox',
      defaultValue: false,
      admin: {
        position: 'sidebar',
        description: 'The newest featured post leads the home page.',
      },
    },
  ],
}
