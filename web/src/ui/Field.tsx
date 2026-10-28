import { useId, type InputHTMLAttributes, type ReactNode } from 'react'

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  hint?: ReactNode
  error?: string | null
}

/** A labelled text input with optional hint and inline error. */
export function Field({ label, hint, error, id, className, ...input }: FieldProps) {
  const generated = useId()
  const inputId = id ?? generated
  return (
    <div className={className ? `field ${className}` : 'field'}>
      <label htmlFor={inputId} className="field-label">
        {label}
      </label>
      <input
        id={inputId}
        className={error ? 'input input-invalid' : 'input'}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${inputId}-error` : hint ? `${inputId}-hint` : undefined}
        {...input}
      />
      {error ? (
        <p id={`${inputId}-error`} className="field-error">
          {error}
        </p>
      ) : (
        hint && (
          <p id={`${inputId}-hint`} className="field-hint">
            {hint}
          </p>
        )
      )}
    </div>
  )
}
