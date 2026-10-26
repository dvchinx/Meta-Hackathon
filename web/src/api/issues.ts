import type { IssueSummary } from '../issues/types'

export async function fetchIssues(signal?: AbortSignal): Promise<IssueSummary[]> {
  const response = await fetch('/api/issues', { signal, headers: { Accept: 'application/json' } })
  if (!response.ok) {
    throw new Error(`GET /api/issues → ${response.status}`)
  }
  return (await response.json()) as IssueSummary[]
}
