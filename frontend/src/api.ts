export type Credentials = { user: string; password: string }

export async function api<T>(credentials: Credentials, path: string, method = 'GET', payload?: object): Promise<T> {
  try {
    const auth = btoa(`${credentials.user}:${String.fromCharCode(...new TextEncoder().encode(credentials.password))}`)
    const response = await fetch(`/api/v1${path}`, {
      method, credentials: 'omit',
      headers: {
        Authorization: `Basic ${auth}`,
        Accept: 'application/json',
        ...(method === 'POST' ? { 'X-ReleaseGuard-Intent': '1', 'Content-Type': 'application/json' } : {}),
      },
      body: payload ? JSON.stringify(payload) : undefined,
      cache: 'no-store',
      signal: AbortSignal.timeout(method === 'POST' ? 265_000 : 20_000),
    })
    if (!response.ok) {
      let detail = `Request returned HTTP ${response.status}.`
      if (response.status === 401) detail = 'Check your username and password.'
      else if (response.headers.get('content-type')?.includes('json')) {
        const problem: { detail?: string } = await response.json()
        detail = problem.detail || detail
      }
      throw new Error(detail)
    }
    return await response.json() as T
  } catch (e) {
    if (e instanceof Error) console.error(e.message, e)
    throw e
  }
}

export const label = (value: string) => value.toLowerCase().replaceAll('_', ' ').replace(/^[a-z]/, first => first.toUpperCase())
export const percent = (value: number) => new Intl.NumberFormat('en', {
  style: 'percent', maximumFractionDigits: 1,
}).format(value)
export const time = (value: string) => new Date(value).toLocaleTimeString([], {
  hour: '2-digit', minute: '2-digit', second: '2-digit',
})
export const dateTime = (value: string) => new Date(value).toLocaleString()
