// Where an editor previews a post: a page that shows the newest draft to an editor signed in to the admin panel,
// and to nobody else.

const PREVIEW_ROOT: string = '/preview/posts'

/**
 * The preview address of a post, which the admin panel's Preview button opens in a new tab.
 * @param post the post as the admin panel holds it, saved at least once.
 * @returns the address, on the site's own origin.
 */
export const postPreviewPath = (post: Readonly<Record<string, unknown>>): string => {
  const slug: unknown = post['slug']
  return typeof slug === 'string' && slug !== '' ? `${PREVIEW_ROOT}/${encodeURIComponent(slug)}` : PREVIEW_ROOT
}

/**
 * The address the admin panel's Live Preview shows beside the editor: the post's preview page, which follows each
 * save of the draft.
 * @param args the Live Preview's arguments.
 * @param args.data the post as the editor holds it.
 * @returns the address, on the site's own origin.
 */
export const postLivePreviewPath = ({ data }: { readonly data: Readonly<Record<string, unknown>> }): string =>
  postPreviewPath(data)
