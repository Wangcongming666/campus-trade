<template>
  <div class="admin-page" data-testid="admin-announcements">
    <div class="page-head">
      <h1>公告管理</h1>
      <el-button type="primary" @click="openCreate">新建公告</el-button>
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
      <el-table-column label="标题" min-width="220" prop="title" show-overflow-tooltip />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="enumTagType(ANNOUNCEMENT_STATUS_TAG_TYPES, row.status)">
            {{ enumLabel(ANNOUNCEMENT_STATUS_LABELS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发布时间" width="170" prop="publishTime" />
      <el-table-column label="更新时间" width="170" prop="updateTime" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
          <el-button
            v-if="row.status !== AnnouncementStatus.PUBLISHED"
            type="success"
            link
            @click="changeStatus(row, AnnouncementStatus.PUBLISHED)"
          >
            发布
          </el-button>
          <el-button
            v-if="row.status === AnnouncementStatus.PUBLISHED"
            type="warning"
            link
            @click="changeStatus(row, AnnouncementStatus.OFF_SHELF)"
          >
            下架
          </el-button>
          <el-button
            v-if="row.status === AnnouncementStatus.OFF_SHELF"
            type="info"
            link
            @click="changeStatus(row, AnnouncementStatus.DRAFT)"
          >
            改回草稿
          </el-button>
          <el-button type="danger" link @click="remove(row)">删除</el-button>
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

    <el-dialog v-model="formVisible" :title="editingId ? '编辑公告' : '新建公告'" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="120" show-word-limit />
        </el-form-item>
        <el-form-item label="正文" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="10" maxlength="20000" show-word-limit />
        </el-form-item>
        <el-form-item v-if="!editingId" label="创建后状态">
          <el-radio-group v-model="form.status">
            <el-radio-button :value="AnnouncementStatus.DRAFT">草稿</el-radio-button>
            <el-radio-button :value="AnnouncementStatus.PUBLISHED">直接发布</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createAdminAnnouncement,
  deleteAdminAnnouncement,
  getAdminAnnouncementDetail,
  getAdminAnnouncements,
  updateAdminAnnouncement,
  updateAdminAnnouncementStatus,
} from '@/api/admin'
import {
  ANNOUNCEMENT_STATUS_LABELS,
  ANNOUNCEMENT_STATUS_TAG_TYPES,
  AnnouncementStatus,
  enumLabel,
  enumOptions,
  enumTagType,
} from '@/constants/enums'
import { API_OFFLINE_TEXT, isApiOffline } from '@/constants/errorCodes'

const status = ref('')
const statusOptions = enumOptions(ANNOUNCEMENT_STATUS_LABELS)
const loading = ref(false)
const offline = ref(false)
const records = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const formVisible = ref(false)
const formRef = ref()
const acting = ref(false)
const editingId = ref('')
const form = reactive({
  title: '',
  content: '',
  status: AnnouncementStatus.DRAFT,
})
const rules = {
  title: [{ required: true, message: '请填写标题', trigger: 'blur' }],
  content: [{ required: true, message: '请填写正文', trigger: 'blur' }],
}

async function loadData() {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize }
    if (status.value) params.status = status.value
    const data = await getAdminAnnouncements(params)
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

function openCreate() {
  editingId.value = ''
  form.title = ''
  form.content = ''
  form.status = AnnouncementStatus.DRAFT
  formVisible.value = true
}

async function openEdit(row) {
  editingId.value = row.id
  form.title = row.title || ''
  form.content = row.content || ''
  formVisible.value = true
  try {
    const detail = await getAdminAnnouncementDetail(row.id)
    if (detail) {
      form.title = detail.title || form.title
      form.content = detail.content || form.content
    }
  } catch {
    // 列表已有字段时仍允许编辑
  }
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  acting.value = true
  try {
    if (editingId.value) {
      await updateAdminAnnouncement(editingId.value, { title: form.title.trim(), content: form.content })
      ElMessage.success('已保存')
    } else {
      await createAdminAnnouncement({
        title: form.title.trim(),
        content: form.content,
        status: form.status,
      })
      ElMessage.success(form.status === AnnouncementStatus.PUBLISHED ? '已发布' : '已保存为草稿')
    }
    formVisible.value = false
    await loadData()
  } finally {
    acting.value = false
  }
}

async function changeStatus(row, next) {
  const labels = {
    [AnnouncementStatus.PUBLISHED]: '发布',
    [AnnouncementStatus.OFF_SHELF]: '下架',
    [AnnouncementStatus.DRAFT]: '改回草稿',
  }
  await ElMessageBox.confirm(`确认${labels[next] || '修改'}「${row.title}」吗？`, '公告状态', { type: 'warning' })
  await updateAdminAnnouncementStatus(row.id, { status: next })
  ElMessage.success('状态已更新')
  await loadData()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除公告「${row.title}」吗？删除后不可恢复。`, '删除公告', {
    type: 'warning',
    confirmButtonText: '确认删除',
    confirmButtonClass: 'el-button--danger',
  })
  await deleteAdminAnnouncement(row.id)
  ElMessage.success('已删除')
  await loadData()
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;

  h1 {
    margin: 0;
    font-size: 20px;
  }
}

.toolbar {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
