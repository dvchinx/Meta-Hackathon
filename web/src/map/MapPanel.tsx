import { useId, useState } from 'react'
import {
  averageDaysOpen,
  isFiltered,
  isOverdue,
  NO_FILTERS,
  statsBy,
  type GroupStats,
  type IssueFilters,
  type StatusFilter,
} from '../issues/filters'
import { DAMAGE_LABELS, STATUS_COLORS, STATUS_LABELS, reportsLabel, timeOpen } from '../issues/labels'
import type { DamageType, IssueStatus, IssueSummary } from '../issues/types'
import { href } from '../routing'
import { DamageIcon } from '../ui/DamageIcon'
import { Icon } from '../ui/Icon'

const LEGEND: IssueStatus[] = ['REPORTED', 'FILED', 'AGENCY_RESPONDED', 'REPAIR_CLAIMED', 'REOPENED', 'VERIFIED']
const STATUS_FILTERS: { value: StatusFilter; label: string }[] = [
  { value: 'all', label: 'Todos' },
  { value: 'open', label: 'Abiertos' },
  { value: 'overdue', label: 'Vencidos' },
  { value: 'verified', label: 'Verificados' },
]
const TYPES = (Object.keys(DAMAGE_LABELS) as DamageType[]).filter((t) => t !== 'OTHER')
const MAX_LIST = 100

const numbers = new Intl.NumberFormat('es-CO')

type Tab = 'list' | 'summary'

export function MapPanel({
  issues,
  visible,
  localities,
  filters,
  onFiltersChange,
  onSelect,
}: {
  /** After filters. */
  issues: IssueSummary[]
  /** After filters, inside the current map view. */
  visible: IssueSummary[]
  localities: string[]
  filters: IssueFilters
  onFiltersChange: (filters: IssueFilters) => void
  onSelect: (issue: IssueSummary) => void
}) {
  const [tab, setTab] = useState<Tab>('list')
  const localityId = useId()
  const verified = issues.filter((i) => i.status === 'VERIFIED').length
  const overdue = issues.filter(isOverdue).length
  const reports = issues.reduce((sum, i) => sum + i.reportCount, 0)
  const avgDays = averageDaysOpen(issues)

  const toggleType = (type: DamageType) =>
    onFiltersChange({
      ...filters,
      types: filters.types.includes(type) ? filters.types.filter((t) => t !== type) : [...filters.types, type],
    })

  return (
    <aside className="map-panel" aria-label="Daños reportados">
      <div className="panel-heading">
        <h1>Daños reportados en Bogotá</h1>
        <p>Lo que reportan los ciudadanos, ante qué entidad y en qué va la reparación.</p>
      </div>

      <section className="filters" aria-label="Filtros">
        <div className="segmented" role="radiogroup" aria-label="Estado">
          {STATUS_FILTERS.map((s) => (
            <button
              key={s.value}
              type="button"
              role="radio"
              aria-checked={filters.status === s.value}
              className={filters.status === s.value ? 'segment is-active' : 'segment'}
              onClick={() => onFiltersChange({ ...filters, status: s.value })}
            >
              {s.label}
            </button>
          ))}
        </div>
        <details className="type-filter">
          <summary>
            Tipo de daño:{' '}
            <strong>
              {filters.types.length === 0
                ? 'todos'
                : filters.types.length === 1
                  ? DAMAGE_LABELS[filters.types[0]]
                  : `${filters.types.length} elegidos`}
            </strong>
          </summary>
          <div className="chips" role="group" aria-label="Tipo de daño">
            {TYPES.map((type) => {
              const on = filters.types.includes(type)
              return (
                <button
                  key={type}
                  type="button"
                  className={on ? 'chip is-active' : 'chip'}
                  aria-pressed={on}
                  onClick={() => toggleType(type)}
                  title={DAMAGE_LABELS[type]}
                >
                  <DamageIcon type={type} size={16} decorative />
                  {DAMAGE_LABELS[type]}
                </button>
              )
            })}
          </div>
        </details>
        <div className="filter-row">
          <label htmlFor={localityId} className="visually-hidden">
            Localidad
          </label>
          <select
            id={localityId}
            className="input input-sm"
            value={filters.locality ?? ''}
            onChange={(e) => onFiltersChange({ ...filters, locality: e.target.value || null })}
          >
            <option value="">Todas las localidades</option>
            {localities.map((l) => (
              <option key={l} value={l}>
                {l}
              </option>
            ))}
          </select>
          {isFiltered(filters) && (
            <button type="button" className="button button-ghost button-sm" onClick={() => onFiltersChange(NO_FILTERS)}>
              Quitar filtros
            </button>
          )}
        </div>
      </section>

      <div className="tabs" role="tablist" aria-label="Vista">
        <button type="button" role="tab" aria-selected={tab === 'list'} className="tab" onClick={() => setTab('list')}>
          En el mapa <span className="count">{numbers.format(visible.length)}</span>
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'summary'}
          className="tab"
          onClick={() => setTab('summary')}
        >
          Resumen
        </button>
      </div>

      {tab === 'list' ? (
        <IssueList issues={visible} onSelect={onSelect} />
      ) : (
        <div className="panel-summary">
          <dl className="stats">
            <div className="stat">
              <dt>Abiertos</dt>
              <dd>{numbers.format(issues.length - verified)}</dd>
            </div>
            <div className="stat">
              <dt>Plazo vencido</dt>
              <dd className={overdue > 0 ? 'is-alert' : undefined}>{numbers.format(overdue)}</dd>
            </div>
            <div className="stat">
              <dt>Verificados</dt>
              <dd>{numbers.format(verified)}</dd>
            </div>
          </dl>
          <p className="muted summary-line">
            {reportsLabel(reports)} ciudadanos
            {avgDays !== null && ` · los daños abiertos llevan en promedio ${avgDays} ${avgDays === 1 ? 'día' : 'días'}`}
          </p>

          <section>
            <h2 className="section-title">Estados</h2>
            <ul className="legend">
              {LEGEND.map((status) => (
                <li key={status}>
                  <span className="dot" style={{ background: STATUS_COLORS[status] }} />
                  {STATUS_LABELS[status]}
                  <span className="count">
                    {
                      issues.filter(
                        (i) =>
                          i.status === status || (status === 'REPAIR_CLAIMED' && i.status === 'NEEDS_MORE_EVIDENCE'),
                      ).length
                    }
                  </span>
                </li>
              ))}
            </ul>
          </section>

          <GroupTable title="Por localidad" rows={statsBy(issues, 'locality')} />
          <GroupTable title="Por entidad" rows={statsBy(issues, 'agency')} />

          <p className="rule">
            <Icon name="shield" size={18} />
            <span>
              <strong>Solo una foto nueva cierra un caso.</strong> Que la entidad diga que lo arregló no basta: un
              ciudadano debe confirmar la reparación con una foto.
            </span>
          </p>
        </div>
      )}

      <p className="panel-footer">
        <a href={href('/privacidad')}>Política de tratamiento de datos</a>
      </p>
    </aside>
  )
}

