import { useEffect, useRef, useState, type RefObject } from 'react'

export function prefersReducedMotion(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/**
 * True once the element has scrolled into view, and from then on. Without IntersectionObserver (old browsers,
 * tests) everything counts as visible, so no content stays hidden.
 */
export function useInView<T extends Element>(threshold = 0.2): [RefObject<T | null>, boolean] {
  const ref = useRef<T>(null)
  const [visible, setVisible] = useState(() => typeof IntersectionObserver === 'undefined')

  useEffect(() => {
    const element = ref.current
    if (!element || visible) return
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          setVisible(true)
          observer.disconnect()
        }
      },
      { threshold },
    )
    observer.observe(element)
    return () => observer.disconnect()
  }, [threshold, visible])

  return [ref, visible]
}

/** Counts from 0 to `target` in `duration` ms once `start` is true (ease-out). Jumps straight to it without motion. */
export function useCountUp(target: number, start: boolean, duration = 1400): number {
  const animate = !prefersReducedMotion() && typeof requestAnimationFrame === 'function'
  const [value, setValue] = useState(0)

  useEffect(() => {
    if (!start || !animate) return
    let frame = 0
    const began = performance.now()
    const tick = (now: number) => {
      const t = Math.min(1, (now - began) / duration)
      setValue(Math.round(target * (1 - (1 - t) ** 3)))
      if (t < 1) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [target, start, duration, animate])

  if (!start) return 0
  return animate ? value : target
}
