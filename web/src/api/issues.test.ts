import { afterEach, describe, expect, it, vi } from 'vitest'
import { fetchIssues } from './issues'

afterEach(() => vi.unstubAllGlobals())

describe('fetchIssues', () => {
  it('returns the issues from the public API', async () => {
    const issues = [{ id: '1', status: 'REPORTED' }]
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(issues), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(fetchIssues()).resolves.toEqual(issues)
    expect(fetchMock).toHaveBeenCalledWith('/api/issues', expect.anything())
  })

  it('fails on HTTP errors', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 503 })))

    await expect(fetchIssues()).rejects.toThrow('503')
  })
})
