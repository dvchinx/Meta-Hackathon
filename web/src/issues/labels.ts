import type { DamageType, IssueStatus } from './types'

export const STATUS_LABELS: Record<IssueStatus, string> = {
  REPORTED: 'Reportado',
  FILED: 'Petición radicada',
  AGENCY_RESPONDED: 'La entidad respondió',
  REPAIR_CLAIMED: 'La entidad dice que lo arregló',
  NEEDS_MORE_EVIDENCE: 'Falta una foto clara',
  VERIFIED: 'Reparación verificada',
  REOPENED: 'Reabierto: sigue dañado',
}

/** Open statuses are warm, the only closed one is green: "the agency says so" never looks resolved. */
export const STATUS_COLORS: Record<IssueStatus, string> = {
  REPORTED: '#d62828',
  FILED: '#f77f00',
  AGENCY_RESPONDED: '#fcbf49',
  REPAIR_CLAIMED: '#a663cc',
  NEEDS_MORE_EVIDENCE: '#a663cc',
  VERIFIED: '#2a9d8f',
  REOPENED: '#9d0208',
}

export const DAMAGE_LABELS: Record<DamageType, string> = {
  POTHOLE: 'Hueco',
  OPEN_MANHOLE: 'Alcantarilla destapada',
  DAMAGED_POLE: 'Poste dañado',
  RISKY_TREE: 'Árbol en riesgo',
  OTHER: 'Otro daño',
}

const DAY_MS = 24 * 60 * 60 * 1000

export function timeOpen(openedAt: string, now: Date = new Date()): string {
  const days = Math.max(0, Math.floor((now.getTime() - new Date(openedAt).getTime()) / DAY_MS))
  if (days === 0) return 'Abierto hoy'
  if (days === 1) return 'Abierto hace 1 día'
  return `Abierto hace ${days} días`
}

export function reportsLabel(count: number): string {
  return count === 1 ? '1 reporte' : `${count} reportes`
}
