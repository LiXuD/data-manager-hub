import { describe, expect, it } from 'vitest'
import { filterGrantedActiveProducts } from '../product-options'

describe('filterGrantedActiveProducts', () => {
  it('returns only active products authorized to the selected API Key', () => {
    const result = filterGrantedActiveProducts([
      { id: 1, productCode: 'authorized', productName: '已授权', status: 'active' },
      { id: 2, productCode: 'not-granted', productName: '未授权', status: 'active' },
      { id: 3, productCode: 'inactive', productName: '已停用', status: 'inactive' }
    ], [1, 3])

    expect(result.map(product => product.productCode)).toEqual(['authorized'])
  })
})
