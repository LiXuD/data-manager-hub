/**
 * 数据类型常量定义
 */

/** 数据类型编码 */
export const DATA_TYPE_CODE = {
  BUSINESS_INFO: 'BUSINESS_INFO',
  CREDIT_QUERY: 'CREDIT_QUERY',
  LITIGATION: 'LITIGATION',
  NEWS: 'NEWS'
} as const
export type DataTypeCode = typeof DATA_TYPE_CODE[keyof typeof DATA_TYPE_CODE]

export const DATA_TYPE_CODE_OPTIONS = [
  { label: '工商信息', value: DATA_TYPE_CODE.BUSINESS_INFO },
  { label: '企业征信', value: DATA_TYPE_CODE.CREDIT_QUERY },
  { label: '诉讼信息', value: DATA_TYPE_CODE.LITIGATION },
  { label: '新闻舆情', value: DATA_TYPE_CODE.NEWS }
] as const
