import { request } from '@/utils/request'

export interface DataTestOptions {
  vendors: Array<{ id: number; vendorName: string }>
  dataTypes: Array<{ id: number; dataTypeCode: string; dataTypeName: string }>
  interfaces: Array<{
    id: number
    interfaceCode: string
    interfaceName: string
    vendorId: number
    dataTypeId: number
  }>
  scenes: Array<{ id: number; sceneCode: string; sceneName: string; status: string }>
  products: Array<{ id: number; productCode: string; productName: string; status: string }>
}

export const getDataTestOptions = (apiKeyId?: number | null) => {
  return request.get<{ data: DataTestOptions }>('/data-test/options', {
    params: apiKeyId != null ? { apiKeyId } : undefined
  })
}
