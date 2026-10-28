import type { CSSProperties } from 'react'
import { DAMAGE_ICONS } from '../issues/damageIcons'
import { DAMAGE_LABELS } from '../issues/labels'
import type { DamageType } from '../issues/types'

/** The damage type's icon, drawn in the current text color like {@link Icon}. Labelled: it carries meaning. */
export function DamageIcon({ type, size = 20, decorative = false }: { type: DamageType; size?: number; decorative?: boolean }) {
  const url = `url("${DAMAGE_ICONS[type]}")`
  const style: CSSProperties = { width: size, height: size, maskImage: url, WebkitMaskImage: url }
  return decorative ? (
    <span className="icon" style={style} aria-hidden="true" />
  ) : (
    <span className="icon" style={style} role="img" aria-label={DAMAGE_LABELS[type]} />
  )
}
