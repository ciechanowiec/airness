import { getPayload, type Payload } from 'payload'
import config from '@/payload.config'

const UNAUTHENTICATED: number = 401

export const GET = async (request: Request): Promise<Response> => {
  const payload: Payload = await getPayload({ config })

  const { user } = await payload.auth({ headers: request.headers })
  if (user === null) {
    return Response.json({ message: 'Authentication required' }, { status: UNAUTHENTICATED })
  }

  const { totalDocs } = await payload.count({ collection: 'users', user, overrideAccess: false })

  return Response.json({
    message: 'This is an example of a custom route.',
    users: totalDocs,
  })
}
