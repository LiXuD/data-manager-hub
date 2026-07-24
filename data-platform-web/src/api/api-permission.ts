import { request } from '@/utils/request'
import type { ListResponse } from '@/types'

export interface ApiPermissionApplication {
  id: number
  applicationNo: string
  requestType: 'OPEN' | 'RENEW'
  tenantId: number
  callerId: number
  callerCodeSnapshot: string
  callerNameSnapshot: string
  apiKeyId: number
  apiKeyNameSnapshot?: string
  applicantUserId: number
  applicantNameSnapshot: string
  businessPurpose: string
  businessScene: string
  expectedDailyCalls: number
  ticketNo?: string
  requestedExpireAt?: string
  approvedExpireAt?: string
  status: string
  engineStatus: string
  processDefinitionKey?: string
  processDefinitionVersion?: number
  processInstanceId?: string
  currentTaskId?: string
  currentTaskName?: string
  currentTaskCreatedAt?: string
  submittedAt?: string
  decisionComment?: string
  version: number
  createdAt: string
  updatedAt: string
}

export interface ApiPermissionItem {
  id: number
  applicationId: number
  apiKeyId: number
  interfaceId: number
  interfaceCodeSnapshot: string
  interfaceNameSnapshot: string
  interfaceStatusSnapshot: string
  itemStatus: string
  grantId?: number
}

export interface ApiPermissionAction {
  id: number
  action: string
  actorType: string
  actorNameSnapshot?: string
  fromStatus?: string
  toStatus?: string
  comment?: string
  taskName?: string
  createdAt: string
}

export interface ApplicationDetail {
  application: ApiPermissionApplication
  items: ApiPermissionItem[]
  actions: ApiPermissionAction[]
}

export interface ApplicationDraft {
  requestType: 'OPEN' | 'RENEW'
  callerId: number | null
  apiKeyId: number | null
  interfaceIds: number[]
  businessPurpose: string
  businessScene: string
  expectedDailyCalls: number
  requestedExpireAt: string
  ticketNo?: string
}

export interface CallerOption {
  id: number
  callerCode: string
  callerName: string
}

export interface ApiKeyOption {
  id: number
  callerId: number
  keyName: string
  status: string
  expireTime?: string
}

export interface InterfaceOption {
  id: number
  interfaceCode: string
  interfaceName: string
  status: string
  granted: boolean
  pending: boolean
}

export interface TaskSnapshot {
  id: string
  processInstanceId: string
  taskDefinitionKey: string
  name: string
  assignee?: string
  createdAt: string
}

export interface ApprovalTask {
  task: TaskSnapshot
  application: ApiPermissionApplication
}

export interface ApprovalTaskDetail {
  task: TaskSnapshot
  policy: TaskPolicy
  application: ApplicationDetail
}

export interface TaskPolicy {
  allowWithdraw: boolean
  allowExpireAdjustment: boolean
  allowedDecisions: string[]
  formFields: TaskFormField[]
}

export interface TaskFormField {
  id: string
  name: string
  type: 'string' | 'boolean' | 'long' | 'double' | 'date' | 'enum'
  required: boolean
  defaultValue?: string
  options: Array<{ value: string; label: string }>
}

export interface Grant {
  id: number
  tenantId: number
  callerId: number
  callerName: string
  apiKeyId: number
  apiKeyName: string
  interfaceId: number
  interfaceCode?: string
  interfaceName?: string
  source: string
  status: string
  effectiveAt: string
  expireAt?: string
  revokedAt?: string
  revokedBy?: number
  revokeReason?: string
}

export interface EmergencyGrantRequest {
  callerId: number | null
  apiKeyId: number | null
  interfaceIds: number[]
  expireAt: string
  reason: string
  ticketNo: string
}

export const getApplications = (params: {
  status?: string
  scope?: 'mine' | 'tenant'
  page?: number
  pageSize?: number
}) => request.get<ListResponse<ApiPermissionApplication>>(
  '/api-permission/applications',
  { params }
)

export const getApplication = (id: number) =>
  request.get<{ data: ApplicationDetail }>(`/api-permission/applications/${id}`)

export const createApplication = (data: ApplicationDraft) =>
  request.post<{ data: ApiPermissionApplication }>('/api-permission/applications', data)

export const updateApplication = (id: number, data: ApplicationDraft) =>
  request.put<{ data: ApiPermissionApplication }>(`/api-permission/applications/${id}`, data)

export const submitApplication = (id: number, idempotencyKey: string) =>
  request.post<{ data: ApiPermissionApplication }>(
    `/api-permission/applications/${id}/submit`,
    undefined,
    { headers: { 'Idempotency-Key': idempotencyKey } }
  )

export const cancelApplication = (id: number) =>
  request.post<{ data: ApiPermissionApplication }>(`/api-permission/applications/${id}/cancel`)

export const copyApplication = (id: number) =>
  request.post<{ data: ApiPermissionApplication }>(`/api-permission/applications/${id}/copy`)

export const getEligibleCallers = () =>
  request.get<{ data: CallerOption[] }>('/api-permission/eligible-callers')

export const getCallerApiKeys = (callerId: number) =>
  request.get<{ data: ApiKeyOption[] }>(`/api-permission/callers/${callerId}/api-keys`)

export const getInterfaceOptions = (apiKeyId: number, keyword?: string) =>
  request.get<{ data: InterfaceOption[] }>('/api-permission/interface-options', {
    params: { apiKeyId, keyword }
  })

export const getTasks = () =>
  request.get<{ data: ApprovalTask[] }>('/api-permission/tasks')

export const getTask = (taskId: string) =>
  request.get<{ data: ApprovalTaskDetail }>(`/api-permission/tasks/${taskId}`)

export const claimTask = (taskId: string) =>
  request.post<{ data: ApprovalTask }>(`/api-permission/tasks/${taskId}/claim`)

export const unclaimTask = (taskId: string) =>
  request.post<void>(`/api-permission/tasks/${taskId}/unclaim`)

export const completeTask = (taskId: string, data: {
  applicationVersion: number
  decision: string
  approvedExpireAt?: string
  comment?: string
  formData?: Record<string, unknown>
}) => request.post<{ data: ApiPermissionApplication }>(
  `/api-permission/tasks/${taskId}/complete`,
  data
)

export const getProcessHistory = (applicationId: number) =>
  request.get<{ data: Array<{
    activityId: string
    activityName?: string
    activityType: string
    taskId?: string
    assignee?: string
    startedAt: string
    endedAt?: string
  }> }>(`/api-permission/applications/${applicationId}/process-history`)

export const getGrants = (status?: string) =>
  request.get<{ data: Grant[] }>('/api-permission/grants', { params: { status } })

export const revokeGrant = (id: number, reason: string) =>
  request.post<{ data: Grant }>(`/api-permission/grants/${id}/revoke`, { reason })

export const getEmergencyCallers = () =>
  request.get<{ data: CallerOption[] }>('/api-permission/emergency-options/callers')

export const getEmergencyCallerApiKeys = (callerId: number) =>
  request.get<{ data: ApiKeyOption[] }>(
    `/api-permission/emergency-options/callers/${callerId}/api-keys`
  )

export const getEmergencyInterfaceOptions = (apiKeyId: number, keyword?: string) =>
  request.get<{ data: InterfaceOption[] }>('/api-permission/emergency-options/interfaces', {
    params: { apiKeyId, keyword }
  })

export const emergencyGrant = (data: EmergencyGrantRequest) =>
  request.post<{ data: Grant[] }>('/api-permission/emergency-grants', data)
