<template>
  <div class="audit-page" data-testid="admin-audit">
    <div class="page-head">
      <h1>审核中心</h1>
    </div>

    <el-tabs v-model="tab" @tab-change="onTabChange">
      <el-tab-pane :label="`商品审核（${goodsTotal}）`" name="goods">
        <el-empty v-if="goodsOffline && !goodsLoading" :description="API_OFFLINE_TEXT" />
        <template v-else>
        <div class="toolbar">
          <el-button type="primary" :disabled="!selectedGoods.length" @click="batchApprove">
            批量通过（{{ selectedGoods.length }}）
          </el-button>
        </div>
        <el-table
          v-loading="goodsLoading"
          :data="goodsList"
          row-key="id"
          :expand-row-keys="expanded"
          @expand-change="onExpand"
          @selection-change="(rows) => (selectedGoods = rows)"
        >
          <el-table-column type="selection" width="44" />
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="expand">
                <p class="expand__desc">{{ row.description || '暂无描述' }}</p>
                <div class="expand__imgs">
                  <el-image
                    v-for="(url, index) in imagesOf(row)"
                    :key="`${row.id}-${index}`"
                    :src="url"
                    :preview-src-list="imagesOf(row)"
                    :initial-index="index"
                    preview-teleported
                    fit="cover"
                    class="expand__img"
                  />
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="封面" width="80">
            <template #default="{ row }">
              <el-image :src="row.coverUrl" fit="cover" class="thumb" />
            </template>
          </el-table-column>
          <el-table-column label="标题" min-width="220" prop="title" show-overflow-tooltip />
          <el-table-column label="价格" width="100">
            <template #default="{ row }">¥{{ formatPrice(row.price) }}</template>
          </el-table-column>
          <el-table-column label="卖家" width="120">
            <template #default="{ row }">{{ row.seller?.nickname || '—' }}</template>
          </el-table-column>
          <el-table-column label="提交时间" width="170" prop="createTime" />
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click="approveGoods(row)">通过</el-button>
              <el-button type="danger" link @click="openReject('goods', row)">驳回</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            background
            layout="prev, pager, next, total"
            :total="goodsTotal"
            :page-size="goodsPageSize"
            :current-page="goodsPage"
            @current-change="(page) => { goodsPage = page; loadGoods() }"
          />
        </div>
        </template>
      </el-tab-pane>

      <el-tab-pane :label="`学生认证（${verifyTotal}）`" name="students">
        <el-empty v-if="verifyOffline && !verifyLoading" :description="API_OFFLINE_TEXT" />
        <template v-else>
        <div class="toolbar">
          <el-select v-model="verifyStatus" clearable placeholder="全部状态" style="width: 160px" @change="loadVerifies">
            <el-option
              v-for="item in verifyStatusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </div>
        <el-table v-loading="verifyLoading" :data="verifies">
          <el-table-column label="申请人" min-width="140">
            <template #default="{ row }">{{ row.nickname }}（{{ row.username }}）</template>
          </el-table-column>
          <el-table-column label="真实姓名" width="110" prop="realName" />
          <el-table-column label="学号" width="140" prop="studentNo" />
          <el-table-column label="学校" min-width="120" prop="school" />
          <el-table-column label="学生证" width="100">
            <template #default="{ row }">
              <el-image
                :src="row.cardImageUrl"
                :preview-src-list="[row.cardImageUrl]"
                preview-teleported
                fit="cover"
                class="thumb"
              />
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="enumTagType(STUDENT_AUTH_STATUS_TAG_TYPES, row.status)">
                {{ enumLabel(STUDENT_AUTH_STATUS_LABELS, row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="提交时间" width="170" prop="createTime" />
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === StudentAuthStatus.PENDING">
                <el-button type="primary" link @click="approveStudent(row)">通过</el-button>
                <el-button type="danger" link @click="openReject('student', row)">驳回</el-button>
              </template>
              <span v-else-if="row.reason" class="muted">{{ row.reason }}</span>
              <span v-else class="muted">已处理</span>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            background
            layout="prev, pager, next, total"
            :total="verifyTotal"
            :page-size="verifyPageSize"
            :current-page="verifyPage"
            @current-change="(page) => { verifyPage = page; loadVerifies() }"
          />
        </div>
        </template>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="rejectVisible" :title="rejectTitle" width="420px">
      <el-input
        v-model="rejectReason"
        type="textarea"
        :rows="4"
        maxlength="200"
        show-word-limit
        placeholder="请填写驳回原因（必填）"
      />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="acting" @click="submitReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { inject, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { auditGoods, auditStudentVerify, getAuditGoods, getStudentVerifies } from '@/api/admin'
import {
  AuditResult,
  STUDENT_AUTH_STATUS_LABELS,
  STUDENT_AUTH_STATUS_TAG_TYPES,
  StudentAuthStatus,
  enumLabel,
  enumOptions,
  enumTagType,
} from '@/constants/enums'
import { API_OFFLINE_TEXT, isApiOffline } from '@/constants/errorCodes'
import { formatPrice } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const tab = ref(route.query.tab === 'students' ? 'students' : 'goods')

const goodsLoading = ref(false)
const goodsOffline = ref(false)
const goodsList = ref([])
const goodsTotal = ref(0)
const goodsPage = ref(1)
const goodsPageSize = 10
const selectedGoods = ref([])
const expanded = ref([])

const verifyLoading = ref(false)
const verifyOffline = ref(false)
const verifies = ref([])
const verifyTotal = ref(0)
const verifyPage = ref(1)
const verifyPageSize = 10
const verifyStatus = ref(StudentAuthStatus.PENDING)
const refreshAdminBadge = inject('refreshAdminBadge', () => {})
const verifyStatusOptions = enumOptions(STUDENT_AUTH_STATUS_LABELS)

const rejectVisible = ref(false)
const rejectTitle = ref('驳回')
const rejectReason = ref('')
const rejectTarget = ref(null)
const rejectKind = ref('goods')
const acting = ref(false)

function imagesOf(row) {
  const list = row.images?.length ? row.images : row.coverUrl ? [row.coverUrl] : []
  return list.map(String)
}

function onTabChange(name) {
  router.replace({ path: '/admin/audit', query: { tab: name } })
}

function onExpand(row, rows) {
  expanded.value = rows.map((item) => item.id)
}

async function loadGoods() {
  goodsLoading.value = true
  try {
    const data = await getAuditGoods({ pageNum: goodsPage.value, pageSize: goodsPageSize })
    goodsList.value = data?.records || []
    goodsTotal.value = data?.total || 0
    goodsOffline.value = false
    if (goodsList.value[0] && !expanded.value.length) {
      expanded.value = [goodsList.value[0].id]
    }
  } catch (error) {
    goodsList.value = []
    goodsTotal.value = 0
    goodsOffline.value = isApiOffline(error)
  } finally {
    goodsLoading.value = false
  }
}

async function loadVerifies() {
  verifyLoading.value = true
  try {
    const params = { pageNum: verifyPage.value, pageSize: verifyPageSize }
    if (verifyStatus.value) params.status = verifyStatus.value
    const data = await getStudentVerifies(params)
    verifies.value = data?.records || []
    verifyTotal.value = data?.total || 0
    verifyOffline.value = false
  } catch (error) {
    verifies.value = []
    verifyTotal.value = 0
    verifyOffline.value = isApiOffline(error)
  } finally {
    verifyLoading.value = false
  }
}

async function approveGoods(row) {
  await auditGoods(row.id, { result: AuditResult.APPROVE })
  ElMessage.success('已通过')
  refreshAdminBadge()
  await loadGoods()
}

async function batchApprove() {
  if (!selectedGoods.value.length) return
  await ElMessageBox.confirm(`确认批量通过 ${selectedGoods.value.length} 件商品？`, '批量通过', { type: 'warning' })
  await Promise.all(selectedGoods.value.map((item) => auditGoods(item.id, { result: AuditResult.APPROVE })))
  ElMessage.success('已批量通过')
  selectedGoods.value = []
  refreshAdminBadge()
  await loadGoods()
}

async function approveStudent(row) {
  await auditStudentVerify(row.id, { result: AuditResult.APPROVE })
  ElMessage.success('已通过认证')
  refreshAdminBadge()
  await loadVerifies()
}

function openReject(kind, row) {
  rejectKind.value = kind
  rejectTarget.value = row
  rejectReason.value = ''
  rejectTitle.value = kind === 'student' ? '驳回学生认证' : '驳回商品'
  rejectVisible.value = true
}

async function submitReject() {
  if (!rejectReason.value.trim()) {
    ElMessage.warning('驳回必须填写原因')
    return
  }
  acting.value = true
  try {
    if (rejectKind.value === 'student') {
      await auditStudentVerify(rejectTarget.value.id, {
        result: AuditResult.REJECT,
        reason: rejectReason.value.trim(),
      })
      await loadVerifies()
    } else {
      await auditGoods(rejectTarget.value.id, {
        result: AuditResult.REJECT,
        reason: rejectReason.value.trim(),
      })
      await loadGoods()
    }
    ElMessage.success('已驳回')
    refreshAdminBadge()
    rejectVisible.value = false
  } finally {
    acting.value = false
  }
}

watch(
  () => route.query.tab,
  (value) => {
    tab.value = value === 'students' ? 'students' : 'goods'
  },
)

onMounted(async () => {
  await Promise.all([loadGoods(), loadVerifies()])
})
</script>

<style scoped lang="scss">
.page-head h1,
.audit-page h1 {
  margin: 0 0 12px;
  font-size: 20px;
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.thumb {
  width: 48px;
  height: 48px;
  border-radius: 6px;
}

.expand {
  padding: 4px 48px 12px;
}

.expand__desc {
  margin: 0 0 10px;
  color: var(--color-text-secondary);
  line-height: 1.6;
}

.expand__imgs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.expand__img {
  width: 96px;
  height: 96px;
  border-radius: 8px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.muted {
  color: var(--color-text-secondary);
  font-size: 13px;
}
</style>
