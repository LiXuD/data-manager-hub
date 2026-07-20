// Helper to extract list data from various API response formats
export function extractPageData<T>(response: unknown): { list: T[]; total: number } {
  if (!response || typeof response !== 'object') {
    return { list: [], total: 0 }
  }

  const res = response as Record<string, unknown>

  const data = res.data
  const page = data && typeof data === 'object' && !Array.isArray(data)
    ? data as Record<string, unknown>
    : res
  const total = Number(page.total ?? res.total ?? (Array.isArray(data) ? data.length : 0))

  if (Array.isArray(page.list)) {
    return { list: page.list as T[], total }
  }

  if (Array.isArray(page.records)) {
    return { list: page.records as T[], total }
  }

  if (Array.isArray(data)) {
    return { list: data as T[], total }
  }

  return { list: [], total: 0 }
}
