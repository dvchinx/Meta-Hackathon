import type { CSSProperties } from 'react'
import { icons, type IconName } from './icons'

/**
 * Renders an icon file as a CSS mask so it takes the surrounding text color (currentColor).
 * Decorative by default; pass `label` when the icon carries meaning on its own.
 */
export function Icon({ name, size = 20, label, className }: { name: IconName; size?: number; label?: string; className?: string }) {
  const url = `url("${icons[name]}")`
  const style: CSSProperties = { width: size, height: size, maskImage: url, WebkitMaskImage: url }
  return (
    <span
      className={className ? `icon ${className}` : 'icon'}
      style={style}
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
    />
  )
}
