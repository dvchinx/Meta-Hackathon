import { STATUS_COLORS, STATUS_LABELS } from '../issues/labels'
import type { IssueStatus, IssueSummary } from '../issues/types'
import { href } from '../routing'
import { Icon } from '../ui/Icon'

const LEGEND: IssueStatus[] = ['REPORTED', 'FILED', 'AGENCY_RESPONDED', 'REPAIR_CLAIMED', 'REOPENED', 'VERIFIED']

const numbers = new Intl.NumberFormat('es-CO')

export function MapPanel({ issues }: { issues: IssueSummary[] }) {
  const countBy = (status: IssueStatus) =>
    issues.filter((i) => i.status === status || (status === 'REPAIR_CLAIMED' && i.status === 'NEEDS_MORE_EVIDENCE')).length
  const verified = countBy('VERIFIED')
  const reports = issues.reduce((sum, i) => sum + i.reportCount, 0)

  return (
    <aside className="map-panel" aria-label="Resumen de los reportes">
      <div className="panel-heading">
        <h1>Daños reportados en Bogotá</h1>
        <p>
          Huecos, alcantarillas, postes, árboles, alumbrado, agua, basuras y andenes reportados por ciudadanos, con su
          estado de reparación.
        </p>
      </div>

      <dl className="stats">
        <div className="stat">
          <dt>Abiertos</dt>
          <dd>{numbers.format(issues.length - verified)}</dd>
        </div>
        <div className="stat">
          <dt>Verificados</dt>
          <dd>{numbers.format(verified)}</dd>
        </div>
        <div className="stat">
          <dt>Reportes</dt>
          <dd>{numbers.format(reports)}</dd>
        </div>
      </dl>

      <section>
        <h2 className="section-title">Estados</h2>
        <ul className="legend">
          {LEGEND.map((status) => (
            <li key={status}>
              <span className="dot" style={{ background: STATUS_COLORS[status] }} />
              {STATUS_LABELS[status]}
              <span className="count">{countBy(status)}</span>
            </li>
          ))}
        </ul>
      </section>

      <p className="rule">
        <Icon name="shield" size={18} />
        <span>
          <strong>Solo una foto nueva cierra un caso.</strong> Que la entidad diga que lo arregló no basta: un ciudadano
          debe confirmar la reparación con una foto.
        </span>
      </p>

      <p className="panel-footer">
        <a href={href('/privacidad')}>Política de tratamiento de datos</a>
      </p>
    </aside>
  )
}
