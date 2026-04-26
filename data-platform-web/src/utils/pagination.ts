// Helper to extract list data from various API response formats
export function extractPageData<T>(response: unknown): { list: T[]; total: number } {
  if (!response || typeof response !== 'object') {
    return { list: [], total: 0 }
  }

  const res = response as Record<string, unknown>

  // Handle PageResult format { list: T[], total: number }
  if ('list' in res && Array.isArray(res.list)) {
    return { list: res.list as T[], total: (res.total as number) || 0 }
  }

  // Handle { records: T[], total: number } format
  if ('records' in res && Array.isArray(res.records)) {
    return { list: res.records as T[], total: (res.total as number) || 0 }
  }

  // Handle { data: T[] } format
  const data = res.data
  if (data && typeof data === 'object') {
    // { data: { records: T[], total: number } }
    const dataObj = data as Record<string, unknown>
    if ('records' in dataObj && Array.isArray(dataObj.records)) {
      return { list: dataObj.records as T[], total: (dataObj.total as number) || 0 }
    }
    // { data: T[] }
    if (Array.isArray(data)) {
      return { list: data as T[], total: (res.total as number) || 0 }
    }
  }

  return { list: [], total: 0 }
}
