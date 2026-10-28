import { useEffect, useRef, useState } from 'react'
import { href, navigate } from '../routing'
import { Icon } from '../ui/Icon'
import { useAuth } from './useAuth'

export function UserMenu({ currentPath }: { currentPath: string }) {
  const { state, signOut } = useAuth()
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const close = (e: MouseEvent) => {
      if (!root.current?.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [open])

  if (state.status === 'loading') return null

  if (state.status === 'anonymous') {
    return (
      <a className="button button-ghost" href={href('/entrar', { next: currentPath })}>
        <Icon name="user" size={18} />
        <span className="nav-label">Entrar</span>
      </a>
    )
  }

  const firstName = state.user.displayName.split(' ')[0]
  return (
    <div className="user-menu" ref={root}>
      <button
        type="button"
        className="button button-ghost"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
      >
        <Icon name="user" size={18} />
        <span className="nav-label">{firstName}</span>
      </button>
      {open && (
        <div className="menu" role="menu">
          <div className="menu-header">
            <strong>{state.user.displayName}</strong>
            <span className="muted">{state.user.email}</span>
          </div>
          <a role="menuitem" className="menu-item" href={href('/mis-reportes')} onClick={() => setOpen(false)}>
            <Icon name="list" size={16} />
            Mis reportes
          </a>
          <a role="menuitem" className="menu-item" href={href('/mis-datos')} onClick={() => setOpen(false)}>
            <Icon name="lock" size={16} />
            Mis datos para radicar
          </a>
          <button
            type="button"
            role="menuitem"
            className="menu-item"
            onClick={() => {
              setOpen(false)
              void signOut().then(() => navigate('/'))
            }}
          >
            <Icon name="logOut" size={16} />
            Salir
          </button>
        </div>
      )}
    </div>
  )
}
