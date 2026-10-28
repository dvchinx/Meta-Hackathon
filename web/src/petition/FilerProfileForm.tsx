import { useId, useState } from 'react'
import { messageOf } from '../api/http'
import { saveFilerProfile, type FilerProfile, type IdType } from '../api/petitions'
import { href } from '../routing'
import { Field } from '../ui/Field'
import { Icon } from '../ui/Icon'
import { Notice } from '../ui/Notice'
import { ID_TYPE_LABELS } from './labels'

const EMPTY: FilerProfile = { idType: 'CC', idNumber: '', firstNames: '', lastNames: '', mobilePhone: '', address: null }

/**
 * The data agencies ask of a petitioner. Saving it means consenting to store it (encrypted) under the current data
 * policy, so the consent box is required every time.
 */
export function FilerProfileForm({
  initial,
  submitLabel,
  onSaved,
  onCancel,
}: {
  initial: FilerProfile | null
  submitLabel: string
  onSaved: (profile: FilerProfile) => void
  onCancel?: () => void
}) {
  const [form, setForm] = useState<FilerProfile>(initial ?? EMPTY)
  const [consent, setConsent] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const typeId = useId()
  const consentId = useId()

  const set = <K extends keyof FilerProfile>(key: K, value: FilerProfile[K]) => setForm((f) => ({ ...f, [key]: value }))
  const complete = form.idNumber.trim() && form.firstNames.trim() && form.lastNames.trim() && form.mobilePhone.trim()

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      onSaved(await saveFilerProfile({ ...form, address: form.address?.trim() ? form.address : null }))
    } catch (e) {
      setError(messageOf(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="stack" onSubmit={submit} noValidate>
      <div className="field-row">
        <div className="field">
          <label htmlFor={typeId} className="field-label">
            Tipo de documento
          </label>
          <select
            id={typeId}
            className="input"
            value={form.idType}
            onChange={(e) => set('idType', e.target.value as IdType)}
          >
            {Object.entries(ID_TYPE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>
        <Field
          label="Número de documento"
          inputMode={form.idType === 'CC' || form.idType === 'CE' || form.idType === 'TI' ? 'numeric' : 'text'}
          autoComplete="off"
          required
          maxLength={24}
          value={form.idNumber}
          onChange={(e) => set('idNumber', e.target.value)}
        />
      </div>
      <div className="field-row">
        <Field
          label="Nombres"
          autoComplete="given-name"
          required
          maxLength={60}
          value={form.firstNames}
          onChange={(e) => set('firstNames', e.target.value)}
        />
        <Field
          label="Apellidos"
          autoComplete="family-name"
          required
          maxLength={60}
          value={form.lastNames}
          onChange={(e) => set('lastNames', e.target.value)}
        />
      </div>
      <Field
        label="Celular"
        type="tel"
        inputMode="tel"
        autoComplete="tel-national"
        required
        maxLength={20}
        value={form.mobilePhone}
        onChange={(e) => set('mobilePhone', e.target.value)}
        hint="10 dígitos, por ejemplo 3001234567. La entidad puede usarlo para contactarte."
      />
      <Field
        label="Dirección (opcional)"
        autoComplete="street-address"
        maxLength={120}
        value={form.address ?? ''}
        onChange={(e) => set('address', e.target.value)}
        hint="Solo si quieres recibir la respuesta también por correo físico. La respuesta llega a tu correo."
      />

      <div className="checkbox">
        <input id={consentId} type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
        <label htmlFor={consentId}>
          Autorizo guardar estos datos cifrados para radicar peticiones a mi nombre, según la{' '}
          <a href={href('/privacidad')} target="_blank" rel="noreferrer">
            Política de tratamiento de datos
          </a>
          . Solo se comparten con la entidad ante la que radique, cuando radique.
        </label>
      </div>

      {error && <Notice tone="error">{error}</Notice>}
      <div className="form-actions">
        <button type="submit" className="button button-primary" disabled={busy || !complete || !consent}>
          {busy ? 'Guardando…' : submitLabel}
        </button>
        {onCancel && (
          <button type="button" className="button button-secondary" onClick={onCancel} disabled={busy}>
            Cancelar
          </button>
        )}
      </div>
      <p className="privacy">
        <Icon name="lock" size={16} />
        Nunca se publican. Puedes borrarlos cuando quieras.
      </p>
    </form>
  )
}

/** Read-only summary, with the document number partly hidden. */
export function FilerProfileSummary({ profile }: { profile: FilerProfile }) {
  const number = profile.idNumber.length > 4 ? `•••${profile.idNumber.slice(-4)}` : profile.idNumber
  return (
    <dl className="facts">
      <div className="wide">
        <dt>Nombre</dt>
        <dd>
          {profile.firstNames} {profile.lastNames}
        </dd>
      </div>
      <div>
        <dt>Documento</dt>
        <dd>
          {profile.idType} {number}
        </dd>
      </div>
      <div>
        <dt>Celular</dt>
        <dd>{profile.mobilePhone}</dd>
      </div>
      {profile.address && (
        <div className="wide">
          <dt>Dirección</dt>
          <dd>{profile.address}</dd>
        </div>
      )}
    </dl>
  )
}
