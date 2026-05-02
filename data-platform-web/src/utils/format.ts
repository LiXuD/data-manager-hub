/**
 * Format number with locale-specific formatting
 * @param num - Number to format
 * @param options - Formatting options
 * @param options.compact - Use compact notation (K, M) for large numbers
 */
export function formatNumber(num: number, options?: { compact?: boolean }): string {
  if (options?.compact) {
    if (num >= 1000000) {
      return (num / 1000000).toFixed(1) + 'M'
    }
    if (num >= 1000) {
      return (num / 1000).toFixed(1) + 'K'
    }
  }
  return num.toLocaleString()
}

/**
 * Format number as currency
 * @param num - Number to format
 * @param currency - Currency symbol (default: ¥)
 */
export function formatCurrency(num: number, currency = '¥'): string {
  return `${currency}${num.toLocaleString()}`
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
