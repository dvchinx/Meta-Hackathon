import { useCallback, useEffect, useState } from 'react'
import { fetchIssues } from './api/issues'
import logo from './assets/logo.svg'
import type { IssueSummary } from './issues/types'
import { IssueMap } from './map/IssueMap'
import { MapPanel } from './map/MapPanel'
import { ReportForm } from './report/ReportForm'
import { Icon } from './ui/Icon'
import { Notice } from './ui/Notice'

type View = 'map' | 'report'

function viewFromHash(): View {
  return window.location.hash === '#/reportar' ? 'report' : 'map'
}

function App() {
  const [view, setView] = useState<View>(viewFromHash)
  const [issues, setIssues] = useState<IssueSummary[]>([])
  const [error, setError] = useState(false)

  useEffect(() => {
    const onHash = () => setView(viewFromHash())
    window.addEventListener('hashchange', onHash)
    return () => window.removeEventListener('hashchange', onHash)
  }, [])

  // Reload every time the map is shown, so a just-confirmed report appears.
  useEffect(() => {
    if (view !== 'map') return
    const controller = new AbortController()
    fetchIssues(controller.signal)
      .then((data) => {
        setIssues(data)
        setError(false)
      })
      .catch((e: unknown) => {
        if (!controller.signal.aborted) {
          console.error(e)
          setError(true)
        }
      })
    return () => controller.abort()
  }, [view])

  const showMap = useCallback(() => {
    window.location.hash = '#/'
  }, [])

  return (
    <div className="app">
      <header className="topbar">
        <a className="brand" href="#/">
          <img src={logo} alt="" />
          <span className="brand-text">
            <span className="brand-name">Daños urbanos</span>
            <span className="brand-place">Bogotá · Reportes ciudadanos</span>
          </span>
        </a>
        <nav className="topbar-nav" aria-label="Principal">
          <a
            className="button button-ghost"
            href="#/"
            aria-label="Mapa"
            aria-current={view === 'map' ? 'page' : undefined}
          >
            <Icon name="map" size={18} />
            <span className="nav-label">Mapa</span>
          </a>
          <a className="button button-primary" href="#/reportar" aria-current={view === 'report' ? 'page' : undefined}>
            <Icon name="plus" size={18} />
            Reportar
          </a>
        </nav>
      </header>

      <div className="app-body">
        {view === 'map' ? (
          <main className="map-layout">
            <MapPanel issues={issues} />
            <div className="map-area">
              <IssueMap issues={issues} />
              {error && (
                <div className="map-status">
                  <Notice tone="error">No pudimos cargar los reportes. Intenta de nuevo en un momento.</Notice>
                </div>
              )}
            </div>
          </main>
        ) : (
          <main className="page-scroll">
            <ReportForm onShowMap={showMap} />
          </main>
        )}
      </div>
    </div>
  )
}

export default App
