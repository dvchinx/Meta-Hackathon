import type { IssueSummary } from '../issues/types'
import { parseJson } from './http'

export async function fetchIssues(signal?: AbortSignal): Promise<IssueSummary[]> {
  const response = await fetch('/api/issues', { signal, headers: { Accept: 'application/json' } })
  if (!response.ok) {
    throw new Error(`GET /api/issues → ${response.status}`)
  }
  return (await response.json()) as IssueSummary[]
}

export async function fetchIssue(id: string, signal?: AbortSignal): Promise<IssueSummary> {
  return parseJson<IssueSummary>(
    await fetch(`/api/issues/${encodeURIComponent(id)}`, { signal, headers: { Accept: 'application/json' } }),
  )
}
