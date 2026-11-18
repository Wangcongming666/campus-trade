<template>
  <div class="admin-page" data-testid="admin-goods">
    <div class="page-head">
      <h1>商品管理</h1>
    </div>
    <div class="toolbar">
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索标题或卖家"
        style="width: 220px"
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-select v-model="categoryId" clearable placeholder="全部分类" style="width: 160px" @change="reload">
        <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
      </el-select>
      <el-select v-model="status" clearable placeholder="全部状态" style="width: 150px" @change="reload">
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
      <el-table-column label="商品" min-width="280">
        <template #default="{ row }">
          <div class="goods-cell">
            <el-image :src="row.coverUrl" fit="cover" class="thumb" />
            <span class="title">{{ row.title }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="价格" width="110">
        <template #default="{ row }">¥{{ formatPrice(row.price) }}</template>
      </el-table-column>
      <el-table-column label="卖家" width="120">
        <template #default="{ row }">{{ row.seller?.nickname || '—' }}</template>
      </el-table-column>
      <el-table-column label="分类" width="120" prop="categoryName" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag size="small" :type="enumTagType(GOODS_STATUS_TAG_TYPES, row.status)">
            {{ enumLabel(GOODS_STATUS_LABELS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发布时间" width="170" prop="createTime" />
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="goDetail(row)">查看详情</el-button>
          <el-button
            v-if="row.status === GoodsStatus.ON_SHELF"
            type="danger"
            link
            @click="changeStatus(row, GoodsStatus.OFF_SHELF)"
          >
            强制下架
          </el-button>
          <el-button
            v-else-if="row.status === GoodsStatus.OFF_SHELF"
            type="success"
            link
            @click="changeStatus(row, GoodsStatus.ON_SHELF)"
          >
            重新上架
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
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminGoods, updateAdminGoodsStatus } from '@/api/admin'
import { getCategoryList } from '@/api/category'
import {
  GOODS_STATUS_LABELS,
  GOODS_STATUS_TAG_TYPES,
  GoodsStatus,
  enumLabel,
  enumOptions,
  enumTagType,
} from '@/constants/enums'
import { API_OFFLINE_TEXT, isApiOffline } from '@/constants/errorCodes'
import { formatPrice } from '@/utils/format'

const router = useRouter()
const keyword = ref('')
const categoryId = ref('')
const status = ref('')
const statusOptions = enumOptions(GOODS_STATUS_LABELS)
const categories = ref([])
const loading = ref(false)
const offline = ref(false)
const records = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10

async function loadCategories() {
  try {
    const data = await getCategoryList()
    const list = Array.isArray(data) ? data : []
    categories.value = list.filter((item) => item.parentId == null || item.parentId === '' || item.parentId === 0)
  } catch {
    categories.value = []
  }
}

async function loadData() {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (categoryId.value) params.categoryId = categoryId.value
    if (status.value) params.status = status.value
    const data = await getAdminGoods(params)
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

function goDetail(row) {
  router.push(`/goods/${row.id}`)
}

async function changeStatus(row, status) {
  const isOff = status === GoodsStatus.OFF_SHELF
  await ElMessageBox.confirm(
    isOff
      ? `确认强制下架「${row.title}」吗？下架后买家将无法继续购买。`
      : `确认将「${row.title}」重新上架吗？`,
    isOff ? '强制下架' : '重新上架',
    {
      type: 'warning',
      confirmButtonText: isOff ? '确认下架' : '确认上架',
      // 设计答复 #3：确认弹窗里的危险操作用实心 danger
      confirmButtonClass: isOff ? 'el-button--danger' : '',
    },
  )
  await updateAdminGoodsStatus(row.id, { status })
  ElMessage.success(isOff ? '已下架' : '已重新上架')
  await loadData()
}

onMounted(async () => {
  await loadCategories()
  await loadData()
})
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

.goods-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.thumb {
  width: 48px;
  height: 48px;
  border-radius: 6px;
  flex-shrink: 0;
}

.title {
  line-height: 1.4;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
