import { useCallback, useEffect, useState } from 'react'
import { messageOf } from './api/http'
import { fetchIssues } from './api/issues'
import { endorseIssue } from './api/reports'
import logo from './assets/logo.svg'
import { useAuth } from './auth/useAuth'
import { ForgotPasswordPage } from './auth/ForgotPasswordPage'
import { LoginPage } from './auth/LoginPage'
import { RegisterPage } from './auth/RegisterPage'
import { RequireAccount } from './auth/RequireAccount'
import { ResetPasswordPage } from './auth/ResetPasswordPage'
import { UserMenu } from './auth/UserMenu'
import { VerifyEmailPage } from './auth/VerifyEmailPage'
import type { IssueSummary } from './issues/types'
import { IssueMap, type EndorseHandler } from './map/IssueMap'
import { MapPanel } from './map/MapPanel'
import { MyReportsPage } from './me/MyReportsPage'
import { ReportForm } from './report/ReportForm'
import { currentRoute, href, navigate, type Route } from './routing'
import { Icon } from './ui/Icon'
import { Notice } from './ui/Notice'

function App() {
  const [route, setRoute] = useState<Route>(currentRoute)
  const { state: auth } = useAuth()
  const [issues, setIssues] = useState<IssueSummary[]>([])
  const [error, setError] = useState(false)
  const [mapNotice, setMapNotice] = useState<string | null>(null)

  useEffect(() => {
    const onHash = () => setRoute(currentRoute())
    window.addEventListener('hashchange', onHash)
    return () => window.removeEventListener('hashchange', onHash)
  }, [])

  const onMap = route.path === '/'

  // Reload every time the map is shown, so a just-confirmed report appears.
  useEffect(() => {
    if (!onMap) return
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
  }, [onMap])

  const showMap = useCallback(() => navigate('/'), [])

  const endorse = useCallback<EndorseHandler>(
    async (issue) => {
      if (auth.status !== 'signedIn') {
        navigate('/entrar', { next: '/' })
        return null
      }
      if (!auth.user.emailVerified) {
        navigate('/verificar')
        return null
      }
      try {
        const result = await endorseIssue(issue.id)
        setIssues((all) => all.map((i) => (i.id === issue.id ? { ...i, reportCount: result.reportCount } : i)))
        return result
      } catch (e) {
        setMapNotice(messageOf(e))
        return null
      }
    },
    [auth],
  )

  const next = route.query.get('next')
  let page: React.ReactNode
  switch (route.path) {
    case '/reportar':
      page = (
        <RequireAccount next="/reportar">
          <ReportForm onShowMap={showMap} />
        </RequireAccount>
      )
      break
    case '/entrar':
      page = <LoginPage next={next} />
      break
    case '/registro':
      page = <RegisterPage next={next} />
      break
    case '/verificar':
      page = <VerifyEmailPage token={route.query.get('token')} next={next} />
      break
    case '/recuperar':
      page = <ForgotPasswordPage />
      break
    case '/restablecer':
      page = <ResetPasswordPage token={route.query.get('token')} />
      break
    case '/mis-reportes':
      page = (
        <RequireAccount next="/mis-reportes">
          <MyReportsPage />
        </RequireAccount>
      )
      break
    default:
      page = null
  }

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
          <a className="button button-ghost" href="#/" aria-label="Mapa" aria-current={onMap ? 'page' : undefined}>
            <Icon name="map" size={18} />
            <span className="nav-label">Mapa</span>
          </a>
          <UserMenu currentPath={route.path} />
          <a
            className="button button-primary"
            href={href('/reportar')}
            aria-current={route.path === '/reportar' ? 'page' : undefined}
          >
            <Icon name="plus" size={18} />
            Reportar
          </a>
        </nav>
      </header>

      <div className="app-body">
        {onMap ? (
          <main className="map-layout">
            <MapPanel issues={issues} />
            <div className="map-area">
              <IssueMap issues={issues} onEndorse={endorse} />
              {(error || mapNotice) && (
                <div className="map-status">
                  <Notice tone="error">
                    {error ? 'No pudimos cargar los reportes. Intenta de nuevo en un momento.' : mapNotice}
                  </Notice>
                </div>
              )}
            </div>
          </main>
        ) : (
          <main className="page-scroll">{page}</main>
        )}
      </div>
    </div>
  )
}

export default App
