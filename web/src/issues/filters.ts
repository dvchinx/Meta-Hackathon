import type { DamageType, IssueSummary } from './types'

export type StatusFilter = 'all' | 'open' | 'overdue' | 'verified'

export interface IssueFilters {
  status: StatusFilter
  /** Empty means every type. */
  types: DamageType[]
  locality: string | null
}

export const NO_FILTERS: IssueFilters = { status: 'all', types: [], locality: null }

/** The agency got the petition and let the legal deadline pass without answering. */
export function isOverdue(issue: IssueSummary): boolean {
  return issue.status === 'FILED' && issue.petition?.overdue === true
}

export function matches(issue: IssueSummary, f: IssueFilters): boolean {
  if (f.types.length > 0 && !f.types.includes(issue.damageType)) return false
  if (f.locality && issue.locality !== f.locality) return false
  switch (f.status) {
    case 'open':
      return issue.status !== 'VERIFIED'
    case 'overdue':
      return isOverdue(issue)
    case 'verified':
      return issue.status === 'VERIFIED'
    default:
      return true
  }
}

export function isFiltered(f: IssueFilters): boolean {
  return f.status !== 'all' || f.types.length > 0 || f.locality !== null
}

export function localitiesOf(issues: IssueSummary[]): string[] {
  return [...new Set(issues.map((i) => i.locality).filter((l): l is string => l !== null))].sort((a, b) =>
    a.localeCompare(b, 'es'),
  )
}

export interface GroupStats {
  name: string
  open: number
  overdue: number
  verified: number
}

/** Open, overdue and verified damage per locality or per agency, most open first. */
export function statsBy(issues: IssueSummary[], key: 'locality' | 'agency'): GroupStats[] {
  const groups = new Map<string, GroupStats>()
  for (const issue of issues) {
    const name = issue[key]
    if (!name) continue
    const group = groups.get(name) ?? { name, open: 0, overdue: 0, verified: 0 }
    if (issue.status === 'VERIFIED') group.verified++
    else group.open++
    if (isOverdue(issue)) group.overdue++
    groups.set(name, group)
  }
  return [...groups.values()].sort((a, b) => b.open - a.open || b.overdue - a.overdue || a.name.localeCompare(b.name, 'es'))
}

const DAY_MS = 24 * 60 * 60 * 1000

/** Average whole days that open damage has been waiting; null when nothing is open. */
export function averageDaysOpen(issues: IssueSummary[], now: Date = new Date()): number | null {
  const open = issues.filter((i) => i.status !== 'VERIFIED')
  if (open.length === 0) return null
  const days = open.reduce((sum, i) => sum + Math.max(0, now.getTime() - new Date(i.openedAt).getTime()) / DAY_MS, 0)
  return Math.round(days / open.length)
}

/** [west, south, east, north] */
export type Bounds = [number, number, number, number]

export function within(issue: IssueSummary, b: Bounds): boolean {
  return issue.longitude >= b[0] && issue.longitude <= b[2] && issue.latitude >= b[1] && issue.latitude <= b[3]
}
