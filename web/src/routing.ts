/** Hash routes: #/ruta?clave=valor. No history library: the app is small and must work from any static host. */
export interface Route {
  path: string
  query: URLSearchParams
}

export function currentRoute(): Route {
  const hash = window.location.hash.replace(/^#/, '') || '/'
  const [path, search = ''] = hash.split('?')
  return { path: path || '/', query: new URLSearchParams(search) }
}

export function navigate(path: string, query?: Record<string, string>): void {
  const search = query ? new URLSearchParams(query).toString() : ''
  window.location.hash = search ? `#${path}?${search}` : `#${path}`
}

export function href(path: string, query?: Record<string, string>): string {
  const search = query ? new URLSearchParams(query).toString() : ''
  return search ? `#${path}?${search}` : `#${path}`
}

/**
 * Only in-app destinations are honored as "next", so a link cannot send people elsewhere after sign-in. A simple
 * query is kept (e.g. /radicar?issue=…).
 */
export function safeNext(value: string | null, fallback = '/'): string {
  return value && /^\/[A-Za-z0-9_\-/]*(\?[A-Za-z0-9_\-=&]*)?$/.test(value) ? value : fallback
}
