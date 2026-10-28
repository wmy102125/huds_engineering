import type { ApiErrorBody } from './types'

// The backend uses a "double submit" CSRF token: it sets a readable cookie and expects
// the same value back in a header on every request that changes data.
const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'

/** Thrown for every failed request. `status` is 0 when the server could not be reached. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly retryable: boolean
  readonly body: ApiErrorBody | null

  constructor(status: number, code: string, message: string, retryable: boolean, body: ApiErrorBody | null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.retryable = retryable
    this.body = body
  }

  /** True when the request probably never reached the server (offline, server down). */
  get isNetworkError(): boolean {
    return this.status === 0
  }
}

type UnauthenticatedHandler = () => void
let unauthenticatedHandler: UnauthenticatedHandler | null = null

/** The app registers one handler that shows the login page when the session has ended. */
export function setUnauthenticatedHandler(handler: UnauthenticatedHandler | null): void {
  unauthenticatedHandler = handler
}

let csrfToken: string | null = null

function readCookie(name: string): string | null {
  const prefix = `${name}=`
  for (const part of document.cookie.split('; ')) {
    if (part.startsWith(prefix)) {
      return decodeURIComponent(part.slice(prefix.length))
    }
  }
  return null
}

async function toApiError(response: Response): Promise<ApiError> {
  let body: ApiErrorBody | null
  try {
    body = (await response.json()) as ApiErrorBody
  } catch {
    body = null
  }
  return new ApiError(
    response.status,
    body?.code ?? `HTTP_${response.status}`,
    body?.message ?? `The request failed (HTTP ${response.status}).`,
    body?.retryable ?? false,
    body,
  )
}

async function fetchCsrfToken(): Promise<string> {
  let response: Response
  try {
    response = await fetch('/api/v1/auth/csrf', { credentials: 'include' })
  } catch {
    throw new ApiError(0, 'NETWORK', 'The server cannot be reached.', true, null)
  }
  if (!response.ok) {
    throw await toApiError(response)
  }
  const body = (await response.json()) as { token: string }
  csrfToken = body.token
  return body.token
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
}

/**
 * Sends a request to the backend and returns the parsed JSON (undefined for 204).
 * Cookies are always included. Requests that change data carry the CSRF header; when the
 * server says the token is stale, a new token is fetched and the request is sent once more.
 */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET'
  let csrfRetried = false

  for (;;) {
    const headers: Record<string, string> = { Accept: 'application/json' }
    if (options.body !== undefined) {
      headers['Content-Type'] = 'application/json'
    }
    if (method !== 'GET') {
      headers[CSRF_HEADER] = readCookie(CSRF_COOKIE) ?? csrfToken ?? (await fetchCsrfToken())
    }

    let response: Response
    try {
      response = await fetch(path, {
        method,
        headers,
        credentials: 'include',
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
      })
    } catch {
      throw new ApiError(0, 'NETWORK', 'The server cannot be reached.', true, null)
    }

    if (response.ok) {
      if (response.status === 204) {
        return undefined as T
      }
      return (await response.json()) as T
    }

    const error = await toApiError(response)
    if (response.status === 403 && error.retryable && !csrfRetried) {
      csrfRetried = true
      await fetchCsrfToken()
      continue
    }
    if (response.status === 401 && !path.startsWith('/api/v1/auth/login')) {
      unauthenticatedHandler?.()
    }
    throw error
  }
}
