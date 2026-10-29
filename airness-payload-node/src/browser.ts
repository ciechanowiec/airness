// Payload owns the navigation, header and frame around a custom admin view. A project's scan judges
// the content it supplied to the default or minimal template, which is the part it can repair.

/** The content roots the Airness browser adapter marks for an in-place admin-view scan. */
export const scanRootSelectors: readonly string[] = [
  '.template-default__wrap > :not(.app-header)',
  '.template-minimal__wrap > *',
]
