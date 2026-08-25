import { request } from '@/utils/request'
import type {
  ConnectorApiResponse,
  VendorConnectorMigration
} from '@/types'

const path = '/vendor/connector-migration'

export const getConnectorMigrations = (state?: string) =>
  request.get<ConnectorApiResponse<VendorConnectorMigration[]>>(path, { params: state ? { state } : undefined })
