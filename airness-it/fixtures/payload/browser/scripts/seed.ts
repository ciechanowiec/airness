import { getPayload } from 'payload'
import config from '@/payload.config'

const payload: Awaited<ReturnType<typeof getPayload>> = await getPayload({ config })
await payload.create({
  collection: 'users',
  data: { email: 'browser@example.invalid', password: 'fixture-browser-password', name: 'Browser contract' },
})
await payload.destroy()
