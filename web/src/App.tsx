import { useEffect, useState } from 'react'
import { fetchIssues } from './api/issues'
import { STATUS_COLORS, STATUS_LABELS } from './issues/labels'
import type { IssueStatus, IssueSummary } from './issues/types'
import { IssueMap } from './map/IssueMap'

const LEGEND: IssueStatus[] = ['REPORTED', 'FILED', 'AGENCY_RESPONDED', 'REPAIR_CLAIMED', 'REOPENED', 'VERIFIED']

function App() {
  const [issues, setIssues] = useState<IssueSummary[]>([])
  const [error, setError] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    fetchIssues(controller.signal)
      .then(setIssues)
      .catch((e: unknown) => {
        if (!controller.signal.aborted) {
          console.error(e)
          setError(true)
        }
      })
    return () => controller.abort()
  }, [])

  return (
    <div className="app">
      <header className="app-header">
        <h1>Daños urbanos en Bogotá</h1>
        <p>Reportados por ciudadanos. Solo se marcan como reparados cuando una foto nueva lo confirma.</p>
      </header>
      {error && <div className="banner">No pudimos cargar los reportes. Intenta de nuevo en un momento.</div>}
      <main className="app-main">
        <IssueMap issues={issues} />
        <aside className="legend" aria-label="Convenciones">
          {LEGEND.map((status) => (
            <span key={status}>
              <i style={{ background: STATUS_COLORS[status] }} />
              {STATUS_LABELS[status]}
            </span>
          ))}
        </aside>
      </main>
    </div>
  )
}

export default App
