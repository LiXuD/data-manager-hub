import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getVendorAll } from '@/api/vendor'
import { getDataTypeAll } from '@/api/datatype'
import type { Vendor, DataType } from '@/types'

const CACHE_TTL = 5 * 60 * 1000 // 5 minutes

export const useCacheStore = defineStore('cache', () => {
  const vendorOptions = ref<Vendor[]>([])
  const dataTypeOptions = ref<DataType[]>([])

  const vendorsLoadedAt = ref(0)
  const dataTypesLoadedAt = ref(0)

  // Promise tracking to prevent duplicate requests
  let vendorsPromise: Promise<Vendor[]> | null = null
  let dataTypesPromise: Promise<DataType[]> | null = null

  const isVendorsFresh = () => {
    return vendorOptions.value.length > 0 &&
           Date.now() - vendorsLoadedAt.value < CACHE_TTL
  }

  const isDataTypesFresh = () => {
    return dataTypeOptions.value.length > 0 &&
           Date.now() - dataTypesLoadedAt.value < CACHE_TTL
  }

  const loadVendors = async (force = false): Promise<Vendor[]> => {
    if (!force && isVendorsFresh()) {
      return vendorOptions.value
    }

    // Return existing promise if request is in flight
    if (vendorsPromise) {
      return vendorsPromise
    }

    vendorsPromise = getVendorAll()
      .then(res => {
        // 使用简单的 /all 端点，响应格式是 { code: 0, data: [...] }
        let list: Vendor[] = []
        if (res && Array.isArray(res.data)) {
          list = res.data
        }
        vendorOptions.value = list
        vendorsLoadedAt.value = Date.now()
        return vendorOptions.value
      })
      .catch(error => {
        console.error('加载厂商失败:', error)
        return []
      })
      .finally(() => {
        vendorsPromise = null
      })

    return vendorsPromise
  }

  const loadDataTypes = async (force = false): Promise<DataType[]> => {
    if (!force && isDataTypesFresh()) {
      return dataTypeOptions.value
    }

    // Return existing promise if request is in flight
    if (dataTypesPromise) {
      return dataTypesPromise
    }

    dataTypesPromise = getDataTypeAll()
      .then(res => {
        // 使用简单的 /all 端点，响应格式是 { code: 0, data: [...] }
        let list: DataType[] = []
        if (res && Array.isArray(res.data)) {
          list = res.data
        }
        dataTypeOptions.value = list
        dataTypesLoadedAt.value = Date.now()
        return dataTypeOptions.value
      })
      .catch(error => {
        console.error('加载数据类型失败:', error)
        return []
      })
      .finally(() => {
        dataTypesPromise = null
      })

    return dataTypesPromise
  }

  const loadAll = async () => {
    const [vendors, dataTypes] = await Promise.all([
      loadVendors(),
      loadDataTypes()
    ])
    return { vendors, dataTypes }
  }

  const clearCache = () => {
    vendorOptions.value = []
    dataTypeOptions.value = []
    vendorsLoadedAt.value = 0
    dataTypesLoadedAt.value = 0
  }

  return {
    vendorOptions,
    dataTypeOptions,
    loadVendors,
    loadDataTypes,
    loadAll,
    clearCache
  }
})
