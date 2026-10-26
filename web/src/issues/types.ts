export type IssueStatus =
  | 'REPORTED'
  | 'FILED'
  | 'AGENCY_RESPONDED'
  | 'REPAIR_CLAIMED'
  | 'NEEDS_MORE_EVIDENCE'
  | 'VERIFIED'
  | 'REOPENED'

export type DamageType = 'POTHOLE' | 'OPEN_MANHOLE' | 'DAMAGED_POLE' | 'RISKY_TREE' | 'OTHER'

/** Public view of an Issue, as served by GET /api/issues. Never contains reporter data. */
export interface IssueSummary {
  id: string
  damageType: DamageType
  status: IssueStatus
  latitude: number
  longitude: number
  reportCount: number
  openedAt: string // ISO-8601
  agency: string | null
  /** Blurred photo only. */
  photoUrl: string | null
}
