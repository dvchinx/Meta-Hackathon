import { useEffect, useState } from 'react'
import { messageOf } from '../api/http'
import { deleteFilerProfile, fetchFilerProfile, type FilerProfile } from '../api/petitions'
import { Icon } from '../ui/Icon'
import { Notice } from '../ui/Notice'
import { FilerProfileForm, FilerProfileSummary } from './FilerProfileForm'

type State =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; profile: FilerProfile | null; editing: boolean; notice?: string }

/** "Mis datos para radicar": see, edit or delete the filing data. */
export function FilerProfilePage() {
  const [state, setState] = useState<State>({ kind: 'loading' })
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    fetchFilerProfile(controller.signal)
      .then((profile) => setState({ kind: 'ready', profile, editing: profile === null }))
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setState({ kind: 'error', message: messageOf(e) })
      })
    return () => controller.abort()
  }, [])

  async function remove() {
    setBusy(true)
    try {
      await deleteFilerProfile()
      setState({ kind: 'ready', profile: null, editing: true, notice: 'Borramos tus datos para radicar.' })
    } catch (e) {
      setState({ kind: 'error', message: messageOf(e) })
    } finally {
      setBusy(false)
      setConfirmDelete(false)
    }
  }

  return (
    <div className="page">
      <header className="page-header">
        <h1>Mis datos para radicar</h1>
        <p>
          Las entidades piden estos datos para tramitar un derecho de petición. Son opcionales: solo los necesitas si
          quieres radicar a tu nombre.
        </p>
      </header>

      {state.kind === 'loading' && <div className="spinner" aria-label="Cargando" />}
      {state.kind === 'error' && <Notice tone="error">{state.message}</Notice>}
      {state.kind === 'ready' && (
        <div className="card form-card">
          {state.notice && (
            <div className="step">
              <Notice tone="info">{state.notice}</Notice>
            </div>
          )}
          <div className="step">
            {state.editing ? (
              <FilerProfileForm
                initial={state.profile}
                submitLabel="Guardar mis datos"
                onSaved={(profile) => setState({ kind: 'ready', profile, editing: false, notice: 'Guardamos tus datos.' })}
                onCancel={state.profile ? () => setState({ ...state, editing: false, notice: undefined }) : undefined}
              />
            ) : (
              state.profile && (
                <div className="stack">
                  <FilerProfileSummary profile={state.profile} />
                  <div className="form-actions">
                    <button
                      type="button"
                      className="button button-secondary"
                      onClick={() => setState({ ...state, editing: true, notice: undefined })}
                    >
                      Editar
                    </button>
                    {confirmDelete ? (
                      <>
                        <button type="button" className="button button-danger" onClick={remove} disabled={busy}>
                          Sí, borrar mis datos
                        </button>
                        <button type="button" className="button button-ghost" onClick={() => setConfirmDelete(false)}>
                          No
                        </button>
                      </>
                    ) : (
                      <button type="button" className="button button-ghost" onClick={() => setConfirmDelete(true)}>
                        Borrar mis datos
                      </button>
                    )}
                  </div>
                  <p className="privacy">
                    <Icon name="info" size={16} />
                    Borrarlos no cambia las peticiones que ya radicaste: cada una conserva los datos con que se radicó.
                  </p>
                </div>
              )
            )}
          </div>
        </div>
      )}
    </div>
  )
}
