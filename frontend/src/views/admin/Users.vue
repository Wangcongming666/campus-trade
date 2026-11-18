<template>
  <div class="admin-page" data-testid="admin-users">
    <div class="page-head">
      <h1>用户管理</h1>
    </div>
    <div class="toolbar">
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索用户名 / 手机号 / 学号"
        style="width: 260px"
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-select v-model="status" clearable placeholder="全部状态" style="width: 140px" @change="reload">
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
      <el-table-column label="用户" min-width="200">
        <template #default="{ row }">
          <div class="user-cell">
            <el-avatar :size="32" :src="row.avatar">{{ (row.nickname || '用').slice(0, 1) }}</el-avatar>
            <div>
              <div>{{ row.nickname }}</div>
              <div class="muted">{{ row.username }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="手机号" width="130" prop="phone" />
      <el-table-column label="学号" width="130" prop="studentNo" />
      <el-table-column label="角色" width="100">
        <template #default="{ row }">{{ enumLabel(ROLE_LABELS, row.role) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="enumTagType(USER_STATUS_TAG_TYPES, row.status)">
            {{ enumLabel(USER_STATUS_LABELS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="认证" width="110">
        <template #default="{ row }">
          {{ enumLabel(STUDENT_AUTH_STATUS_LABELS, row.studentAuthStatus, '未认证') }}
        </template>
      </el-table-column>
      <el-table-column label="信誉" width="80" prop="creditScore" />
      <el-table-column label="注册时间" width="170" prop="createTime" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="openDetail(row)">详情</el-button>
          <el-button
            v-if="canToggle(row)"
            :type="row.status === UserStatus.BANNED ? 'success' : 'danger'"
            link
            @click="toggleStatus(row)"
          >
            {{ row.status === UserStatus.BANNED ? '解封' : '封禁' }}
          </el-button>
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

    <el-drawer v-model="detailVisible" title="用户详情" size="400px">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="用户名">{{ detail.username }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ detail.nickname }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detail.phone || '—' }}</el-descriptions-item>
        <el-descriptions-item label="学号">{{ detail.studentNo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="学校">{{ detail.school || '—' }}</el-descriptions-item>
        <el-descriptions-item label="角色">{{ enumLabel(ROLE_LABELS, detail.role) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ enumLabel(USER_STATUS_LABELS, detail.status) }}</el-descriptions-item>
        <el-descriptions-item label="认证">
          {{ enumLabel(STUDENT_AUTH_STATUS_LABELS, detail.studentAuthStatus, '未认证') }}
        </el-descriptions-item>
        <el-descriptions-item label="信誉分">{{ detail.creditScore }}</el-descriptions-item>
        <el-descriptions-item label="注册时间">{{ detail.createTime }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminUsers, updateAdminUserStatus } from '@/api/admin'
import {
  ROLE_LABELS,
  STUDENT_AUTH_STATUS_LABELS,
  USER_STATUS_LABELS,
  USER_STATUS_TAG_TYPES,
  Role,
  UserStatus,
  enumLabel,
  enumOptions,
  enumTagType,
} from '@/constants/enums'
import { API_OFFLINE_TEXT, isApiOffline } from '@/constants/errorCodes'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const keyword = ref('')
const status = ref('')
const statusOptions = enumOptions(USER_STATUS_LABELS)
const loading = ref(false)
const offline = ref(false)
const records = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const detailVisible = ref(false)
const detail = ref(null)

function canToggle(row) {
  if (row.role === Role.ADMIN) return false
  if (String(row.id) === String(userStore.userInfo?.id || '')) return false
  return true
}

async function loadData() {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (status.value) params.status = status.value
    const data = await getAdminUsers(params)
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

function openDetail(row) {
  detail.value = row
  detailVisible.value = true
}

async function toggleStatus(row) {
  const next = row.status === UserStatus.BANNED ? UserStatus.NORMAL : UserStatus.BANNED
  const action = next === UserStatus.BANNED ? '封禁' : '解封'
  await ElMessageBox.confirm(`确认${action}用户「${row.nickname || row.username}」吗？`, `${action}确认`, {
    type: 'warning',
    confirmButtonText: `确认${action}`,
    confirmButtonClass: next === UserStatus.BANNED ? 'el-button--danger' : '',
  })
  await updateAdminUserStatus(row.id, { status: next })
  ElMessage.success(`已${action}`)
  await loadData()
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
  margin-bottom: 12px;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.muted {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
