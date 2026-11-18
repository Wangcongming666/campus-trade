<template>
  <div class="search-page" data-testid="search-page">
    <DemoBanner />

    <div class="search-layout">
      <aside class="search-filters">
        <section class="filter-block">
          <h3>分类</h3>
          <button class="filter-all" type="button" :class="{ 'is-active': !filters.categoryId }" @click="setFilter({ categoryId: '' })">
            全部分类
          </button>
          <el-tree
            ref="treeRef"
            class="filter-tree"
            :data="categoryTree"
            node-key="id"
            :props="{ label: 'name', children: 'children' }"
            :current-node-key="filters.categoryId || undefined"
            highlight-current
            default-expand-all
            @node-click="onCategoryClick"
          />
        </section>

        <section class="filter-block">
          <h3>成色</h3>
          <el-radio-group size="small" :model-value="filters.condition" @change="(value) => setFilter({ condition: value || '' })">
            <el-radio :value="''">不限</el-radio>
            <el-radio v-for="item in conditionOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio>
          </el-radio-group>
        </section>

        <section class="filter-block">
          <h3>价格区间</h3>
          <div class="price-row">
            <el-input v-model="priceDraft.minPrice" placeholder="最低" inputmode="decimal" />
            <span>—</span>
            <el-input v-model="priceDraft.maxPrice" placeholder="最高" inputmode="decimal" />
          </div>
          <el-button class="price-apply" type="primary" plain @click="applyPrice">应用</el-button>
        </section>
      </aside>

      <main class="search-main">
        <div class="search-toolbar">
          <div>
            <h2>{{ resultTitle }}</h2>
            <p>共 {{ total }} 件闲置</p>
          </div>
          <el-radio-group :model-value="filters.sort" size="small" @change="(value) => setFilter({ sort: value }, false)">
            <el-radio-button v-for="item in sortOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
        </div>

        <GoodsGrid :items="records" :loading="loading" empty-text="没有找到符合条件的商品" />

        <div v-if="total > pageSize" class="search-pagination">
          <el-pagination
            background
            layout="prev, pager, next, total"
            :page-size="pageSize"
            :current-page="pageNum"
            :total="total"
            @current-change="onPageChange"
          />
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCategoryTree } from '@/api/category'
import { getGoodsList, searchGoods } from '@/api/goods'
import DemoBanner from '@/components/DemoBanner.vue'
import GoodsGrid from '@/components/GoodsGrid.vue'
import {
  GOODS_CONDITION_LABELS,
  GOODS_SORT_LABELS,
  GoodsSort,
  enumOptions,
} from '@/constants/enums'

const route = useRoute()
const router = useRouter()
const treeRef = ref()

const categoryTree = ref([])
const records = ref([])
const total = ref(0)
const loading = ref(false)
const pageSize = 8

const filters = reactive({
  keyword: '',
  categoryId: '',
  condition: '',
  minPrice: '',
  maxPrice: '',
  sort: GoodsSort.LATEST,
})
const priceDraft = reactive({ minPrice: '', maxPrice: '' })
const pageNum = ref(1)

const conditionOptions = enumOptions(GOODS_CONDITION_LABELS)
const sortOptions = enumOptions(GOODS_SORT_LABELS)

const resultTitle = computed(() => {
  if (filters.keyword) return `搜索「${filters.keyword}」`
  const name = findCategoryName(categoryTree.value, filters.categoryId)
  return name ? `${name}` : '全部闲置'
})

function findCategoryName(nodes, id) {
  if (!id) return ''
  for (const node of nodes || []) {
    if (String(node.id) === String(id)) return node.name
    const child = findCategoryName(node.children || [], id)
    if (child) return child
  }
  return ''
}

function compactQuery(source, currentPage) {
  const query = {}
  if (source.keyword) query.keyword = source.keyword
  if (source.categoryId) query.categoryId = String(source.categoryId)
  if (source.condition) query.condition = source.condition
  if (source.minPrice !== '' && source.minPrice != null) query.minPrice = String(source.minPrice)
  if (source.maxPrice !== '' && source.maxPrice != null) query.maxPrice = String(source.maxPrice)
  if (source.sort && source.sort !== GoodsSort.LATEST) query.sort = source.sort
  if (currentPage > 1) query.pageNum = String(currentPage)
  return query
}

