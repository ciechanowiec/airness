import { greeting } from '@/greeting'

/**
 * The home page.
 * @returns the page.
 */
export default function Home(): React.ReactNode {
  return (
    <main>
      <h1>{greeting('Airness')}</h1>
    </main>
  )
}
