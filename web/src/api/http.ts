/** Error from the API, with a Spanish message safe to show. */
export class ApiError extends Error {
  readonly code: string
  readonly status: number

  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

const GENERIC = 'Algo salió mal. Revisa tu conexión e intenta de nuevo.'

export async function parseJson<T>(response: Response): Promise<T> {
  if (response.ok) {
    return response.status === 204 ? (undefined as T) : ((await response.json()) as T)
  }
  let code = `HTTP_${response.status}`
  let message = GENERIC
  try {
    const body = (await response.json()) as { code?: string; message?: string }
    if (body.code) code = body.code
    if (body.message) message = body.message
  } catch {
    // Not a JSON error body: keep the generic message.
  }
  throw new ApiError(response.status, code, message)
}

export function postJson(path: string, body?: unknown, signal?: AbortSignal): Promise<Response> {
  return fetch(path, {
    method: 'POST',
    headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal,
  })
}

export function messageOf(error: unknown, fallback = GENERIC): string {
  return error instanceof ApiError ? error.message : fallback
}
