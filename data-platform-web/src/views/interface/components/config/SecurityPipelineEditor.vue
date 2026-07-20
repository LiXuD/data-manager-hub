<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getSecurityCapabilities,
  getVendorSecurityVersions,
  previewVendorSecurity,
  rollbackVendorSecurity
} from '@/api/vendor-config'
import { getConfigByVendor } from '@/api/config'
import type {
  SecurityDirection,
  SecurityStepType,
  VendorSecurityCapability,
  VendorSecurityStep,
  VendorSecurityVersion
} from '@/types'

interface Props {
  modelValue: VendorSecurityStep[]
  configId?: number
  version?: number
  vendorId?: number
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => [],
  version: 0
})

const emit = defineEmits<{
  'update:modelValue': [value: VendorSecurityStep[]]
  'update:version': [value: number]
}>()

const activeDirection = ref<SecurityDirection>('REQUEST')
const capabilities = ref<VendorSecurityCapability[]>([])
const expandedKeys = ref(new Set<string>())
const draggingIndex = ref<number | null>(null)
const previewParams = ref('{\n  "appId": "demo-app",\n  "timestamp": 1704067200,\n  "name": "测试企业"\n}')
const previewHeaders = ref('{}')
const previewBody = ref('')
const previewResult = ref('')
const previewing = ref(false)
const versions = ref<VendorSecurityVersion[]>([])
const selectedVersionId = ref<number>()
const secretRefs = ref<string[]>(['vendor.secretKey'])

const directionSteps = computed(() => props.modelValue
  .filter(step => step.direction === activeDirection.value)
  .sort((a, b) => a.sortNo - b.sortNo))

const availableCapabilities = computed(() => capabilities.value
  .filter(item => item.directions.includes(activeDirection.value)))

const stepTypeNames: Record<SecurityStepType, string> = {
  FIELD_SELECT: '字段选择',
  GENERATE: '生成动态字段',
  CANONICALIZE: '字段规范化',
  DIGEST: '摘要',
  HMAC: 'HMAC',
  SIGN: '非对称签名',
  ENCRYPT: '加密',
  DECRYPT: '解密',
  VERIFY: '验签',
  ENCODE: '编码',
  DECODE: '解码',
  INJECT: '写入结果',
  REMOVE_FIELD: '移除字段'
}

const templates = [
  { code: 'HMAC_HEADER', name: 'HMAC-SHA256 请求头签名' },
  { code: 'MD5_PARAM', name: 'MD5 参数签名（兼容）' },
  { code: 'SHA256_BODY', name: 'SHA-256 Body 摘要' },
  { code: 'AES_GCM_BODY', name: 'AES-GCM Body 加密' },
  { code: 'RSA_FIELD', name: 'RSA-OAEP 字段加密' },
  { code: 'SM3_SM4', name: 'SM3 + SM4 国密组合' }
]

