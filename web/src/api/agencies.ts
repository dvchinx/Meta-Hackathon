import { parseJson } from './http'

/** How one agency handles its damages. Counts are damages (Issues), except the responses. */
export interface AgencyScorecard {
  agencyCode: string
  agencyName: string
  issues: number
  open: number
  verified: number
  /** Mean age of the open damages; null when none is open. */
  averageDaysOpen: number | null
  filed: number
  /** Latest petition past its legal deadline with no reply since it was filed. */
  overduePetitions: number
  resolvingResponses: number
  /** Replies read as evasive or as no real answer. */
  evasiveResponses: number
  /** Damages the agency said it repaired at least once. */
  repairClaims: number
  /** Of those, repairs a new photo confirmed. */
  claimsConfirmed: number
  /** Reopened because a new photo showed the damage still there. */
  claimsRefuted: number
  /** Demo examples among the damages. */
  examples: number
}

export async function fetchScorecards(signal?: AbortSignal): Promise<AgencyScorecard[]> {
  return parseJson<AgencyScorecard[]>(
    await fetch('/api/agencies/scorecards', { signal, headers: { Accept: 'application/json' } }),
  )
}