function IssueList({ issues, onSelect }: { issues: IssueSummary[]; onSelect: (issue: IssueSummary) => void }) {
  if (issues.length === 0) {
    return (
      <p className="muted empty-list">
        No hay daños en esta parte del mapa con estos filtros. Aleja el mapa o quita filtros.
      </p>
    )
  }
  // Most reported first: what more people suffer goes on top.
  const sorted = [...issues].sort((a, b) => b.reportCount - a.reportCount || b.openedAt.localeCompare(a.openedAt))
  return (
    <ul className="issue-list">
      {sorted.slice(0, MAX_LIST).map((issue) => (
        <li key={issue.id}>
          <button type="button" className="issue-row" onClick={() => onSelect(issue)}>
            <span className="issue-row-icon" style={{ background: STATUS_COLORS[issue.status] }}>
              <DamageIcon type={issue.damageType} size={18} decorative />
            </span>
            <span className="issue-row-body">
              <span className="issue-row-title">
                {DAMAGE_LABELS[issue.damageType]}
                {issue.locality && <span className="muted"> · {issue.locality}</span>}
              </span>
              <span className="issue-row-meta">
                {STATUS_LABELS[issue.status]} · {reportsLabel(issue.reportCount)} · {timeOpen(issue.openedAt)}
              </span>
              {isOverdue(issue) && <span className="issue-row-alert">Plazo vencido sin respuesta</span>}
            </span>
          </button>
        </li>
      ))}
      {sorted.length > MAX_LIST && <li className="muted empty-list">Acerca el mapa para ver el resto.</li>}
    </ul>
  )
}

function GroupTable({ title, rows }: { title: string; rows: GroupStats[] }) {
  if (rows.length === 0) return null
  const max = Math.max(...rows.map((r) => r.open), 1)
  return (
    <section>
      <h2 className="section-title">{title}</h2>
      <table className="group-table">
        <thead>
          <tr>
            <th scope="col">{title === 'Por entidad' ? 'Entidad' : 'Localidad'}</th>
            <th scope="col">Abiertos</th>
            <th scope="col">Vencidos</th>
          </tr>
        </thead>
        <tbody>
          {rows.slice(0, 8).map((r) => (
            <tr key={r.name}>
              <th scope="row">{r.name}</th>
              <td>
                <span className="bar" style={{ width: `${Math.max(8, (r.open / max) * 64)}px` }} aria-hidden="true" />
                {r.open}
              </td>
              <td className={r.overdue > 0 ? 'is-alert' : undefined}>{r.overdue}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}
