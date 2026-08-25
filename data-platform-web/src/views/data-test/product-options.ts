import type { CallerProductDTO } from '@/types'

export const filterGrantedActiveProducts = (
  products: CallerProductDTO[],
  grantedProductIds: number[]
) => {
  const grantedIds = new Set(grantedProductIds)
  return products.filter(product =>
    product.id !== undefined
    && product.status === 'active'
    && grantedIds.has(product.id))
}
