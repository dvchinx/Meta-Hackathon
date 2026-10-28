import type { AgencyResponseClassification, IssueStatus } from '../issues/types'
import { parseJson } from './http'

export interface AdminOverview {
  metaEnabled: boolean
  spentUsd: number
  budgetUsd: number
  issuesByStatus: Record<IssueStatus, number>
  manualReview: {
    reportId: string
    channel: 'WEB' | 'WHATSAPP'
    createdAt: string
    damageType: string | null
    summary: string | null
    reason: string | null
  }[]
}

export interface AgencyResponseResult {
  issueId: string
  classification: AgencyResponseClassification
  summary: string
  status: IssueStatus
}

/** The token travels in a header, never in the URL. */
function headers(token: string, json = false): HeadersInit {
  return { 'X-Admin-Token': token, Accept: 'application/json', ...(json ? { 'Content-Type': 'application/json' } : {}) }
}

export async function fetchOverview(token: string, signal?: AbortSignal): Promise<AdminOverview> {
  return parseJson<AdminOverview>(await fetch('/api/admin/overview', { signal, headers: headers(token) }))
}

export async function registerAgencyResponse(token: string, issueId: string, body: string): Promise<AgencyResponseResult> {
  return parseJson<AgencyResponseResult>(
    await fetch(`/api/admin/issues/${encodeURIComponent(issueId)}/agency-responses`, {
      method: 'POST',
      headers: headers(token, true),
      body: JSON.stringify({ body }),
    }),
  )
}
