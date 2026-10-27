import type { ReactNode } from 'react'
import { Icon } from './Icon'

type Tone = 'info' | 'warning' | 'error'

const ICON = { info: 'info', warning: 'alertTriangle', error: 'alertTriangle' } as const

export function Notice({ tone = 'info', children }: { tone?: Tone; children: ReactNode }) {
  return (
    <div className={`notice notice-${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      <Icon name={ICON[tone]} size={18} />
      <div>{children}</div>
    </div>
  )
}
