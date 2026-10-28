import { describe, expect, it } from 'vitest'
import { currentRoute, href, safeNext } from './routing'

describe('routing', () => {
  it('parses path and query from the hash', () => {
    window.location.hash = '#/verificar?token=abc&next=%2Freportar'
    const route = currentRoute()
    expect(route.path).toBe('/verificar')
    expect(route.query.get('token')).toBe('abc')
    expect(route.query.get('next')).toBe('/reportar')
  })

  it('builds hrefs with encoded queries', () => {
    expect(href('/entrar', { next: '/mis-reportes' })).toBe('#/entrar?next=%2Fmis-reportes')
    expect(href('/')).toBe('#/')
  })

  it('only honors in-app destinations as next', () => {
    expect(safeNext('/reportar')).toBe('/reportar')
    expect(safeNext('/radicar?issue=0b9e-11aa')).toBe('/radicar?issue=0b9e-11aa')
    expect(safeNext('/radicar?next=https://evil.example')).toBe('/')
    expect(safeNext('https://evil.example')).toBe('/')
    expect(safeNext('//evil.example')).toBe('/')
    expect(safeNext(null, '/x')).toBe('/x')
  })
})
