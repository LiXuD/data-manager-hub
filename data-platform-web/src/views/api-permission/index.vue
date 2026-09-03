<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>接口权限审批</h2>
        <p class="header-desc">申请、审批并追踪内部系统的接口调用权限</p>
      </div>
      <el-button
        v-if="userStore.hasPermission('api-permission:apply')"
        type="primary"
        @click="openCreate"
      >
        新建申请
      </el-button>
    </div>

    <el-tabs v-model="activeTab" class="approval-tabs" @tab-change="handleTabChange">
      <el-tab-pane
        v-if="userStore.hasPermission('api-permission:view')"
        label="我的申请"
        name="applications"
      >
        <div class="toolbar">
          <el-select
            v-model="applicationStatus"
            placeholder="全部状态"
            clearable
            class="status-filter"
            @change="loadApplications"
          >
            <el-option
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <el-button @click="loadApplications">刷新</el-button>
        </div>

        <el-card class="table-card">
          <el-table :data="applications" v-loading="applicationLoading" stripe>
            <el-table-column prop="applicationNo" label="申请单号" width="190">
              <template #default="{ row }">
                <button class="link-button code" @click="openDetail(row.id)">
                  {{ row.applicationNo }}
                </button>
              </template>
            </el-table-column>
            <el-table-column prop="callerNameSnapshot" label="内部系统" min-width="135" />
            <el-table-column prop="apiKeyNameSnapshot" label="API Key" min-width="120" />
            <el-table-column prop="businessScene" label="业务场景" min-width="120" show-overflow-tooltip />
            <el-table-column prop="requestType" label="类型" width="90">
              <template #default="{ row }">{{ row.requestType === 'RENEW' ? '续期' : '开通' }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">
                  {{ statusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="210">
              <template #default="{ row }">
                <el-button type="primary" link @click="openDetail(row.id)">详情</el-button>
                <el-button
                  v-if="row.status === 'DRAFT' && userStore.hasPermission('api-permission:apply')"
                  type="primary"
                  link
                  @click="openEdit(row)"
                >
                  编辑
                </el-button>
                <el-button
                  v-if="row.status === 'DRAFT' && userStore.hasPermission('api-permission:apply')"
                  type="primary"
                  link
                  @click="submitDraft(row)"
                >
                  提交
                </el-button>
                <el-button
                  v-if="userStore.hasPermission('api-permission:apply')
                    && (row.status === 'DRAFT' || row.status === 'IN_REVIEW')"
                  type="danger"
                  link
                  @click="cancelDraft(row)"
                >
                  取消
                </el-button>
                <el-button
                  v-if="userStore.hasPermission('api-permission:apply')
                    && ['REJECTED', 'CANCELED', 'EXPIRED', 'REVOKED'].includes(row.status)"
                  type="primary"
                  link
                  @click="copyDraft(row)"
                >
                  复制
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-container">
            <el-pagination
              v-model:current-page="applicationPage.page"
              v-model:page-size="applicationPage.pageSize"
              :total="applicationPage.total"
              layout="total, sizes, prev, pager, next"
              @size-change="loadApplications"
              @current-change="loadApplications"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane
        v-if="userStore.hasPermission('api-permission:approve')"
        label="审批待办"
        name="tasks"
      >
        <div class="toolbar">
          <span class="toolbar-note">仅显示当前用户候选组或已认领的活动任务</span>
          <el-button @click="loadTasks">刷新</el-button>
        </div>
        <el-card class="table-card">
          <el-table :data="tasks" v-loading="taskLoading" stripe>
            <el-table-column prop="application.applicationNo" label="申请单号" width="190" />
            <el-table-column prop="application.applicantNameSnapshot" label="申请人" width="120" />
            <el-table-column prop="application.callerNameSnapshot" label="内部系统" min-width="150" />
            <el-table-column prop="task.name" label="当前节点" min-width="150" />
            <el-table-column prop="task.assignee" label="办理人" width="120">
              <template #default="{ row }">{{ row.task.assignee || '待认领' }}</template>
            </el-table-column>
            <el-table-column prop="task.createdAt" label="到达时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.task.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button
                  v-if="!row.task.assignee"
                  type="primary"
                  link
                  @click="claim(row)"
                >
                  认领
                </el-button>
                <el-button type="primary" link @click="openTask(row.task.id)">办理</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane
        v-if="userStore.hasPermission('api-permission:process-view')"
        label="流程诊断"
        name="process-diagnostics"
      >
        <div class="toolbar">
          <span class="toolbar-note">只读展示流程定义、节点角色和实例统计；不会修改运行中实例。</span>
          <el-button @click="loadProcessDiagnostics">刷新</el-button>
        </div>
        <el-alert
          v-if="processDiagnosticError"
          :title="processDiagnosticError"
          type="error"
          :closable="false"
          show-icon
          class="diagnostic-alert"
        />
        <el-card v-for="definition in processDiagnostics" :key="definition.id" class="diagnostic-card">
          <div class="diagnostic-header">
            <div>
              <div class="primary-cell">{{ definition.name || definition.key }}</div>
              <div class="secondary-cell">{{ definition.key }} · v{{ definition.version }} · {{ definition.id }}</div>
            </div>
            <div class="diagnostic-stats">
              <el-tag :type="definition.suspended ? 'warning' : 'success'">
                {{ definition.suspended ? '已暂停' : '已启用' }}
              </el-tag>
              <span>活动实例 {{ definition.activeInstances }}</span>
              <span>累计实例 {{ definition.totalInstances }}</span>
            </div>
          </div>
          <div class="diagnostic-roles">
            <span class="diagnostic-label">绑定角色</span>
            <el-tag v-for="role in definition.boundRoles" :key="role" size="small" effect="plain">{{ role }}</el-tag>
            <span v-if="!definition.boundRoles.length" class="secondary-cell">未声明候选角色</span>
          </div>
          <el-table :data="definition.nodes" stripe size="small">
            <el-table-column prop="id" label="节点 ID" min-width="180" />
            <el-table-column prop="name" label="节点名称" min-width="160" />
            <el-table-column prop="type" label="类型" width="150" />
            <el-table-column label="候选角色" min-width="220">
              <template #default="{ row }">{{ row.candidateGroups.join('、') || '—' }}</template>
            </el-table-column>
          </el-table>
        </el-card>
        <el-empty v-if="!processDiagnosticLoading && !processDiagnostics.length && !processDiagnosticError" description="暂无流程定义" />
        <div v-loading="processDiagnosticLoading" class="diagnostic-loading" />
      </el-tab-pane>

      <el-tab-pane
        v-if="userStore.hasPermission('api-permission:grant-view')"
        label="授权台账"
        name="grants"
      >
        <div class="toolbar">
          <el-select
            v-model="grantStatus"
            placeholder="全部状态"
            clearable
            class="status-filter"
            @change="loadGrants"
          >
            <el-option label="有效" value="ACTIVE" />
            <el-option label="已到期" value="EXPIRED" />
            <el-option label="已撤销" value="REVOKED" />
          </el-select>
          <el-button
            v-if="userStore.hasPermission('api-permission:emergency-grant')"
            type="danger"
            plain
            @click="openEmergency"
          >
            紧急授权
          </el-button>
          <el-button @click="loadGrants">刷新</el-button>
        </div>
        <el-card class="table-card">
          <el-table :data="grants" v-loading="grantLoading" stripe>
            <el-table-column prop="callerName" label="内部系统" min-width="140" />
            <el-table-column prop="apiKeyName" label="API Key" min-width="130" />
            <el-table-column prop="interfaceCode" label="接口编码" min-width="145">
              <template #default="{ row }"><span class="code">{{ row.interfaceCode || row.interfaceId }}</span></template>
            </el-table-column>
            <el-table-column prop="interfaceName" label="接口名称" min-width="150" />
            <el-table-column prop="source" label="来源" width="130">
              <template #default="{ row }">{{ sourceLabel(row.source) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
                  {{ grantStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="缓存策略" width="130">
              <template #default="{ row }">
                {{ row.cacheEnabled ? `最多 ${row.approvedCacheDays} 天` : '不允许缓存' }}
              </template>
            </el-table-column>
            <el-table-column prop="expireAt" label="有效截止" width="170">
              <template #default="{ row }">{{ row.expireAt ? formatDateTime(row.expireAt) : '长期' }}</template>
            </el-table-column>
            <el-table-column
              v-if="userStore.hasPermission('api-permission:revoke')"
              label="操作"
              width="90"
            >
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'ACTIVE'"
                  type="danger"
                  link
                  @click="revoke(row)"
                >
                  撤销
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-drawer
      v-model="createVisible"
      :title="editingId ? '编辑接口权限申请' : '新建接口权限申请'"
      size="min(620px, 100%)"
      destroy-on-close
    >
      <el-form :model="draft" label-position="top" class="application-form">
        <div class="form-grid">
          <el-form-item label="申请类型" required>
            <el-radio-group v-model="draft.requestType">
              <el-radio-button value="OPEN">新增开通</el-radio-button>
              <el-radio-button value="RENEW">授权续期</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="内部系统" required>
            <el-select
              v-model="draft.callerId"
              placeholder="选择有权管理的内部系统"
              filterable
              :disabled="callers.length === 0"
              @change="handleCallerChange"
            >
              <el-option
                v-for="item in callers"
                :key="item.id"
                :label="`${item.callerName}（${item.callerCode}）`"
                :value="item.id"
              />
            </el-select>
            <div v-if="callers.length === 0" class="empty-caller-hint">
              <span>当前租户还没有可申请的内部系统，请先新增内部系统并创建 API Key。</span>
              <el-button type="primary" link @click="goToInternalSystems">前往内部系统管理</el-button>
            </div>
          </el-form-item>
          <el-form-item label="API Key" required>
            <el-select
              v-model="draft.apiKeyId"
              placeholder="选择有效 API Key"
              :disabled="!draft.callerId"
              @change="handleApiKeyChange"
            >
              <el-option
                v-for="item in apiKeys"
                :key="item.id"
                :label="item.keyName"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="期望有效截止时间" required>
            <el-date-picker
              v-model="draft.requestedExpireAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="选择未来时间"
              style="width: 100%"
            />
          </el-form-item>
        </div>

        <el-form-item label="申请接口" required>
          <el-select
            v-model="draft.interfaceIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择启用接口"
            :disabled="!draft.apiKeyId"
          >
            <el-option
              v-for="item in interfaces"
              :key="item.id"
              :label="`${item.interfaceName}（${item.interfaceCode}）`"
              :value="item.id"
              :disabled="draft.requestType === 'OPEN' ? item.granted || item.pending : !item.granted || item.pending"
            >
              <span>{{ item.interfaceName }}</span>
              <span class="option-meta">
                {{ item.granted ? '已授权' : item.pending ? '审批中' : item.interfaceCode }}
              </span>
            </el-option>
          </el-select>
          <div class="field-hint">新增申请排除已授权/审批中接口，续期仅可选择当前有效授权。</div>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="申请结果缓存">
            <el-switch
              v-model="draft.cacheEnabled"
              active-text="申请使用缓存"
              inactive-text="不使用缓存"
            />
          </el-form-item>
          <el-form-item v-if="draft.cacheEnabled" label="申请缓存时效（天）" required>
            <el-input-number
              v-model="draft.requestedCacheDays"
              :min="1"
              :max="365"
              controls-position="right"
              style="width: 100%"
            />
            <div class="field-hint">审批人只能降低该上限，不能提高。</div>
          </el-form-item>
        </div>
        <el-form-item label="业务用途" required>
          <el-input
            v-model="draft.businessPurpose"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            placeholder="说明为什么需要这些接口权限、数据将如何使用"
          />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="业务场景" required>
            <el-input v-model="draft.businessScene" maxlength="200" placeholder="例如：贷前审批" />
          </el-form-item>
          <el-form-item label="预计日调用量" required>
            <el-input-number
              v-model="draft.expectedDailyCalls"
              :min="1"
              :max="100000000"
              controls-position="right"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="工单/项目编号">
            <el-input v-model="draft.ticketNo" maxlength="100" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-alert
          v-if="draftValidationMessage"
          :title="draftValidationMessage"
          type="warning"
          show-icon
          :closable="false"
          class="draft-validation-alert"
        />
        <el-button @click="createVisible = false">取消</el-button>
        <el-button :loading="saving" @click="saveDraft(false)">保存草稿</el-button>
        <el-button type="primary" :loading="saving" @click="saveDraft(true)">保存并提交</el-button>
      </template>
    </el-drawer>

    <el-drawer v-model="detailVisible" title="申请详情" size="min(720px, 100%)">
      <template v-if="detail">
        <div class="detail-heading">
          <div>
            <span class="code">{{ detail.application.applicationNo }}</span>
            <h3>{{ detail.application.callerNameSnapshot }}</h3>
          </div>
          <el-tag :type="statusTagType(detail.application.status)">
            {{ statusLabel(detail.application.status) }}
          </el-tag>
        </div>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="申请人">{{ detail.application.applicantNameSnapshot }}</el-descriptions-item>
          <el-descriptions-item label="API Key">{{ detail.application.apiKeyNameSnapshot || detail.application.apiKeyId }}</el-descriptions-item>
          <el-descriptions-item label="业务场景">{{ detail.application.businessScene }}</el-descriptions-item>
          <el-descriptions-item label="预计日调用量">{{ detail.application.expectedDailyCalls }}</el-descriptions-item>
          <el-descriptions-item label="申请截止">{{ formatDateTime(detail.application.requestedExpireAt) }}</el-descriptions-item>
          <el-descriptions-item label="批准截止">{{ formatDateTime(detail.application.approvedExpireAt) }}</el-descriptions-item>
          <el-descriptions-item label="业务用途" :span="2">{{ detail.application.businessPurpose }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="section-title">接口清单</h4>
        <el-table :data="detail.items" size="small" border>
          <el-table-column prop="interfaceCodeSnapshot" label="接口编码" min-width="150" />
          <el-table-column prop="interfaceNameSnapshot" label="接口名称" min-width="180" />
          <el-table-column label="申请缓存" width="110">
            <template #default="{ row }">
              {{ row.requestedCacheEnabled ? `${row.requestedCacheDays} 天` : '不使用' }}
            </template>
          </el-table-column>
          <el-table-column label="批准缓存" width="110">
            <template #default="{ row }">
              {{ row.approvedCacheEnabled ? `${row.approvedCacheDays} 天` : '未批准' }}
            </template>
          </el-table-column>
          <el-table-column prop="itemStatus" label="状态" width="110">
            <template #default="{ row }">{{ statusLabel(row.itemStatus) }}</template>
          </el-table-column>
        </el-table>

        <h4 class="section-title">审批轨迹</h4>
        <el-timeline>
          <el-timeline-item
            v-for="action in detail.actions"
            :key="action.id"
            :timestamp="formatDateTime(action.createdAt)"
            placement="top"
          >
            <div class="timeline-title">{{ actionLabel(action.action) }}</div>
            <div class="timeline-meta">
              {{ action.actorNameSnapshot || (action.actorType === 'SYSTEM' ? '系统' : '-') }}
              <span v-if="action.taskName"> · {{ action.taskName }}</span>
            </div>
            <div v-if="action.comment" class="timeline-comment">{{ action.comment }}</div>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-drawer>

    <el-dialog
      v-model="taskVisible"
      title="办理接口权限审批"
      width="min(760px, calc(100vw - 24px))"
      destroy-on-close
    >
      <template v-if="taskDetail">
        <div class="task-summary">
          <div>
            <span class="code">{{ taskDetail.application.application.applicationNo }}</span>
            <h3>{{ taskDetail.application.application.callerNameSnapshot }}</h3>
          </div>
          <div class="task-node">{{ taskDetail.task.name }}</div>
        </div>
        <el-alert
          v-if="taskDetail.task.assignee"
          :title="`当前办理人：${taskDetail.task.assignee}`"
          type="info"
          :closable="false"
          show-icon
        />
        <el-descriptions :column="2" border class="task-descriptions">
          <el-descriptions-item label="申请人">{{ taskDetail.application.application.applicantNameSnapshot }}</el-descriptions-item>
          <el-descriptions-item label="业务场景">{{ taskDetail.application.application.businessScene }}</el-descriptions-item>
          <el-descriptions-item label="预计日调用量">{{ taskDetail.application.application.expectedDailyCalls }}</el-descriptions-item>
          <el-descriptions-item label="申请截止">{{ formatDateTime(taskDetail.application.application.requestedExpireAt) }}</el-descriptions-item>
          <el-descriptions-item label="业务用途" :span="2">{{ taskDetail.application.application.businessPurpose }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="taskDetail.application.items" size="small" border>
          <el-table-column prop="interfaceCodeSnapshot" label="接口编码" min-width="150" />
          <el-table-column prop="interfaceNameSnapshot" label="接口名称" min-width="180" />
          <el-table-column label="申请缓存" width="110">
            <template #default="{ row }">
              {{ row.requestedCacheEnabled ? `${row.requestedCacheDays} 天` : '不使用' }}
            </template>
          </el-table-column>
          <el-table-column prop="interfaceStatusSnapshot" label="接口状态" width="110" />
        </el-table>
        <el-form label-position="top" class="decision-form">
          <el-form-item
            v-if="taskDetail.policy.allowExpireAdjustment"
            label="批准有效截止时间"
          >
            <el-date-picker
              v-model="decision.approvedExpireAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              style="width: 100%"
            />
          </el-form-item>
          <template v-if="taskRequestedCacheEnabled">
            <el-form-item label="批准结果缓存">
              <el-switch
                v-model="decision.approvedCacheEnabled"
                active-text="批准"
                inactive-text="不批准"
              />
            </el-form-item>
            <el-form-item
              v-if="decision.approvedCacheEnabled"
              label="批准缓存时效（天）"
              required
            >
              <el-input-number
                v-model="decision.approvedCacheDays"
                :min="1"
                :max="taskRequestedCacheDays"
                controls-position="right"
                style="width: 100%"
              />
              <div class="field-hint">申请上限为 {{ taskRequestedCacheDays }} 天。</div>
            </el-form-item>
          </template>
          <el-form-item
            v-for="field in taskDetail.policy.formFields"
            :key="field.id"
            :label="field.name || field.id"
            :required="field.required"
          >
            <el-select
              v-if="field.type === 'enum'"
              v-model="decision.formData[field.id]"
              style="width: 100%"
            >
              <el-option
                v-for="option in field.options"
                :key="option.value"
                :label="option.label || option.value"
                :value="option.value"
              />
            </el-select>
            <el-switch
              v-else-if="field.type === 'boolean'"
              v-model="decision.formData[field.id]"
            />
            <el-input-number
              v-else-if="field.type === 'long' || field.type === 'double'"
              v-model="decision.formData[field.id]"
              :precision="field.type === 'long' ? 0 : 2"
              style="width: 100%"
            />
            <el-date-picker
              v-else-if="field.type === 'date'"
              v-model="decision.formData[field.id]"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              style="width: 100%"
            />
            <el-input v-else v-model="decision.formData[field.id]" maxlength="2000" />
          </el-form-item>
          <el-form-item label="审批意见">
            <el-input
              v-model="decision.comment"
              type="textarea"
              :rows="3"
              maxlength="2000"
              show-word-limit
            />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="taskVisible = false">关闭</el-button>
        <el-button
          v-if="taskDetail?.task.assignee"
          :loading="decisionLoading"
          @click="releaseCurrentTask"
        >
          释放任务
        </el-button>
        <el-button
          v-for="decisionOption in taskDetail?.policy.allowedDecisions || []"
          :key="decisionOption"
          :type="decisionOption === 'REJECT' ? 'danger' : 'primary'"
          :loading="decisionLoading"
          @click="decide(decisionOption)"
        >
          {{ decisionLabel(decisionOption) }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="emergencyVisible"
      title="紧急接口授权"
      width="min(620px, calc(100vw - 24px))"
      destroy-on-close
    >
      <el-alert
        title="仅用于故障处置或业务连续性，最长有效 24 小时，操作将完整审计。"
        type="warning"
        :closable="false"
        show-icon
        class="emergency-alert"
      />
      <el-form :model="emergency" label-position="top">
        <div class="form-grid">
          <el-form-item label="内部系统" required>
            <el-select
              v-model="emergency.callerId"
              filterable
              placeholder="选择当前租户内部系统"
              style="width: 100%"
              @change="handleEmergencyCallerChange"
            >
              <el-option
                v-for="item in emergencyCallers"
                :key="item.id"
                :label="`${item.callerName}（${item.callerCode}）`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="API Key" required>
            <el-select
              v-model="emergency.apiKeyId"
              placeholder="选择有效 API Key"
              :disabled="!emergency.callerId"
              style="width: 100%"
              @change="handleEmergencyApiKeyChange"
            >
              <el-option
                v-for="item in emergencyApiKeys"
                :key="item.id"
                :label="item.keyName"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="有效截止时间" required>
            <el-date-picker
              v-model="emergency.expireAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="未来 24 小时内"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="工单号" required>
            <el-input v-model="emergency.ticketNo" maxlength="100" placeholder="例如 INC-2026-001" />
          </el-form-item>
        </div>
        <el-form-item label="授权接口" required>
          <el-select
            v-model="emergency.interfaceIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择一个或多个启用接口"
            :disabled="!emergency.apiKeyId"
            style="width: 100%"
          >
            <el-option
              v-for="item in emergencyInterfaces"
              :key="item.id"
              :label="`${item.interfaceName}（${item.interfaceCode}）${item.granted ? ' · 已授权' : ''}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="紧急原因" required>
          <el-input
            v-model="emergency.reason"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            placeholder="至少 10 个字，说明故障影响和临时授权必要性"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="emergencyVisible = false">取消</el-button>
        <el-button type="danger" :loading="emergencySaving" @click="saveEmergencyGrant">
          确认紧急授权
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type TabsPaneContext } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import {
  cancelApplication,
  claimTask,
  completeTask,
  copyApplication,
  createApplication,
  emergencyGrant,
  getApplication,
  getApplications,
  getCallerApiKeys,
  getEligibleCallers,
  getEmergencyCallerApiKeys,
  getEmergencyCallers,
  getEmergencyInterfaceOptions,
  getGrants,
  getProcessDiagnostics,
  getInterfaceOptions,
  getTask,
  getTasks,
  revokeGrant,
  submitApplication,
  unclaimTask,
  updateApplication,
  type ApiKeyOption,
  type ApiPermissionApplication,
  type ApplicationDetail,
  type ApplicationDraft,
  type ApprovalTask,
  type ApprovalTaskDetail,
  type CallerOption,
  type Grant,
  type InterfaceOption,
  type ProcessDiagnostic
} from '@/api/api-permission'
import { getDraftValidationError } from './validation'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const defaultTab = userStore.hasPermission('api-permission:view')
  ? 'applications'
  : userStore.hasPermission('api-permission:approve')
    ? 'tasks'
    : userStore.hasPermission('api-permission:process-view') ? 'process-diagnostics' : 'grants'
const activeTab = ref(defaultTab)
const applications = ref<ApiPermissionApplication[]>([])
const applicationLoading = ref(false)
const applicationStatus = ref('')
const applicationPage = reactive({ page: 1, pageSize: 20, total: 0 })
const tasks = ref<ApprovalTask[]>([])
const taskLoading = ref(false)
const grants = ref<Grant[]>([])
const grantLoading = ref(false)
const grantStatus = ref('')
const createVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const draftValidationMessage = ref('')
const callers = ref<CallerOption[]>([])
const apiKeys = ref<ApiKeyOption[]>([])
const interfaces = ref<InterfaceOption[]>([])
const detailVisible = ref(false)
const detail = ref<ApplicationDetail | null>(null)
const taskVisible = ref(false)
const taskDetail = ref<ApprovalTaskDetail | null>(null)
const decisionLoading = ref(false)
const emergencyVisible = ref(false)
const emergencySaving = ref(false)
const emergencyCallers = ref<CallerOption[]>([])
const emergencyApiKeys = ref<ApiKeyOption[]>([])
const emergencyInterfaces = ref<InterfaceOption[]>([])
const processDiagnostics = ref<ProcessDiagnostic[]>([])
const processDiagnosticLoading = ref(false)
const processDiagnosticError = ref('')

const draft = reactive<ApplicationDraft>({
  requestType: 'OPEN',
  callerId: null,
  apiKeyId: null,
  interfaceIds: [],
  businessPurpose: '',
  businessScene: '',
  expectedDailyCalls: 1000,
  requestedExpireAt: '',
  ticketNo: '',
  cacheEnabled: false,
  requestedCacheDays: undefined
})

const decision = reactive({
  approvedExpireAt: '',
  approvedCacheEnabled: false,
  approvedCacheDays: undefined as number | undefined,
  comment: '',
  formData: {} as Record<string, any>
})

const emergency = reactive({
  callerId: null as number | null,
  apiKeyId: null as number | null,
  interfaceIds: [] as number[],
  expireAt: '',
  reason: '',
  ticketNo: ''
})

const statusOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: '审批中', value: 'IN_REVIEW' },
  { label: '已生效', value: 'EFFECTIVE' },
  { label: '已驳回', value: 'REJECTED' },
  { label: '已取消', value: 'CANCELED' },
  { label: '流程异常', value: 'ENGINE_ERROR' },
  { label: '已到期', value: 'EXPIRED' },
  { label: '已撤销', value: 'REVOKED' }
]

const currentUserId = computed(() => String(userStore.userInfo?.id || ''))
const taskRequestedCacheEnabled = computed(() =>
  Boolean(taskDetail.value?.application.items[0]?.requestedCacheEnabled)
)
const taskRequestedCacheDays = computed(() =>
  taskDetail.value?.application.items[0]?.requestedCacheDays || 1
)

const loadApplications = async () => {
  applicationLoading.value = true
  try {
    const response = await getApplications({
      status: applicationStatus.value || undefined,
      scope: 'mine',
      page: applicationPage.page,
      pageSize: applicationPage.pageSize
    })
    applications.value = response.data || []
    applicationPage.total = response.total || 0
  } finally {
    applicationLoading.value = false
  }
}

const loadTasks = async () => {
  taskLoading.value = true
  try {
    const response = await getTasks()
    tasks.value = response.data || []
  } finally {
    taskLoading.value = false
  }
}

const loadGrants = async () => {
  grantLoading.value = true
  try {
    const response = await getGrants(grantStatus.value || undefined)
    grants.value = response.data || []
  } finally {
    grantLoading.value = false
  }
}

const loadProcessDiagnostics = async () => {
  processDiagnosticLoading.value = true
  processDiagnosticError.value = ''
  try {
    const response = await getProcessDiagnostics()
    processDiagnostics.value = response.data || []
  } catch {
    processDiagnostics.value = []
    processDiagnosticError.value = '流程诊断加载失败，请检查审批引擎状态或稍后重试'
  } finally {
    processDiagnosticLoading.value = false
  }
}

const handleTabChange = (name: TabsPaneContext['paneName']) => {
  if (name === 'applications') loadApplications()
  if (name === 'tasks') loadTasks()
  if (name === 'process-diagnostics') loadProcessDiagnostics()
  if (name === 'grants') loadGrants()
}

const resetDraft = () => {
  Object.assign(draft, {
    requestType: 'OPEN',
    callerId: null,
    apiKeyId: null,
    interfaceIds: [],
    businessPurpose: '',
    businessScene: '',
    expectedDailyCalls: 1000,
    requestedExpireAt: '',
    ticketNo: '',
    cacheEnabled: false,
    requestedCacheDays: undefined
  })
  apiKeys.value = []
  interfaces.value = []
}

const openCreate = async () => {
  resetDraft()
  editingId.value = null
  const response = await getEligibleCallers()
  callers.value = response.data || []
  createVisible.value = true
  const callerId = Number(route.query.callerId)
  const apiKeyId = Number(route.query.apiKeyId)
  if (callerId && callers.value.some(item => item.id === callerId)) {
    draft.callerId = callerId
    await handleCallerChange(callerId)
    if (apiKeyId && apiKeys.value.some(item => item.id === apiKeyId)) {
      draft.apiKeyId = apiKeyId
      await handleApiKeyChange(apiKeyId)
    }
  }
}

const goToInternalSystems = () => {
  createVisible.value = false
  void router.push('/caller')
}

const openEdit = async (row: ApiPermissionApplication) => {
  resetDraft()
  editingId.value = row.id
  const [callerResponse, detailResponse] = await Promise.all([
    getEligibleCallers(),
    getApplication(row.id)
  ])
  callers.value = callerResponse.data || []
  const application = detailResponse.data.application
  Object.assign(draft, {
    requestType: application.requestType,
    callerId: application.callerId,
    apiKeyId: application.apiKeyId,
    interfaceIds: [],
    businessPurpose: application.businessPurpose,
    businessScene: application.businessScene,
    expectedDailyCalls: application.expectedDailyCalls,
    requestedExpireAt: application.requestedExpireAt || '',
    ticketNo: application.ticketNo || '',
    cacheEnabled: Boolean(detailResponse.data.items[0]?.requestedCacheEnabled),
    requestedCacheDays: detailResponse.data.items[0]?.requestedCacheDays
  })
  const [apiKeyResponse, interfaceResponse] = await Promise.all([
    getCallerApiKeys(application.callerId),
    getInterfaceOptions(application.apiKeyId)
  ])
  apiKeys.value = apiKeyResponse.data || []
  interfaces.value = interfaceResponse.data || []
  draft.interfaceIds = detailResponse.data.items.map(item => item.interfaceId)
  createVisible.value = true
}

const handleCallerChange = async (callerId: number) => {
  draft.apiKeyId = null
  draft.interfaceIds = []
  interfaces.value = []
  const response = await getCallerApiKeys(callerId)
  apiKeys.value = response.data || []
}

const handleApiKeyChange = async (apiKeyId: number) => {
  draft.interfaceIds = []
  const response = await getInterfaceOptions(apiKeyId)
  interfaces.value = response.data || []
}

watch(draft, () => {
  draftValidationMessage.value = ''
}, { deep: true })

const validateDraft = (submit: boolean) => {
  const validationError = getDraftValidationError(draft, submit)
  draftValidationMessage.value = validationError || ''
  if (validationError) {
    ElMessage.warning(validationError)
    return false
  }
  return true
}

const saveDraft = async (submit: boolean) => {
  if (!validateDraft(submit)) return
  saving.value = true
  try {
    const payload: ApplicationDraft = {
      ...draft,
      requestedCacheDays: draft.cacheEnabled ? draft.requestedCacheDays : undefined
    }
    const response = editingId.value
      ? await updateApplication(editingId.value, payload)
      : await createApplication(payload)
    if (submit && response.data?.id) {
      await submitApplication(response.data.id, crypto.randomUUID())
      ElMessage.success('申请已提交')
    } else {
      ElMessage.success('草稿已保存')
    }
    createVisible.value = false
    await loadApplications()
  } finally {
    saving.value = false
  }
}

const resetEmergency = () => {
  const oneHourLater = new Date(Date.now() + 60 * 60 * 1000)
  const localTime = new Date(oneHourLater.getTime() - oneHourLater.getTimezoneOffset() * 60 * 1000)
  Object.assign(emergency, {
    callerId: null,
    apiKeyId: null,
    interfaceIds: [],
    expireAt: localTime.toISOString().slice(0, 19),
    reason: '',
    ticketNo: ''
  })
  emergencyApiKeys.value = []
  emergencyInterfaces.value = []
}

const openEmergency = async () => {
  resetEmergency()
  const response = await getEmergencyCallers()
  emergencyCallers.value = response.data || []
  emergencyVisible.value = true
}

const handleEmergencyCallerChange = async (callerId: number) => {
  emergency.apiKeyId = null
  emergency.interfaceIds = []
  emergencyInterfaces.value = []
  const response = await getEmergencyCallerApiKeys(callerId)
  emergencyApiKeys.value = response.data || []
}

const handleEmergencyApiKeyChange = async (apiKeyId: number) => {
  emergency.interfaceIds = []
  const response = await getEmergencyInterfaceOptions(apiKeyId)
  emergencyInterfaces.value = response.data || []
}

const saveEmergencyGrant = async () => {
  if (!emergency.callerId || !emergency.apiKeyId || emergency.interfaceIds.length === 0) {
    ElMessage.warning('请选择内部系统、API Key 和授权接口')
    return
  }
  if (emergency.ticketNo.trim().length < 3 || emergency.reason.trim().length < 10) {
    ElMessage.warning('请填写有效工单号和至少 10 个字的紧急原因')
    return
  }
  const expireAt = new Date(emergency.expireAt)
  const maxExpireAt = Date.now() + 24 * 60 * 60 * 1000
  if (!emergency.expireAt || Number.isNaN(expireAt.getTime())
    || expireAt.getTime() <= Date.now() || expireAt.getTime() > maxExpireAt) {
    ElMessage.warning('有效截止时间必须在未来 24 小时内')
    return
  }
  await ElMessageBox.confirm('紧急授权将立即生效并记录审计，确认继续？', '确认紧急授权', {
    type: 'warning'
  })
  emergencySaving.value = true
  try {
    await emergencyGrant({ ...emergency })
    ElMessage.success('紧急授权已生效')
    emergencyVisible.value = false
    await loadGrants()
  } finally {
    emergencySaving.value = false
  }
}

const submitDraft = async (row: ApiPermissionApplication) => {
  await ElMessageBox.confirm('提交后将进入审批流程，确认继续？', '提交申请', { type: 'warning' })
  await submitApplication(row.id, crypto.randomUUID())
  ElMessage.success('申请已提交')
  await loadApplications()
}

const cancelDraft = async (row: ApiPermissionApplication) => {
  await ElMessageBox.confirm('确认取消该申请？', '取消申请', { type: 'warning' })
  await cancelApplication(row.id)
  ElMessage.success('申请已取消')
  await loadApplications()
}

const copyDraft = async (row: ApiPermissionApplication) => {
  await copyApplication(row.id)
  ElMessage.success('已复制为新草稿')
  await loadApplications()
}

const openDetail = async (id: number) => {
  const response = await getApplication(id)
  detail.value = response.data
  detailVisible.value = true
}

const claim = async (row: ApprovalTask) => {
  await claimTask(row.task.id)
  ElMessage.success('任务已认领')
  await loadTasks()
}

const openTask = async (taskId: string) => {
  let response = await getTask(taskId)
  if (!response.data.task.assignee) {
    await claimTask(taskId)
    response = await getTask(taskId)
  }
  if (response.data.task.assignee !== currentUserId.value) {
    ElMessage.warning('任务已由其他审批人认领')
    return
  }
  taskDetail.value = response.data
  decision.approvedExpireAt = response.data.application.application.requestedExpireAt || ''
  const requestedCache = response.data.application.items[0]
  decision.approvedCacheEnabled = Boolean(requestedCache?.requestedCacheEnabled)
  decision.approvedCacheDays = requestedCache?.requestedCacheDays
  decision.comment = ''
  decision.formData = Object.fromEntries(response.data.policy.formFields.map(field => [
    field.id,
    field.type === 'boolean'
      ? field.defaultValue === 'true'
      : field.defaultValue
  ]))
  taskVisible.value = true
}

const releaseCurrentTask = async () => {
  if (!taskDetail.value) return
  await unclaimTask(taskDetail.value.task.id)
  ElMessage.success('任务已释放')
  taskVisible.value = false
  await loadTasks()
}

const decide = async (decisionValue: string) => {
  if (!taskDetail.value) return
  if (decisionValue === 'REJECT' && !decision.comment.trim()) {
    ElMessage.warning('驳回时必须填写审批意见')
    return
  }
  if (decisionValue === 'APPROVE'
    && taskDetail.value.policy.allowExpireAdjustment
    && !decision.approvedExpireAt) {
    ElMessage.warning('请选择批准有效截止时间')
    return
  }
  if (decisionValue === 'APPROVE'
    && taskRequestedCacheEnabled.value
    && decision.approvedCacheEnabled
    && (!decision.approvedCacheDays
      || decision.approvedCacheDays < 1
      || decision.approvedCacheDays > taskRequestedCacheDays.value)) {
    ElMessage.warning(`批准缓存时效必须在 1 到 ${taskRequestedCacheDays.value} 天之间`)
    return
  }
  const missingField = taskDetail.value.policy.formFields.find(field => {
    const value = decision.formData[field.id]
    return field.required && (value === undefined || value === null || value === '')
  })
  if (missingField) {
    ElMessage.warning(`请填写${missingField.name || missingField.id}`)
    return
  }
  decisionLoading.value = true
  try {
    const application = taskDetail.value.application.application
    await completeTask(taskDetail.value.task.id, {
      applicationVersion: application.version,
      decision: decisionValue,
      approvedExpireAt: decisionValue === 'APPROVE'
        && taskDetail.value.policy.allowExpireAdjustment
        ? decision.approvedExpireAt
        : undefined,
      comment: decision.comment.trim() || undefined,
      approvedCacheEnabled: decisionValue === 'APPROVE'
        ? decision.approvedCacheEnabled
        : false,
      approvedCacheDays: decisionValue === 'APPROVE' && decision.approvedCacheEnabled
        ? decision.approvedCacheDays
        : undefined,
      formData: Object.fromEntries(
        Object.entries(decision.formData).filter(([, value]) => value !== undefined && value !== '')
      )
    })
    ElMessage.success(`已提交${decisionLabel(decisionValue)}决定`)
    taskVisible.value = false
    await loadTasks()
  } finally {
    decisionLoading.value = false
  }
}

const decisionLabel = (value: string) => ({
  APPROVE: '批准',
  REJECT: '驳回',
  RETURN: '退回',
  TRANSFER: '转交'
}[value] || value)

const revoke = async (row: Grant) => {
  const result = await ElMessageBox.prompt('请输入撤销原因（至少 5 个字）', '撤销授权', {
    inputType: 'textarea',
    inputValidator: value => value.trim().length >= 5 || '撤销原因至少 5 个字',
    confirmButtonText: '确认撤销',
    cancelButtonText: '取消'
  })
  await revokeGrant(row.id, result.value)
  ElMessage.success('授权已撤销')
  await loadGrants()
}

const formatDateTime = (value?: string) => value ? value.replace('T', ' ').slice(0, 19) : '-'
const statusLabel = (status: string) => ({
  DRAFT: '草稿',
  IN_REVIEW: '审批中',
  PROVISIONING: '开通中',
  EFFECTIVE: '已生效',
  REJECTED: '已驳回',
  CANCELED: '已取消',
  ENGINE_ERROR: '流程异常',
  EXPIRED: '已到期',
  REVOKED: '已撤销'
}[status] || status)
const statusTagType = (status: string) => ({
  DRAFT: 'info',
  IN_REVIEW: 'warning',
  PROVISIONING: 'warning',
  EFFECTIVE: 'success',
  REJECTED: 'danger',
  ENGINE_ERROR: 'danger'
}[status] || 'info') as 'success' | 'warning' | 'info' | 'danger'
const grantStatusLabel = (status: string) => ({
  ACTIVE: '有效',
  EXPIRED: '已到期',
  REVOKED: '已撤销'
}[status] || status)
const sourceLabel = (source: string) => ({
  APPROVAL: '审批授权',
  EMERGENCY_ADMIN: '紧急授权',
  LEGACY_ADMIN: '历史授权'
}[source] || source)
const actionLabel = (action: string) => ({
  CREATE: '创建申请',
  SUBMIT: '提交审批',
  APPROVE: '审批通过',
  REJECT: '审批驳回',
  CANCEL: '取消申请',
  GRANT: '权限开通',
  REVOKE: '权限撤销',
  EXPIRE: '权限到期',
  EMERGENCY_GRANT: '紧急授权'
}[action] || action)

onMounted(async () => {
  await handleTabChange(activeTab.value)
  if (route.query.create === '1' && userStore.hasPermission('api-permission:apply')) {
    await openCreate()
  }
})
</script>

<style scoped>
.page-container { max-width: 1600px; margin: 0 auto; }
.page-header { display: flex; align-items: center; justify-content: space-between; gap: 24px; margin-bottom: 20px; }
.page-header h2 { margin: 0 0 4px; color: var(--color-text-primary); font-size: 24px; font-weight: 700; letter-spacing: -0.02em; }
.header-desc { margin: 0; color: var(--color-text-tertiary); font-size: 14px; }
.approval-tabs { --el-tabs-header-height: 44px; }
.toolbar { display: flex; align-items: center; justify-content: flex-end; gap: 12px; min-height: 40px; margin-bottom: 14px; }
.toolbar-note { margin-right: auto; color: var(--color-text-tertiary); font-size: 13px; }
.status-filter { width: 160px; }
.table-card { border-radius: 10px; }
.diagnostic-card { margin-bottom: 14px; border-radius: 10px; }
.diagnostic-alert { margin-bottom: 14px; }
.diagnostic-header { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin-bottom: 14px; }
.diagnostic-stats { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; color: var(--color-text-secondary); font-size: 13px; }
.diagnostic-roles { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; margin-bottom: 14px; }
.diagnostic-label { margin-right: 4px; color: var(--color-text-tertiary); font-size: 12px; }
.diagnostic-loading { min-height: 4px; }
.pagination-container { display: flex; justify-content: flex-end; margin-top: 20px; }
.link-button { padding: 0; border: 0; color: var(--el-color-primary); background: transparent; cursor: pointer; }
.code { font-family: var(--font-mono); font-size: 13px; }
.application-form { padding: 0 4px 24px; }
.draft-validation-alert { margin-bottom: 12px; text-align: left; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 18px; }
.application-form :deep(.el-select) { width: 100%; }
.field-hint { margin-top: 6px; color: var(--color-text-tertiary); font-size: 12px; line-height: 1.5; }
.empty-caller-hint { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 8px; color: var(--el-color-warning); font-size: 12px; line-height: 1.5; }
.option-meta { float: right; margin-left: 24px; color: var(--color-text-tertiary); font-size: 12px; }
.detail-heading, .task-summary { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 20px; }
.detail-heading h3, .task-summary h3 { margin: 6px 0 0; color: var(--color-text-primary); font-size: 20px; }
.section-title { margin: 26px 0 12px; color: var(--color-text-primary); font-size: 15px; }
.timeline-title { color: var(--color-text-primary); font-weight: 600; }
.timeline-meta { margin-top: 3px; color: var(--color-text-tertiary); font-size: 12px; }
.timeline-comment { margin-top: 7px; color: var(--color-text-secondary); line-height: 1.6; }
.task-node { color: var(--el-color-primary); font-size: 14px; font-weight: 600; }
.task-descriptions { margin: 18px 0; }
.decision-form { margin-top: 20px; }
.emergency-alert { margin-bottom: 18px; }

@media (max-width: 900px) {
  .page-header { flex-direction: column; align-items: stretch; gap: 14px; }
  .form-grid { grid-template-columns: 1fr; }
  .toolbar { flex-wrap: wrap; justify-content: flex-start; }
  .toolbar-note { width: 100%; }
}

@media (max-width: 480px) {
  .page-header h2 { font-size: 21px; }
  .header-desc { line-height: 1.6; }
  .status-filter { width: 100%; }
  .pagination-container { justify-content: center; overflow-x: auto; }
  .detail-heading, .task-summary { flex-direction: column; }
}
</style>
