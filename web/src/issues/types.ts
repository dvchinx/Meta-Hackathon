export type IssueStatus =
  | 'REPORTED'
  | 'FILED'
  | 'AGENCY_RESPONDED'
  | 'REPAIR_CLAIMED'
  | 'NEEDS_MORE_EVIDENCE'
  | 'VERIFIED'
  | 'REOPENED'

export type DamageType =
  | 'POTHOLE'
  | 'OPEN_MANHOLE'
  | 'DAMAGED_POLE'
  | 'RISKY_TREE'
  | 'STREETLIGHT_OUT'
  | 'WATER_LEAK'
  | 'BLOCKED_DRAIN'
  | 'GARBAGE_DUMP'
  | 'DAMAGED_SIDEWALK'
  | 'DAMAGED_STREET_FURNITURE'
  | 'OTHER'

/** Where to report for real: our filing is simulated. */
export interface OfficialChannel {
  url: string
  /** e.g. "Acualínea 116"; null when there is no verified line. */
  phone: string | null
}

/** Latest petition of an Issue. Public: never says who filed it. */
export interface PetitionStatus {
  radicado: string
  filedAt: string // ISO-8601
  dueDate: string // YYYY-MM-DD, Bogotá
  /** Business days left as of today; 0 on the due date and after it. */
  businessDaysLeft: number
  overdue: boolean
}

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
  locality: string | null
  officialChannel: OfficialChannel | null
  petition: PetitionStatus | null
}
