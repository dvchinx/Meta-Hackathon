import { lazy, Suspense, useCallback, useEffect, useMemo, useState } from 'react'
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
import { localitiesOf, matches, NO_FILTERS, within, type Bounds, type IssueFilters } from './issues/filters'
import type { IssueSummary } from './issues/types'
import type { EndorseHandler, MapFocus } from './map/IssueMap'
import { MapPanel } from './map/MapPanel'
import { MyReportsPage } from './me/MyReportsPage'
import { FilerProfilePage } from './petition/FilerProfilePage'
import { FilingPage } from './petition/FilingPage'
import { PrivacyPolicyPage } from './privacy/PrivacyPolicyPage'
import { currentRoute, href, navigate, type Route } from './routing'
import { Icon } from './ui/Icon'
import { Notice } from './ui/Notice'

// MapLibre is most of the bundle: the map, the report form (location picker) and the rarely used pages load on
// demand, so sign-in and the other light pages start fast.
const IssueMap = lazy(() => import('./map/IssueMap').then((m) => ({ default: m.IssueMap })))
const ReportForm = lazy(() => import('./report/ReportForm').then((m) => ({ default: m.ReportForm })))
const IssueDetailPage = lazy(() => import('./issue/IssueDetailPage').then((m) => ({ default: m.IssueDetailPage })))
const PetitionPage = lazy(() => import('./petition/PetitionPage').then((m) => ({ default: m.PetitionPage })))
const AdminPage = lazy(() => import('./admin/AdminPage').then((m) => ({ default: m.AdminPage })))

const loading = (
  <div className="page">
    <div className="spinner" aria-label="Cargando" />
  </div>
)

function App() {
  const [route, setRoute] = useState<Route>(currentRoute)
  const { state: auth } = useAuth()
  const [issues, setIssues] = useState<IssueSummary[]>([])
  const [error, setError] = useState(false)
  const [mapNotice, setMapNotice] = useState<string | null>(null)
  const [filters, setFilters] = useState<IssueFilters>(NO_FILTERS)
  const [bounds, setBounds] = useState<Bounds | null>(null)
  const [focus, setFocus] = useState<MapFocus | null>(null)

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

  const filtered = useMemo(() => issues.filter((i) => matches(i, filters)), [issues, filters])
  const visible = useMemo(() => (bounds ? filtered.filter((i) => within(i, bounds)) : filtered), [filtered, bounds])
  const localities = useMemo(() => localitiesOf(issues), [issues])

  const showMap = useCallback(() => navigate('/'), [])
  const select = useCallback((issue: IssueSummary) => setFocus({ id: issue.id, nonce: Date.now() }), [])

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
    case '/dano':
      page = <IssueDetailPage key={route.query.get('id')} issueId={route.query.get('id')} />
      break
    case '/radicar': {
      const issue = route.query.get('issue')
      page = (
        <RequireAccount next={issue ? `/radicar?issue=${issue}` : '/radicar'} purpose="radicar">
          <FilingPage issueId={issue} />
        </RequireAccount>
      )
      break
    }
    case '/peticion':
      page = (
        <RequireAccount next="/mis-reportes">
          <PetitionPage petitionId={route.query.get('id')} />
        </RequireAccount>
      )
      break
    case '/mis-datos':
      page = (
        <RequireAccount next="/mis-datos">
          <FilerProfilePage />
        </RequireAccount>
      )
      break
    case '/privacidad':
      page = <PrivacyPolicyPage />
      break
    case '/admin':
      page = <AdminPage />
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
            <MapPanel
              issues={filtered}
              visible={visible}
              localities={localities}
              filters={filters}
              onFiltersChange={setFilters}
              onSelect={select}
            />
            <div className="map-area">
              <Suspense fallback={<div className="issue-map map-loading" aria-label="Cargando el mapa" />}>
                <IssueMap issues={filtered} onEndorse={endorse} focus={focus} onViewChange={setBounds} />
              </Suspense>
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
          <main className="page-scroll">
            <Suspense fallback={loading}>{page}</Suspense>
          </main>
        )}
      </div>
    </div>
  )
}

export default App
