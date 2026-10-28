import type { DamageType, IssueStatus, PetitionStatus } from '../issues/types'
import { parseJson, postJson } from './http'

export { ApiError as ReportApiError } from './http'

export type AnalysisOutcome = 'AWAITING_CONFIRMATION' | 'PHOTO_REJECTED' | 'MANUAL_REVIEW' | 'MODELS_UNAVAILABLE'
export type Urgency = 'LOW' | 'MEDIUM' | 'HIGH'
export type ReportStatus = 'COLLECTING' | 'ANALYZING' | 'AWAITING_CONFIRMATION' | 'CONFIRMED' | 'CANCELLED' | 'MANUAL_REVIEW'

export interface ReportAnalysis {
  reportId: string
  outcome: AnalysisOutcome
  damageType: DamageType | null
  urgency: Urgency | null
  summary: string | null
  locationHints: string[]
  agency: string | null
  locality: string | null
  markedPhotoUrl: string | null
  /** Immediate danger: show the 123 emergency line before anything else. */
  callEmergencyLine: boolean
}

export interface ReportConfirmation {
  reportId: string
  accepted: boolean
  issueId: string | null
  newIssue: boolean
  reportCount: number
}

export interface MyReport {
  reportId: string
  reportStatus: ReportStatus
  createdAt: string
  damageType: DamageType | null
  summary: string | null
  latitude: number
  longitude: number
  issueId: string | null
  issueStatus: IssueStatus | null
  issueReportCount: number
  photoUrl: string | null
  agency: string | null
  locality: string | null
  petition: PetitionStatus | null
  /** Latest petition this citizen filed for the damage: they can open the document. */
  myPetitionId: string | null
}

export interface Endorsement {
  issueId: string
  counted: boolean
  reportCount: number
}

export interface NewReport {
  photo: File
  latitude: number
  longitude: number
  text?: string
}

/** The session cookie identifies the citizen; the backend only accepts verified accounts. */
export async function submitReport(report: NewReport, signal?: AbortSignal): Promise<ReportAnalysis> {
  const form = new FormData()
  form.append('photo', report.photo)
  form.append('latitude', String(report.latitude))
  form.append('longitude', String(report.longitude))
  if (report.text?.trim()) form.append('text', report.text.trim())
  return parseJson<ReportAnalysis>(await fetch('/api/reports', { method: 'POST', body: form, signal }))
}

export async function confirmReport(reportId: string, accepted: boolean): Promise<ReportConfirmation> {
  return parseJson<ReportConfirmation>(
    await postJson(`/api/reports/${encodeURIComponent(reportId)}/confirmation`, { accepted }),
  )
}

export async function fetchMyReports(signal?: AbortSignal): Promise<MyReport[]> {
  return parseJson<MyReport[]>(await fetch('/api/me/reports', { signal, headers: { Accept: 'application/json' } }))
}

export async function endorseIssue(issueId: string): Promise<Endorsement> {
  return parseJson<Endorsement>(await postJson(`/api/issues/${encodeURIComponent(issueId)}/endorsements`))
}
