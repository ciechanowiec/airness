// Dates as a reader sees them. Formatting is fixed to one language and to UTC, so a page renders the same text
// on every server, whatever its locale and time zone.

const PUBLICATION_DATE: Intl.DateTimeFormat = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
})

/**
 * A publication date as a reader reads it.
 * @param timestamp an ISO 8601 timestamp, as Payload stores a date field.
 * @returns the date, such as `4 March 2026`.
 */
export const formatPublicationDate = (timestamp: string): string => PUBLICATION_DATE.format(new Date(timestamp))
