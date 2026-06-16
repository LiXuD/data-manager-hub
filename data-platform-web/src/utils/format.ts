/**
 * Format number with locale-specific formatting
 * @param num - Number to format
 * @param options - Formatting options
 * @param options.compact - Use compact notation (K, M) for large numbers
 */
export function formatNumber(num: number, options?: { compact?: boolean }): string {
  const locale = 'zh-CN'
  
  if (options?.compact) {
    const formatter = new Intl.NumberFormat(locale, {
      notation: 'compact',
      maximumFractionDigits: 1
    })
    return formatter.format(num)
  }
  
  return num.toLocaleString(locale)
}

/**
 * Format number as currency
 * @param num - Number to format
 * @param currency - Currency code (default: CNY)
 */
export function formatCurrency(num: number, currency = 'CNY'): string {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: currency,
    minimumFractionDigits: 2
  }).format(num)
}

/**
 * Format decimal with fixed precision
 * @param num - Number to format
 * @param precision - Decimal places (default: 2)
 */
export function formatDecimal(num: number, precision = 2): string {
  return num.toFixed(precision)
}

/**
 * Format percentage
 * @param num - Number to format (0-100)
 * @param precision - Decimal places (default: 1)
 */
export function formatPercent(num: number, precision = 1): string {
  return `${num.toFixed(precision)}%`
}
