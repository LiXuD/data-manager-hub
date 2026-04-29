import request from '@/utils/request'

export const generateJavaSDK = (baseUrl: string, apiKey: string) => {
  return request.get('/sdk/java', { params: { baseUrl, apiKey } })
}

export const generatePythonSDK = (baseUrl: string, apiKey: string) => {
  return request.get('/sdk/python', { params: { baseUrl, apiKey } })
}

export const generateGoSDK = (baseUrl: string, apiKey: string) => {
  return request.get('/sdk/go', { params: { baseUrl, apiKey } })
}

export const generateAllSDKs = (baseUrl: string, apiKey: string) => {
  return request.get('/sdk/all', { params: { baseUrl, apiKey } })
}