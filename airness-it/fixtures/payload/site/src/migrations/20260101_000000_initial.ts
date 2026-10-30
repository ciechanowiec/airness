// The recorded schema this application deploys with. A configuration whose adapter does not push its schema
// states it here, where a reader can review it.

/**
 * Apply the initial schema.
 * @returns nothing; a real migration issues its statements here.
 */
export const up = async (): Promise<void> => {
  await Promise.resolve()
}

/**
 * Undo the initial schema.
 * @returns nothing; a real migration reverses its statements here.
 */
export const down = async (): Promise<void> => {
  await Promise.resolve()
}
