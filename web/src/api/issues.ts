import type { IssueHistory, IssueSummary, VerificationReceipt } from '../issues/types'
import { parseJson } from './http'

const JSON_HEADERS = { Accept: 'application/json' }

export async function fetchIssues(signal?: AbortSignal): Promise<IssueSummary[]> {
  const response = await fetch('/api/issues', { signal, headers: JSON_HEADERS })
  if (!response.ok) {
    throw new Error(`GET /api/issues → ${response.status}`)
  }
  return (await response.json()) as IssueSummary[]
}

export async function fetchIssue(id: string, signal?: AbortSignal): Promise<IssueSummary> {
  return parseJson<IssueSummary>(await fetch(`/api/issues/${encodeURIComponent(id)}`, { signal, headers: JSON_HEADERS }))
}

export async function fetchIssueHistory(id: string, signal?: AbortSignal): Promise<IssueHistory> {
  return parseJson<IssueHistory>(
    await fetch(`/api/issues/${encodeURIComponent(id)}/history`, { signal, headers: JSON_HEADERS }),
  )
}

/** Before/after check. The session cookie identifies the citizen; the analysis takes a few seconds. */
export async function verifyRepair(id: string, photo: File): Promise<VerificationReceipt> {
  const form = new FormData()
  form.append('photo', photo)
  return parseJson<VerificationReceipt>(
    await fetch(`/api/issues/${encodeURIComponent(id)}/verifications`, { method: 'POST', body: form }),
  )
}
