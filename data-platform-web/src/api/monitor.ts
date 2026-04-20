import request from "@/utils/request"

export const getAlertRuleList = (params: any) => {
  return request({
    url: "/api/v1/monitor/alert/rule/list",
    method: "get",
    params
  })
}

export const getAlertRule = (id: number) => {
  return request({
    url: `/api/v1/monitor/alert/rule/${id}`,
    method: "get"
  })
}

export const createAlertRule = (data: any) => {
  return request({
    url: "/api/v1/monitor/alert/rule",
    method: "post",
    data
  })
}

export const updateAlertRule = (id: number, data: any) => {
  return request({
    url: `/api/v1/monitor/alert/rule/${id}`,
    method: "put",
    data
  })
}

export const deleteAlertRule = (id: number) => {
  return request({
    url: `/api/v1/monitor/alert/rule/${id}`,
    method: "delete"
  })
}

export const updateAlertRuleStatus = (id: number, status: string) => {
  return request({
    url: `/api/v1/monitor/alert/rule/${id}/status`,
    method: "put",
    data: { status }
  })
}

export const getAlertRecordList = (params: any) => {
  return request({
    url: "/api/v1/monitor/alert/record/list",
    method: "get",
    params
  })
}

export const getAlertRecord = (id: number) => {
  return request({
    url: `/api/v1/monitor/alert/record/${id}`,
    method: "get"
  })
}

export const resolveAlertRecord = (id: number) => {
  return request({
    url: `/api/v1/monitor/alert/record/${id}/resolve`,
    method: "put"
  })
}
