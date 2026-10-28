import { getDeviceId } from '../db/device'
import { apiRequest } from './http'
import type { ClientType, CurrentUser } from './types'

function detectClientType(): ClientType {
  return window.matchMedia('(max-width: 768px)').matches ? 'MOBILE' : 'DESKTOP'
}

export function login(email: string, password: string): Promise<CurrentUser> {
  return apiRequest<CurrentUser>('/api/v1/auth/login', {
    method: 'POST',
    body: {
      email,
      password,
      deviceId: getDeviceId(),
      deviceLabel: 'Web browser',
      clientType: detectClientType(),
    },
  })
}

/** Returns the logged-in user, or fails with 401 when there is no valid session. */
export function fetchMe(): Promise<CurrentUser> {
  return apiRequest<CurrentUser>('/api/v1/auth/me')
}

export function logout(): Promise<void> {
  return apiRequest<void>('/api/v1/auth/logout', { method: 'POST' })
}
