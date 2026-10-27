const KEY = 'civic.reporterToken'
let memoryToken: string | null = null

function newToken(): string {
  const bytes = new Uint8Array(24)
  crypto.getRandomValues(bytes)
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
}

/**
 * Anonymous id of this browser: the backend hashes it like a phone number so only the author can confirm a report.
 * Falls back to an in-memory token when storage is unavailable (private mode, blocked site data).
 */
export function reporterToken(): string {
  try {
    const stored = localStorage.getItem(KEY)
    if (stored) return stored
    const token = newToken()
    localStorage.setItem(KEY, token)
    return token
  } catch {
    memoryToken ??= newToken()
    return memoryToken
  }
}
