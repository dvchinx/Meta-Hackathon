import type { DamageType } from '../issues/types'
import { reporterToken } from '../report/reporterToken'

export type AnalysisOutcome = 'AWAITING_CONFIRMATION' | 'PHOTO_REJECTED' | 'MANUAL_REVIEW' | 'MODELS_UNAVAILABLE'
export type Urgency = 'LOW' | 'MEDIUM' | 'HIGH'

export interface ReportAnalysis {
  reportId: string
  outcome: AnalysisOutcome
  damageType: DamageType | null
  urgency: Urgency | null
  summary: string | null
  locationHints: string[]
  agency: string | null
  markedPhotoUrl: string | null
}

export interface ReportConfirmation {
  reportId: string
  accepted: boolean
  issueId: string | null
  newIssue: boolean
  reportCount: number
}

/** Error from the API, with a Spanish message safe to show. */
export class ReportApiError extends Error {
  readonly code: string

  constructor(code: string, message: string) {
    super(message)
    this.code = code
  }
}

const GENERIC = 'No pudimos enviar el reporte. Revisa tu conexión e intenta de nuevo.'

async function parse<T>(response: Response): Promise<T> {
  if (response.ok) return (await response.json()) as T
  let code = `HTTP_${response.status}`
  let message = GENERIC
  try {
    const body = (await response.json()) as { code?: string; message?: string }
    if (body.code) code = body.code
    if (body.message) message = body.message
  } catch {
    // Not a JSON error body: keep the generic message.
  }
  throw new ReportApiError(code, message)
}

export interface NewReport {
  photo: File
  latitude: number
  longitude: number
  text?: string
}

export async function submitReport(report: NewReport, signal?: AbortSignal): Promise<ReportAnalysis> {
  const form = new FormData()
  form.append('photo', report.photo)
  form.append('latitude', String(report.latitude))
  form.append('longitude', String(report.longitude))
  if (report.text?.trim()) form.append('text', report.text.trim())
  const response = await fetch('/api/reports', {
    method: 'POST',
    body: form,
    headers: { 'X-Reporter-Token': reporterToken() },
    signal,
  })
  return parse<ReportAnalysis>(response)
}

export async function confirmReport(reportId: string, accepted: boolean): Promise<ReportConfirmation> {
  const response = await fetch(`/api/reports/${encodeURIComponent(reportId)}/confirmation`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Reporter-Token': reporterToken() },
    body: JSON.stringify({ accepted }),
  })
  return parse<ReportConfirmation>(response)
}
