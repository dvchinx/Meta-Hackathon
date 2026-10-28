import type { ReactNode } from 'react'
import logo from '../assets/logo.svg'

/** Centered card used by every account screen. */
export function AuthPage({ title, intro, children, footer }: { title: string; intro?: ReactNode; children: ReactNode; footer?: ReactNode }) {
  return (
    <div className="page page-narrow">
      <div className="card auth-card">
        <img className="auth-logo" src={logo} alt="" />
        <h1>{title}</h1>
        {intro && <p className="auth-intro">{intro}</p>}
        {children}
      </div>
      {footer && <p className="auth-footer">{footer}</p>}
    </div>
  )
}
