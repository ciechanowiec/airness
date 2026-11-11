// Waiting in the page for a condition written as source. `page.waitForFunction` given a string evaluates it
// with `eval` on every poll, which a page's Content Security Policy without 'unsafe-eval' refuses - the policy
// a seeded proxy sends. The condition here is part of one evaluated expression instead, which the protocol
// runs outside the page's policy, and the expression polls it itself.
import type { Page } from '@playwright/test'

// How often the condition is read again, about once a frame.
const POLL_MS: number = 16

// A navigation the awaited key caused destroys the context the poll ran in: the condition was not met there,
// and the caller's next read happens on the page that replaced it.
const DESTROYED: string = 'Execution context was destroyed'

/**
 * Wait until a condition holds in the page, or the time runs out.
 * @param page the page.
 * @param condition a JavaScript expression, read afresh on each poll.
 * @param timeoutMs how long to wait.
 * @returns whether the condition held before the time ran out.
 */
export const isMetInPage = async (page: Page, condition: string, timeoutMs: number): Promise<boolean> => {
  try {
    return await page.evaluate<boolean>(
      `new Promise((resolve) => {
        const deadline = performance.now() + ${String(timeoutMs)}
        const poll = () => {
          const isMet = Boolean(${condition})
          if (isMet || performance.now() >= deadline) {
            resolve(isMet)
          } else {
            setTimeout(poll, ${String(POLL_MS)})
          }
        }
        poll()
      })`,
    )
  } catch (error: unknown) {
    if (error instanceof Error && error.message.includes(DESTROYED)) {
      return false
    }
    throw error
  }
}
