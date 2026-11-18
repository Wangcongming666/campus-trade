<template>
  <div class="admin-page" data-testid="admin-reports">
    <div class="page-head">
      <h1>举报处理</h1>
    </div>
    <div class="toolbar">
      <el-select v-model="status" clearable placeholder="全部状态" style="width: 160px" @change="reload">
        <el-option
          v-for="item in statusOptions"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
      <el-button type="primary" @click="reload">查询</el-button>
    </div>

    <el-empty v-if="offline && !loading" :description="API_OFFLINE_TEXT" />
    <el-table v-else v-loading="loading" :data="records">
      <el-table-column label="对象" min-width="200">
        <template #default="{ row }">
          <div>{{ enumLabel(REPORT_TARGET_TYPE_LABELS, row.targetType) }} · {{ row.targetName || row.targetId }}</div>
          <div class="muted">ID {{ row.targetId }}</div>
        </template>
      </el-table-column>
      <el-table-column label="举报人" width="140">
        <template #default="{ row }">{{ row.reporter?.nickname || row.reporter?.username || '—' }}</template>
      </el-table-column>
      <el-table-column label="原因" min-width="120" prop="reason" show-overflow-tooltip />
      <el-table-column label="说明" min-width="180" prop="detail" show-overflow-tooltip />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="enumTagType(REPORT_STATUS_TAG_TYPES, row.status)">
            {{ enumLabel(REPORT_STATUS_LABELS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="170" prop="createTime" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === ReportStatus.PENDING">
            <el-button type="primary" link @click="openHandle(row, AuditResult.APPROVE)">成立</el-button>
            <el-button type="danger" link @click="openHandle(row, AuditResult.REJECT)">驳回</el-button>
          </template>
          <span v-else class="muted">{{ row.remark || '已处理' }}</span>
        </template>
      </el-table-column>
    </el-table>
    <div v-if="!offline" class="pager">
      <el-pagination
        background
        layout="prev, pager, next, total"
        :total="total"
        :page-size="pageSize"
        :current-page="pageNum"
        @current-change="onPage"
      />
    </div>

    <el-dialog v-model="handleVisible" :title="handleTitle" width="420px">
      <p class="hint">处理结果只记录，不会自动下架或封禁，如需处置请另行操作。</p>
      <el-input
        v-model="remark"
        type="textarea"
        :rows="4"
        maxlength="255"
        show-word-limit
        placeholder="处理说明（选填）"
      />
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitHandle">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAdminReports, handleAdminReport } from '@/api/admin'
import {
  AuditResult,
  REPORT_STATUS_LABELS,
  REPORT_STATUS_TAG_TYPES,
  REPORT_TARGET_TYPE_LABELS,
  ReportStatus,
  enumLabel,
  enumOptions,
  enumTagType,
} from '@/constants/enums'
import { API_OFFLINE_TEXT, isApiOffline } from '@/constants/errorCodes'

const status = ref('')
const statusOptions = enumOptions(REPORT_STATUS_LABELS)
const loading = ref(false)
const offline = ref(false)
const records = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const handleVisible = ref(false)
const handleTitle = ref('处理举报')
const remark = ref('')
const acting = ref(false)
const current = ref(null)
const currentResult = ref('')

async function loadData() {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize }
    if (status.value) params.status = status.value
    const data = await getAdminReports(params)
    records.value = data?.records || []
    total.value = data?.total || 0
    offline.value = false
  } catch (error) {
    records.value = []
    total.value = 0
    offline.value = isApiOffline(error)
  } finally {
    loading.value = false
  }
}

function reload() {
  pageNum.value = 1
  loadData()
}

function onPage(page) {
  pageNum.value = page
  loadData()
}

function openHandle(row, result) {
  current.value = row
  currentResult.value = result
  remark.value = ''
  handleTitle.value = result === AuditResult.APPROVE ? '标记举报成立' : '驳回举报'
  handleVisible.value = true
}

async function submitHandle() {
  if (!current.value) return
  acting.value = true
  try {
    const payload = { result: currentResult.value }
    if (remark.value.trim()) payload.remark = remark.value.trim()
    await handleAdminReport(current.value.id, payload)
    ElMessage.success('已处理')
    handleVisible.value = false
    await loadData()
  } finally {
    acting.value = false
  }
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.page-head h1 {
  margin: 0 0 16px;
  font-size: 20px;
}

.toolbar {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.muted {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.hint {
  margin: 0 0 12px;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