function parseRouteQuery(query) {
  filters.keyword = typeof query.keyword === 'string' ? query.keyword : ''
  filters.categoryId = query.categoryId != null && query.categoryId !== '' ? String(query.categoryId) : ''
  filters.condition = typeof query.condition === 'string' ? query.condition : ''
  filters.minPrice = query.minPrice != null ? String(query.minPrice) : ''
  filters.maxPrice = query.maxPrice != null ? String(query.maxPrice) : ''
  filters.sort = typeof query.sort === 'string' && query.sort ? query.sort : GoodsSort.LATEST
  pageNum.value = Number(query.pageNum) > 0 ? Number(query.pageNum) : 1
  priceDraft.minPrice = filters.minPrice
  priceDraft.maxPrice = filters.maxPrice
}

function setFilter(patch, resetPage = true) {
  const next = {
    ...filters,
    ...patch,
  }
  const nextPage = resetPage ? 1 : pageNum.value
  router.push({ path: '/search', query: compactQuery(next, nextPage) })
}

function applyPrice() {
  setFilter({
    minPrice: priceDraft.minPrice.trim(),
    maxPrice: priceDraft.maxPrice.trim(),
  })
}

function onCategoryClick(node) {
  const id = String(node.id)
  setFilter({ categoryId: filters.categoryId === id ? '' : id })
}

function onPageChange(page) {
  router.push({ path: '/search', query: compactQuery(filters, page) })
}

async function fetchList() {
  loading.value = true
  try {
    const params = {
      pageNum: pageNum.value,
      pageSize,
      sort: filters.sort || GoodsSort.LATEST,
    }
    if (filters.keyword) params.keyword = filters.keyword
    if (filters.categoryId) params.categoryId = filters.categoryId
    if (filters.condition) params.condition = filters.condition
    if (filters.minPrice !== '') params.minPrice = filters.minPrice
    if (filters.maxPrice !== '') params.maxPrice = filters.maxPrice
    const fetcher = filters.keyword ? searchGoods : getGoodsList
    const data = (await fetcher(params)) || {}
    records.value = data.records || []
    total.value = data.total ?? 0
  } catch {
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

watch(
  () => route.query,
  async () => {
    parseRouteQuery(route.query)
    await fetchList()
    if (filters.categoryId && treeRef.value) {
      treeRef.value.setCurrentKey(filters.categoryId)
    } else if (treeRef.value) {
      treeRef.value.setCurrentKey(null)
    }
  },
  { immediate: true },
)

onMounted(async () => {
  try {
    const data = await getCategoryTree()
    categoryTree.value = Array.isArray(data) ? data : []
  } catch {
    categoryTree.value = []
  }
})
</script>

<style scoped lang="scss">
.search-page {
  padding: 20px 0 48px;
}

.search-layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 24px;
  align-items: start;
}

.search-filters {
  position: sticky;
  top: calc(var(--header-height) + 12px);
  padding: 12px 14px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--ct-radius-lg);
}

.filter-block + .filter-block {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
}

.filter-block h3 {
  margin: 0 0 6px;
  font-size: 14px;
}

.filter-all {
  display: block;
  width: 100%;
  margin-bottom: 8px;
  padding: 6px 8px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  color: var(--color-text-secondary);

  &.is-active,
  &:hover {
    background: var(--color-primary-bg);
    color: var(--color-primary-dark);
  }
}

.filter-tree {
  background: transparent;
}

.price-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.price-apply {
  width: 100%;
  margin-top: 10px;
}

.search-toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;

  h2 {
    margin: 0;
    font-size: 22px;
  }

  p {
    margin: 4px 0 0;
    color: var(--color-text-secondary);
    font-size: 13px;
  }
}

.search-pagination {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}

:deep(.el-radio-group) {
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: flex-start;
}

.search-toolbar :deep(.el-radio-group) {
  flex-direction: row;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .search-page {
    padding: 12px 0 32px;
  }

  .search-layout {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .search-filters {
    position: static;
  }

  .search-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
