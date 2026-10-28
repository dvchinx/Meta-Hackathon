import type { DamageType, IssueStatus, IssueSummary, PetitionStatus } from './types'

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
  REPORTED: '#d92d20',
  FILED: '#e8590c',
  AGENCY_RESPONDED: '#ca8a04',
  REPAIR_CLAIMED: '#7a5af8',
  NEEDS_MORE_EVIDENCE: '#7a5af8',
  VERIFIED: '#16a34a',
  REOPENED: '#7a1414',
}

export const DAMAGE_LABELS: Record<DamageType, string> = {
  POTHOLE: 'Hueco',
  OPEN_MANHOLE: 'Alcantarilla destapada',
  DAMAGED_POLE: 'Poste dañado',
  RISKY_TREE: 'Árbol en riesgo',
  STREETLIGHT_OUT: 'Luminaria apagada',
  WATER_LEAK: 'Fuga de agua',
  BLOCKED_DRAIN: 'Sumidero tapado',
  GARBAGE_DUMP: 'Basuras o escombros',
  DAMAGED_SIDEWALK: 'Andén dañado',
  DAMAGED_STREET_FURNITURE: 'Mobiliario urbano dañado',
  OTHER: 'Otro daño',
}

/** A petition can be filed when none is in progress (REOPENED goes back to FILED) and an agency is known. */
export function canFile(issue: Pick<IssueSummary, 'status' | 'agency'>): boolean {
  return (issue.status === 'REPORTED' || issue.status === 'REOPENED') && issue.agency !== null
}

const dueFormat = new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'short', timeZone: 'UTC' })

/** Legal deadline (15 business days, Ley 1755 de 2015), while the agency has not answered. */
export function deadlineLabel(petition: PetitionStatus): string {
  // dueDate is a calendar date: format it as such, not in the browser's time zone.
  const due = dueFormat.format(new Date(`${petition.dueDate}T00:00:00Z`))
  if (petition.overdue) return `Plazo vencido el ${due} sin respuesta`
  if (petition.businessDaysLeft === 0) return 'El plazo para responder vence hoy'
  if (petition.businessDaysLeft === 1) return `Queda 1 día hábil para responder (vence el ${due})`
  return `Quedan ${petition.businessDaysLeft} días hábiles para responder (vence el ${due})`
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