function newKey(prefix: string) {
  const suffix = globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`
  return `${prefix.toLowerCase()}-${suffix}`
}

function emitDirectionSteps(nextDirectionSteps: VendorSecurityStep[]) {
  const normalized = nextDirectionSteps.map((step, index) => ({ ...step, sortNo: (index + 1) * 100 }))
  const other = props.modelValue.filter(step => step.direction !== activeDirection.value)
  const request = activeDirection.value === 'REQUEST' ? normalized : other
  const response = activeDirection.value === 'RESPONSE' ? normalized : other
  emit('update:modelValue', [...request, ...response])
}

function addStep(stepType: SecurityStepType) {
  const capability = capabilities.value.find(item => item.stepType === stepType)
  const step: VendorSecurityStep = {
    stepKey: newKey(stepType),
    direction: activeDirection.value,
    stepType,
    stepName: capability?.name || stepTypeNames[stepType],
    sortNo: (directionSteps.value.length + 1) * 100,
    enabled: true,
    config: { ...(capability?.defaults || {}) }
  }
  emitDirectionSteps([...directionSteps.value, step])
  expandedKeys.value.add(step.stepKey)
}

function updateStep(stepKey: string, updates: Partial<VendorSecurityStep>) {
  emit('update:modelValue', props.modelValue.map(step => step.stepKey === stepKey ? { ...step, ...updates } : step))
}

function setConfig(stepKey: string, key: string, value: any) {
  const target = props.modelValue.find(step => step.stepKey === stepKey)
  if (!target) return
  updateStep(stepKey, { config: { ...target.config, [key]: value } })
}

function setFields(stepKey: string, value: string) {
  setConfig(stepKey, 'fields', value.split(',').map(item => item.trim()).filter(Boolean))
}

function removeStep(stepKey: string) {
  emitDirectionSteps(directionSteps.value.filter(step => step.stepKey !== stepKey))
}

function copyStep(step: VendorSecurityStep) {
  const copy: VendorSecurityStep = {
    ...step,
    id: undefined,
    stepKey: newKey(step.stepType),
    stepName: `${step.stepName || stepTypeNames[step.stepType]} 副本`,
    config: { ...step.config },
    sortNo: (directionSteps.value.length + 1) * 100
  }
  emitDirectionSteps([...directionSteps.value, copy])
}

function moveStep(index: number, offset: number) {
  const target = index + offset
  if (target < 0 || target >= directionSteps.value.length) return
  const next = [...directionSteps.value]
  const [item] = next.splice(index, 1)
  next.splice(target, 0, item)
  emitDirectionSteps(next)
}

function handleDrop(targetIndex: number) {
  if (draggingIndex.value == null || draggingIndex.value === targetIndex) return
  const next = [...directionSteps.value]
  const [item] = next.splice(draggingIndex.value, 1)
  next.splice(targetIndex, 0, item)
  emitDirectionSteps(next)
  draggingIndex.value = null
}

function toggleExpanded(stepKey: string) {
  const next = new Set(expandedKeys.value)
  if (next.has(stepKey)) {
    next.delete(stepKey)
  } else {
    next.add(stepKey)
  }
  expandedKeys.value = next
}

function algorithmsFor(step: VendorSecurityStep) {
  return capabilities.value.find(item => item.stepType === step.stepType)?.algorithms || []
}

function applyTemplate(code: string) {
  activeDirection.value = 'REQUEST'
  const create = (type: SecurityStepType, name: string, config: Record<string, any>): VendorSecurityStep => ({
    stepKey: newKey(type), direction: 'REQUEST', stepType: type, stepName: name,
    sortNo: 0, enabled: true, config
  })
  let steps: VendorSecurityStep[] = []
  if (code === 'HMAC_HEADER') {
    const canonical = create('CANONICALIZE', '字段排序拼接', { inputFrom: 'PARAMS', fieldOrder: 'KEY_ASC', nullPolicy: 'IGNORE', pairSeparator: '&', keyValueSeparator: '=' })
    const hmac = create('HMAC', 'HMAC-SHA256', { inputFrom: canonical.stepKey, algorithm: 'HMAC_SHA256', secretRef: 'vendor.secretKey', outputEncoding: 'HEX_LOWER' })
    steps = [canonical, hmac, create('INJECT', '写入 X-Signature', { inputFrom: hmac.stepKey, location: 'HEADER', fieldName: 'X-Signature' })]
  } else if (code === 'MD5_PARAM') {
    const canonical = create('CANONICALIZE', '字段排序拼接', { inputFrom: 'PARAMS', fieldOrder: 'KEY_ASC', nullPolicy: 'IGNORE', pairSeparator: '&', keyValueSeparator: '=' })
    const digest = create('DIGEST', 'MD5摘要', { inputFrom: canonical.stepKey, algorithm: 'MD5', secretRef: 'vendor.secretKey', secretPlacement: 'SUFFIX', outputEncoding: 'HEX_LOWER' })
    steps = [canonical, digest, create('INJECT', '写入 sign', { inputFrom: digest.stepKey, location: 'PARAM', fieldName: 'sign' })]
  } else if (code === 'SHA256_BODY') {
    const digest = create('DIGEST', 'Body SHA-256', { inputFrom: 'BODY', algorithm: 'SHA256', outputEncoding: 'HEX_LOWER' })
    steps = [digest, create('INJECT', '写入摘要头', { inputFrom: digest.stepKey, location: 'HEADER', fieldName: 'X-Content-SHA256' })]
  } else if (code === 'AES_GCM_BODY') {
    const canonical = create('CANONICALIZE', '序列化请求参数', { inputFrom: 'PARAMS', fieldOrder: 'NONE', includeKey: true, pairSeparator: '&', keyValueSeparator: '=' })
    const encrypt = create('ENCRYPT', 'AES-GCM加密', { inputFrom: canonical.stepKey, algorithm: 'AES_GCM', secretRef: 'vendor.aes.key', keyEncoding: 'BASE64', outputEncoding: 'BASE64', prependIv: true })
    steps = [canonical, encrypt, create('INJECT', '写入加密Body', { inputFrom: encrypt.stepKey, location: 'BODY' })]
  } else if (code === 'RSA_FIELD') {
    const encrypt = create('ENCRYPT', 'RSA-OAEP字段加密', { inputFrom: 'PARAMS.sensitiveData', algorithm: 'RSA_OAEP', secretRef: 'vendor.rsa.publicKey', outputEncoding: 'BASE64' })
    steps = [encrypt, create('INJECT', '覆盖敏感字段', { inputFrom: encrypt.stepKey, location: 'PARAM', fieldName: 'sensitiveData' })]
  } else if (code === 'SM3_SM4') {
    const canonical = create('CANONICALIZE', '国密字段规范化', { inputFrom: 'PARAMS', fieldOrder: 'KEY_ASC', nullPolicy: 'IGNORE', pairSeparator: '&', keyValueSeparator: '=' })
    const digest = create('DIGEST', 'SM3摘要', { inputFrom: canonical.stepKey, algorithm: 'SM3', outputEncoding: 'HEX_LOWER' })
    const encrypt = create('ENCRYPT', 'SM4-CBC加密', { inputFrom: digest.stepKey, algorithm: 'SM4_CBC', secretRef: 'vendor.sm4.key', keyEncoding: 'HEX', outputEncoding: 'BASE64', prependIv: true })
    steps = [canonical, digest, encrypt, create('INJECT', '写入国密结果', { inputFrom: encrypt.stepKey, location: 'PARAM', fieldName: 'secureData' })]
  }
  emitDirectionSteps(steps)
}

async function runPreview(stepKey?: string) {
  if (!props.configId) {
    ElMessage.warning('请先保存基础配置，再进行安全步骤试算')
    return
  }
  try {
    const params = JSON.parse(previewParams.value || '{}')
    const headers = JSON.parse(previewHeaders.value || '{}')
    let steps = directionSteps.value
    if (stepKey) {
      steps = steps.slice(0, steps.findIndex(step => step.stepKey === stepKey) + 1)
    }
    previewing.value = true
    const res = await previewVendorSecurity(props.configId, {
      direction: activeDirection.value,
      params,
      headers,
      body: previewBody.value || undefined,
      steps
    })
    previewResult.value = JSON.stringify(res.data, null, 2)
  } catch (error: any) {
    if (error instanceof SyntaxError) ElMessage.error('试算参数和响应头必须是有效JSON')
  } finally {
    previewing.value = false
  }
}

async function loadCapabilities() {
  const res = await getSecurityCapabilities()
  capabilities.value = res.data || []
}

async function loadVersions() {
  if (!props.configId) {
    versions.value = []
    return
  }
  const res = await getVendorSecurityVersions(props.configId)
  versions.value = res.data || []
}

async function loadSecretRefs() {
  if (!props.vendorId) {
    secretRefs.value = ['vendor.secretKey']
    return
  }
  try {
    const res: any = await getConfigByVendor(props.vendorId)
    secretRefs.value = ['vendor.secretKey', ...((res.data || [])
      .filter((item: any) => item.isEncrypted)
      .map((item: any) => item.configKey))]
  } catch {
    secretRefs.value = ['vendor.secretKey']
  }
}

async function rollback() {
  if (!props.configId || !selectedVersionId.value) return
  await ElMessageBox.confirm('回滚会创建一个新的安全配置版本，是否继续？', '回滚确认', { type: 'warning' })
  const res = await rollbackVendorSecurity(props.configId, selectedVersionId.value, props.version)
  emit('update:modelValue', res.data.steps || [])
  emit('update:version', res.data.version)
  await loadVersions()
  ElMessage.success('安全配置已回滚')
}

watch(() => props.configId, loadVersions)
watch(() => props.vendorId, loadSecretRefs)
onMounted(() => Promise.all([loadCapabilities(), loadVersions(), loadSecretRefs()]))
</script>

<template>
  <div class="security-editor">
    <el-alert type="info" :closable="false" show-icon>
      安全步骤按顺序执行。拖拽即可调整“先签名后加密”或“先加密后签名”，密钥只保存引用，不会返回明文。
    </el-alert>

    <div class="security-toolbar">
      <el-radio-group v-model="activeDirection" size="small">
        <el-radio-button value="REQUEST">请求处理</el-radio-button>
        <el-radio-button value="RESPONSE">响应处理</el-radio-button>
      </el-radio-group>
      <div class="toolbar-actions">
        <el-dropdown v-if="activeDirection === 'REQUEST'" @command="applyTemplate">
          <el-button size="small">常用模板</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="item in templates" :key="item.code" :command="item.code">
                {{ item.name }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-dropdown @command="addStep">
          <el-button type="primary" size="small">添加步骤</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="item in availableCapabilities" :key="item.stepType" :command="item.stepType">
                {{ item.name }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <div v-if="directionSteps.length === 0" class="empty-steps">暂无步骤，可选择模板或添加处理步骤</div>

    <div class="step-list">
      <div
        v-for="(step, index) in directionSteps"
        :key="step.stepKey"
        class="step-card"
        :class="{ disabled: !step.enabled }"
        draggable="true"
        @dragstart="draggingIndex = index"
        @dragover.prevent
        @drop="handleDrop(index)"
      >
        <div class="step-summary">
          <span class="drag-handle">⋮⋮</span>
          <span class="step-order">{{ index + 1 }}</span>
          <div class="step-title" @click="toggleExpanded(step.stepKey)">
            <strong>{{ step.stepName || stepTypeNames[step.stepType] }}</strong>
            <span>{{ step.stepType }}</span>
          </div>
          <el-switch :model-value="step.enabled" @update:model-value="value => updateStep(step.stepKey, { enabled: Boolean(value) })" />
          <el-button link size="small" :disabled="index === 0" @click="moveStep(index, -1)">上移</el-button>
          <el-button link size="small" :disabled="index === directionSteps.length - 1" @click="moveStep(index, 1)">下移</el-button>
          <el-button link size="small" @click="copyStep(step)">复制</el-button>
          <el-button link size="small" @click="runPreview(step.stepKey)">试算</el-button>
          <el-button link type="danger" size="small" @click="removeStep(step.stepKey)">删除</el-button>
        </div>

        <div v-if="expandedKeys.has(step.stepKey)" class="step-config">
          <el-form label-width="110px" size="small">
            <el-form-item label="步骤名称">
              <el-input :model-value="step.stepName" @update:model-value="value => updateStep(step.stepKey, { stepName: value })" />
            </el-form-item>

            <el-form-item v-if="!['GENERATE', 'REMOVE_FIELD'].includes(step.stepType)" label="输入来源">
              <el-input :model-value="step.config.inputFrom" placeholder="PARAMS、BODY 或前置步骤标识" @update:model-value="value => setConfig(step.stepKey, 'inputFrom', value)" />
            </el-form-item>

            <template v-if="step.stepType === 'FIELD_SELECT'">
              <el-form-item label="字段列表">
                <el-input :model-value="(step.config.fields || []).join(',')" placeholder="appId,timestamp,body" @update:model-value="value => setFields(step.stepKey, value)" />
              </el-form-item>
              <el-form-item label="替换参数">
                <el-switch :model-value="step.config.replaceParams" @update:model-value="value => setConfig(step.stepKey, 'replaceParams', value)" />
              </el-form-item>
            </template>

            <template v-else-if="step.stepType === 'GENERATE'">
              <el-form-item label="生成类型">
                <el-select :model-value="step.config.generator" @update:model-value="value => setConfig(step.stepKey, 'generator', value)">
                  <el-option v-for="item in algorithmsFor(step)" :key="item" :label="item" :value="item" />
                </el-select>
              </el-form-item>
              <el-form-item label="字段名"><el-input :model-value="step.config.fieldName" @update:model-value="value => setConfig(step.stepKey, 'fieldName', value)" /></el-form-item>
              <el-form-item v-if="step.config.generator === 'NONCE'" label="随机长度">
                <el-input-number :model-value="step.config.length || 16" :min="8" :max="128" @update:model-value="value => setConfig(step.stepKey, 'length', value)" />
              </el-form-item>
              <el-form-item v-if="step.config.generator === 'CONSTANT'" label="固定值">
                <el-input :model-value="step.config.value" @update:model-value="value => setConfig(step.stepKey, 'value', value)" />
              </el-form-item>
              <el-form-item label="写入位置">
                <el-select :model-value="step.config.location" @update:model-value="value => setConfig(step.stepKey, 'location', value)">
                  <el-option v-for="item in ['PARAM', 'HEADER', 'QUERY']" :key="item" :label="item" :value="item" />
                </el-select>
              </el-form-item>
            </template>

            <template v-else-if="step.stepType === 'CANONICALIZE'">
              <el-form-item label="参与字段">
                <el-input :model-value="(step.config.fields || []).join(',')" placeholder="留空表示全部字段" @update:model-value="value => setFields(step.stepKey, value)" />
              </el-form-item>
              <el-form-item label="字段排序">
                <el-select :model-value="step.config.fieldOrder" @update:model-value="value => setConfig(step.stepKey, 'fieldOrder', value)">
                  <el-option v-for="item in ['KEY_ASC', 'KEY_DESC', 'EXPLICIT', 'NONE']" :key="item" :label="item" :value="item" />
                </el-select>
              </el-form-item>
              <el-row :gutter="12">
                <el-col :span="12"><el-form-item label="键值分隔"><el-input :model-value="step.config.keyValueSeparator" @update:model-value="value => setConfig(step.stepKey, 'keyValueSeparator', value)" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="参数分隔"><el-input :model-value="step.config.pairSeparator" @update:model-value="value => setConfig(step.stepKey, 'pairSeparator', value)" /></el-form-item></el-col>
              </el-row>
              <el-row :gutter="12">
                <el-col :span="12"><el-form-item label="前缀"><el-input :model-value="step.config.prefix" @update:model-value="value => setConfig(step.stepKey, 'prefix', value)" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="后缀"><el-input :model-value="step.config.suffix" @update:model-value="value => setConfig(step.stepKey, 'suffix', value)" /></el-form-item></el-col>
              </el-row>
              <el-row :gutter="12">
                <el-col :span="12">
                  <el-form-item label="空值策略">
                    <el-select :model-value="step.config.nullPolicy" @update:model-value="value => setConfig(step.stepKey, 'nullPolicy', value)">
                      <el-option label="忽略空值" value="IGNORE" />
                      <el-option label="保留空值" value="KEEP" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="12"><el-form-item label="包含字段名"><el-switch :model-value="step.config.includeKey !== false" @update:model-value="value => setConfig(step.stepKey, 'includeKey', value)" /></el-form-item></el-col>
              </el-row>
            </template>

            <template v-else-if="['DIGEST', 'HMAC', 'SIGN', 'ENCRYPT', 'DECRYPT', 'VERIFY'].includes(step.stepType)">
              <el-form-item label="算法">
                <el-select :model-value="step.config.algorithm" style="width: 100%" @update:model-value="value => setConfig(step.stepKey, 'algorithm', value)">
                  <el-option v-for="item in algorithmsFor(step)" :key="item" :label="item" :value="item" />
                </el-select>
                <el-alert v-if="['MD5', 'SHA1'].includes(step.config.algorithm)" type="warning" :closable="false" title="该算法仅用于兼容旧厂商，不建议新接口使用" />
              </el-form-item>
              <el-form-item v-if="['DIGEST', 'HMAC', 'SIGN', 'ENCRYPT', 'DECRYPT', 'VERIFY'].includes(step.stepType)" :label="step.stepType === 'DIGEST' ? '密钥引用(可选)' : '密钥引用'">
                <el-select
                  :model-value="step.config.secretRef"
                  filterable
                  allow-create
                  default-first-option
                  placeholder="选择加密配置或输入引用"
                  style="width: 100%"
                  @update:model-value="value => setConfig(step.stepKey, 'secretRef', value)"
                >
                  <el-option v-for="item in secretRefs" :key="item" :label="item" :value="item" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="step.stepType === 'DIGEST' && step.config.secretRef" label="密钥拼接位置">
                <el-radio-group :model-value="step.config.secretPlacement || 'SUFFIX'" @update:model-value="value => setConfig(step.stepKey, 'secretPlacement', value)">
                  <el-radio-button value="PREFIX">前缀</el-radio-button>
                  <el-radio-button value="SUFFIX">后缀</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item v-if="step.stepType === 'VERIFY'" label="签名来源">
                <el-input :model-value="step.config.signatureFrom" placeholder="如 PARAMS.sign" @update:model-value="value => setConfig(step.stepKey, 'signatureFrom', value)" />
              </el-form-item>
              <el-form-item v-if="step.stepType === 'HMAC' || (step.stepType === 'VERIFY' && String(step.config.algorithm || '').startsWith('HMAC')) || (['ENCRYPT', 'DECRYPT'].includes(step.stepType) && !String(step.config.algorithm || '').startsWith('RSA'))" label="密钥编码">
                <el-select :model-value="step.config.keyEncoding || 'UTF8'" @update:model-value="value => setConfig(step.stepKey, 'keyEncoding', value)">
                  <el-option v-for="item in ['UTF8', 'HEX', 'BASE64']" :key="item" :label="item" :value="item" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="step.stepType === 'DECRYPT'" label="输入编码">
                <el-select :model-value="step.config.inputEncoding || 'BASE64'" @update:model-value="value => setConfig(step.stepKey, 'inputEncoding', value)">
                  <el-option v-for="item in ['HEX_LOWER', 'HEX_UPPER', 'BASE64', 'BASE64_URL']" :key="item" :label="item" :value="item" />
                </el-select>
              </el-form-item>
              <template v-if="['ENCRYPT', 'DECRYPT'].includes(step.stepType) && ['AES_GCM', 'AES_CBC', 'SM4_CBC'].includes(step.config.algorithm)">
                <el-form-item label="密文携带IV">
                  <el-switch :model-value="step.config.prependIv !== false" @update:model-value="value => setConfig(step.stepKey, 'prependIv', value)" />
                </el-form-item>
                <el-form-item v-if="step.config.prependIv === false" label="固定IV">
                  <el-input :model-value="step.config.iv" placeholder="需配合IV编码，生产环境建议由对端协议明确" @update:model-value="value => setConfig(step.stepKey, 'iv', value)" />
                  <el-select :model-value="step.config.ivEncoding || 'UTF8'" @update:model-value="value => setConfig(step.stepKey, 'ivEncoding', value)">
                    <el-option v-for="item in ['UTF8', 'HEX', 'BASE64']" :key="item" :label="item" :value="item" />
                  </el-select>
                </el-form-item>
              </template>
              <el-form-item v-if="step.stepType === 'VERIFY'" label="签名编码">
                <el-select :model-value="step.config.signatureEncoding || 'BASE64'" @update:model-value="value => setConfig(step.stepKey, 'signatureEncoding', value)">
                  <el-option v-for="item in ['HEX_LOWER', 'HEX_UPPER', 'BASE64', 'BASE64_URL']" :key="item" :label="item" :value="item" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="step.stepType === 'VERIFY'" label="失败即终止">
                <el-switch :model-value="step.config.failOnInvalid !== false" @update:model-value="value => setConfig(step.stepKey, 'failOnInvalid', value)" />
              </el-form-item>
              <el-form-item v-if="!['DECRYPT', 'VERIFY'].includes(step.stepType)" label="输出编码">
                <el-select :model-value="step.config.outputEncoding" @update:model-value="value => setConfig(step.stepKey, 'outputEncoding', value)">
                  <el-option v-for="item in ['HEX_LOWER', 'HEX_UPPER', 'BASE64', 'BASE64_URL']" :key="item" :label="item" :value="item" />
                </el-select>
              </el-form-item>
            </template>

            <template v-else-if="['ENCODE', 'DECODE'].includes(step.stepType)">
              <el-form-item label="编码方式">
                <el-select :model-value="step.config.encoding" @update:model-value="value => setConfig(step.stepKey, 'encoding', value)">
                  <el-option v-for="item in algorithmsFor(step)" :key="item" :label="item" :value="item" />
                </el-select>
              </el-form-item>
            </template>

            <template v-else-if="step.stepType === 'INJECT'">
              <el-form-item label="写入位置">
                <el-select :model-value="step.config.location" @update:model-value="value => setConfig(step.stepKey, 'location', value)">
                  <el-option v-for="item in algorithmsFor(step)" :key="item" :label="item" :value="item" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="step.config.location !== 'BODY'" label="字段名称">
                <el-input :model-value="step.config.fieldName" @update:model-value="value => setConfig(step.stepKey, 'fieldName', value)" />
              </el-form-item>
            </template>

            <template v-else-if="step.stepType === 'REMOVE_FIELD'">
              <el-form-item label="移除位置">
                <el-select :model-value="step.config.location" @update:model-value="value => setConfig(step.stepKey, 'location', value)">
                  <el-option v-for="item in algorithmsFor(step)" :key="item" :label="item" :value="item" />
                </el-select>
              </el-form-item>
              <el-form-item label="字段名称"><el-input :model-value="step.config.fieldName" @update:model-value="value => setConfig(step.stepKey, 'fieldName', value)" /></el-form-item>
            </template>
          </el-form>
        </div>
      </div>
    </div>

    <el-collapse class="preview-panel">
      <el-collapse-item title="流水线试算与脱敏预览" name="preview">
        <div class="preview-label">参数 JSON</div>
        <el-input v-model="previewParams" type="textarea" :rows="6" placeholder="输入JSON参数" />
        <div class="preview-label">请求/响应头 JSON</div>
        <el-input v-model="previewHeaders" type="textarea" :rows="3" placeholder="例如 {&quot;X-Signature&quot;:&quot;...&quot;}" />
        <div class="preview-label">Body（留空时自动序列化参数）</div>
        <el-input v-model="previewBody" type="textarea" :rows="4" placeholder="响应解密时可直接粘贴密文" />
        <div class="preview-actions"><el-button type="primary" size="small" :loading="previewing" @click="runPreview()">执行试算</el-button></div>
        <el-input v-if="previewResult" :model-value="previewResult" type="textarea" :rows="10" readonly />
      </el-collapse-item>
      <el-collapse-item v-if="configId" title="版本历史与回滚" name="versions">
        <div class="version-actions">
          <el-select v-model="selectedVersionId" placeholder="选择历史版本" style="width: 260px">
            <el-option v-for="item in versions" :key="item.id" :label="`版本 ${item.version} · ${item.createdAt}`" :value="item.id" />
          </el-select>
          <el-button type="warning" :disabled="!selectedVersionId" @click="rollback">回滚</el-button>
        </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<style scoped>
.security-editor { display: flex; flex-direction: column; gap: 16px; }
.security-toolbar, .toolbar-actions, .step-summary, .preview-actions, .version-actions { display: flex; align-items: center; gap: 10px; }
.security-toolbar { justify-content: space-between; }
.empty-steps { padding: 32px; text-align: center; color: var(--color-text-tertiary); border: 1px dashed var(--color-border); border-radius: 8px; }
.step-list { display: flex; flex-direction: column; gap: 10px; }
.step-card { border: 1px solid var(--color-border); border-radius: 8px; background: var(--color-surface); overflow: hidden; }
.step-card.disabled { opacity: .6; }
.step-summary { min-height: 48px; padding: 8px 12px; }
.drag-handle { cursor: grab; color: var(--color-text-tertiary); }
.step-order { width: 24px; height: 24px; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; background: var(--color-primary-light); color: var(--color-primary); font-weight: 600; }
.step-title { min-width: 150px; flex: 1; cursor: pointer; display: flex; flex-direction: column; }
.step-title span { font-size: 11px; color: var(--color-text-tertiary); }
.step-config { padding: 16px; border-top: 1px solid var(--color-border); background: var(--color-bg-light); }
.step-config :deep(.el-select) { min-width: 220px; }
.step-config :deep(.el-alert) { margin-top: 8px; }
.preview-panel { border-top: 1px solid var(--color-border); }
.preview-actions { margin: 10px 0; }
.preview-label { margin: 10px 0 6px; color: var(--color-text-secondary); font-size: 13px; }
.version-actions { padding: 8px 0; }
</style>
