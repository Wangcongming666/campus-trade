<template>
  <div class="home-page" data-testid="home-page">
    <DemoBanner />

    <!-- ① Hero：7 列文案 + 搜索，5 列视觉 -->
    <section class="hero">
      <div class="hero-main">
        <span class="eyebrow">
          <i class="eyebrow__live" />
          {{ schoolName }} · <NumberRoll :value="stats.onSale" /> 件闲置正在出售
        </span>
        <h1>
          毕业季好物<br />
          <span class="ct-grad-text">同校面交</span>，更安心
        </h1>
        <p class="lead">学生认证 · 智能推荐你感兴趣的闲置 · 发布时给出参考价格，让交易更透明。</p>

        <form class="hero-search" role="search" @submit.prevent="heroSearch">
          <el-select v-model="heroCategory" class="hero-search__cat" placeholder="全部分类" clearable :teleported="true">
            <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="String(item.id)" />
          </el-select>
          <el-icon class="hero-search__icon"><Search /></el-icon>
          <input v-model="heroKeyword" type="search" placeholder="试试搜「高等数学」「iPad」「自行车」" aria-label="搜索闲置" />
          <el-button type="primary" round native-type="submit" class="hero-search__btn">搜一搜</el-button>
        </form>

        <div v-if="categories.length" class="hot">
          <span class="hot__label">热门分类</span>
          <button
            v-for="item in categories.slice(0, 5)"
            :key="item.id"
            type="button"
            class="ct-chip is-sm"
            @click="goCategory(item.id)"
          >
            {{ item.name }}
          </button>
        </div>

        <div class="stats">
          <div>
            <b><NumberRoll :value="stats.onSale" /></b>
            <span>在售闲置</span>
          </div>
          <div>
            <b><NumberRoll :value="stats.todayNew" /></b>
            <span>今日上新</span>
          </div>
          <div>
            <b><NumberRoll :value="categories.length || null" /></b>
            <span>商品分类</span>
          </div>
        </div>
      </div>

      <div class="hero-side" @pointermove="onTilt" @pointerleave="resetTilt">
        <div class="hero-side__grid" />
        <div class="hero-side__stack" :style="tiltStyle">
          <router-link
            v-for="(item, index) in heroGoods"
            :key="item.id"
            :to="`/goods/${item.id}`"
            class="float-card"
            :class="`fc${index + 1}`"
          >
            <img :src="item.coverUrl" :alt="item.title" />
            <h4>{{ item.title }}</h4>
            <p class="num">¥{{ formatPrice(item.price) }}</p>
          </router-link>
          <div class="bubble-note bn1">
            <span class="bubble-note__ico bubble-note__ico--mint"><el-icon><School /></el-icon></span>
            <span>同校面交<small>当面验货，更安心</small></span>
          </div>
          <div class="bubble-note bn2">
            <span class="bubble-note__ico"><el-icon><MagicStick /></el-icon></span>
            <span>智能定价建议<small>参考同类商品的成交价</small></span>
          </div>
        </div>
      </div>
    </section>

    <!-- ② 公告：纵向轮播一行 -->
    <div v-if="announcements.length" class="ticker ct-card" data-testid="announce-bar">
      <span class="ticker__lbl"><el-icon><Bell /></el-icon>公告</span>
      <div class="ticker__vp">
        <ul :style="{ transform: `translateY(${-tickerIndex * 24}px)` }">
          <li v-for="item in announcements" :key="item.id">
            <router-link :to="`/announcements/${item.id}`">{{ item.title }}</router-link>
          </li>
        </ul>
      </div>
      <router-link class="ticker__all" :to="`/announcements/${announcements[tickerIndex]?.id}`">查看详情</router-link>
    </div>

    <!-- ③ 逛逛分类 -->
    <section v-if="categories.length" class="ct-section">
      <div class="ct-section-head">
        <div>
          <h2 class="ct-section-title">逛逛分类</h2>
          <p class="ct-section-sub">按一级分类浏览在售闲置</p>
        </div>
      </div>
      <nav class="cats" aria-label="商品分类">
        <button
          v-for="(item, index) in categories.slice(0, 8)"
          :key="item.id"
          v-reveal="index"
          type="button"
          class="cat-tile"
          @click="goCategory(item.id)"
        >
          <span class="cat-tile__ci" :style="{ '--c1': visualOf(item, index).c1, '--c2': visualOf(item, index).c2 }">
            <el-icon><component :is="visualOf(item, index).icon" /></el-icon>
          </span>
          {{ item.name }}
          <small>{{ categoryCounts[item.id] != null ? `${categoryCounts[item.id]} 件` : '\u00a0' }}</small>
        </button>
      </nav>
    </section>

    <!-- ④ 猜你喜欢 · 智能推荐 -->
    <section v-if="recommendLoading || recommendList.length || recommendError" class="ct-section rec" data-testid="recommend-section">
      <div class="ct-section-head">
        <div>
          <h2 class="ct-section-title">
            <el-icon class="rec__spark"><MagicStick /></el-icon>猜你喜欢
            <span class="ct-badge ct-badge--primary">智能推荐</span>
          </h2>
          <p class="ct-section-sub">{{ userStore.isLoggedIn ? '根据你的兴趣和浏览记录推荐' : '登录后按兴趣推荐，现在为你展示热门闲置' }}</p>
        </div>
        <div v-if="recommendList.length > recPerPage" class="rec-ctrl">
          <button type="button" class="ct-icon-btn rec-ctrl__btn" aria-label="上一页" @click="recRef?.prev()">
            <el-icon><ArrowLeft /></el-icon>
          </button>
          <button type="button" class="ct-icon-btn rec-ctrl__btn" aria-label="下一页" @click="recRef?.next()">
            <el-icon><ArrowRight /></el-icon>
          </button>
        </div>
      </div>
      <div v-if="recommendError" class="block-error">
        {{ recommendError }}<el-button link type="primary" @click="loadRecommend">点击重试</el-button>
      </div>
      <GoodsGrid v-else-if="!recommendList.length" :items="[]" :loading="recommendLoading" :skeleton-count="4" />
      <RecommendCarousel v-else ref="recRef" :items="recommendList" />
    </section>

    <!-- ⑤ 最新发布 -->
    <section class="ct-section">
      <div class="feed-bar">
        <div>
          <h2 class="ct-section-title">最新发布</h2>
          <p class="ct-section-sub">共 <span class="num">{{ total }}</span> 件在售闲置</p>
        </div>
        <Segmented v-model="sort" :options="sortOptions" @change="loadLatest(true)" />
      </div>
      <div class="filters">
        <button
          v-for="item in filterOptions"
          :key="item.key"
          type="button"
          class="ct-chip"
          :class="{ 'is-active': filterKey === item.key }"
          @click="setFilter(item.key)"
        >
          {{ item.label }}
        </button>
      </div>

      <div v-if="latestError && !records.length" class="block-error">
        {{ latestError }}<el-button link type="primary" @click="loadLatest(true)">点击重试</el-button>
      </div>
      <GoodsGrid v-else :items="records" :loading="loading && records.length === 0">
        <template #empty>
          <div class="feed-empty">
            <div class="feed-empty__art"><el-icon><Box /></el-icon></div>
            <template v-if="filterKey !== 'all' || activeCategoryId">
              <p>换个条件试试</p>
              <el-button round @click="setFilter('all')">清除筛选</el-button>
            </template>
            <template v-else>
              <p>这里还没有闲置，去发布第一件吧</p>
              <el-button type="primary" round @click="$router.push('/publish')">发布闲置</el-button>
            </template>
          </div>
        </template>
      </GoodsGrid>

      <div v-if="records.length" class="home-more">
        <div v-if="loading" class="home-more__loading">
          <div class="home-more__sk">
            <GoodsSkeleton v-for="n in 4" :key="n" />
          </div>
          <p class="loading-cap"><span class="loading-cap__spin" />正在为你加载更多闲置…</p>
        </div>
        <span v-else-if="finished" class="home-more__end">已经到底啦</span>
        <el-button v-else round @click="onLoadMore">加载更多</el-button>
        <div ref="sentinelRef" class="home-more__sentinel" />
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useIntervalFn, usePreferredReducedMotion } from '@vueuse/core'
import { getAnnouncements } from '@/api/announcement'
import { getCategoryList } from '@/api/category'
import { getGoodsList } from '@/api/goods'
import { getHomeRecommend } from '@/api/recommend'
import DemoBanner from '@/components/DemoBanner.vue'
import GoodsGrid from '@/components/GoodsGrid.vue'
import GoodsSkeleton from '@/components/GoodsSkeleton.vue'
import NumberRoll from '@/components/NumberRoll.vue'
import RecommendCarousel from '@/components/RecommendCarousel.vue'
import Segmented from '@/components/Segmented.vue'
import { usePagination } from '@/composables/usePagination'
import { API_OFFLINE_TEXT, isApiOffline } from '@/constants/errorCodes'
import { GoodsCondition, GoodsSort } from '@/constants/enums'
import { categoryVisual } from '@/constants/categoryVisual'
import { useUserStore } from '@/stores/user'
import { formatPrice } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const categories = ref([])
const categoryCounts = reactive({})
const announcements = ref([])
const recommendList = ref([])
const recommendLoading = ref(false)
const recommendError = ref('')
const latestError = ref('')
const activeCategoryId = ref('')
const sentinelRef = ref()
const recRef = ref()
const recPerPage = 4
const stats = reactive({ onSale: null, todayNew: null })
const heroGoods = ref([])
const heroKeyword = ref('')
const heroCategory = ref('')
let observer = null

const schoolName = computed(() => userStore.userInfo?.school || '本校')

// 设计答复（第二轮）#2：分段只保留「推荐」「最新」。
// 「推荐」用 GET /goods 的 sort=hot（浏览热度），个性化推荐在上方「猜你喜欢」；
// 「免费送」不加：接口能按 maxPrice=0 筛选，但发布校验要求售价 > 0，永远为空。
const sortOptions = [
  { label: '推荐', value: GoodsSort.HOT },
  { label: '最新', value: GoodsSort.LATEST },
]
const sort = ref(GoodsSort.HOT)

// 筛选 Chip：只用 GET /goods 已支持的 minPrice / maxPrice / condition
const filterOptions = [
  { key: 'all', label: '全部', params: {} },
  { key: 'lt50', label: '¥50 以下', params: { maxPrice: 50 } },
  { key: '50to200', label: '¥50–200', params: { minPrice: 50, maxPrice: 200 } },
  { key: 'new', label: '全新', params: { condition: GoodsCondition.NEW } },
  { key: 'likeNew', label: '几乎全新', params: { condition: GoodsCondition.LIKE_NEW } },
]
const filterKey = ref('all')

const { records, loading, total, finished, fetchData, loadMore, reset } = usePagination(getGoodsList, { pageSize: 8 })

function visualOf(item, index) {
  return categoryVisual(item.name, index)
}

function listParams() {
  const params = { sort: sort.value, ...(filterOptions.find((item) => item.key === filterKey.value)?.params || {}) }
  if (activeCategoryId.value) params.categoryId = activeCategoryId.value
  return params
}

function errorText(error) {
  return isApiOffline(error) ? API_OFFLINE_TEXT : '加载失败，'
}

async function loadAnnouncements() {
  try {
    const data = await getAnnouncements({ pageNum: 1, pageSize: 3 })
    announcements.value = (data?.records || (Array.isArray(data) ? data : [])).slice(0, 3)
  } catch {
    announcements.value = []
  }
}

async function loadRecommend() {
  recommendLoading.value = true
  recommendError.value = ''
  try {
    const data = await getHomeRecommend({ size: 12 })
    recommendList.value = Array.isArray(data) ? data : data?.records || []
  } catch (error) {
    recommendList.value = []
    recommendError.value = errorText(error)
  } finally {
    recommendLoading.value = false
  }
}

async function loadLatest(resetList = true) {
  latestError.value = ''
  if (resetList) reset()
  try {
    await fetchData(listParams())
  } catch (error) {
    latestError.value = errorText(error)
  }
}

async function onLoadMore() {
  try {
    await loadMore(listParams())
  } catch {
    // 加载更多失败时保留已有列表，按钮仍可再次点击
  }
}

async function loadStats() {
  // Hero 统计：在售总数取列表 total；今日上新按最新 100 条的发布日期统计。拿不到时显示「—」
  try {
    const data = await getGoodsList({ pageNum: 1, pageSize: 100, sort: GoodsSort.LATEST })
    const list = data?.records || []
    stats.onSale = data?.total ?? list.length
    const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
    stats.todayNew = list.filter((item) => String(item.createTime || '').startsWith(today)).length
    heroGoods.value = list.filter((item) => item.coverUrl).slice(0, 3)
  } catch {
    stats.onSale = null
    stats.todayNew = null
  }
}

async function loadCategories() {
  try {
    const data = await getCategoryList()
    const list = Array.isArray(data) ? data : data?.records || []
    categories.value = list.filter((item) => item.parentId == null || item.parentId === '' || item.parentId === 0)
  } catch {
    categories.value = []
  }
  // 分类件数：每个一级分类取一次 total（只读）
  await Promise.all(
    categories.value.slice(0, 8).map(async (item) => {
      try {
        const data = await getGoodsList({ pageNum: 1, pageSize: 1, categoryId: String(item.id) })
        categoryCounts[item.id] = data?.total ?? 0
      } catch {
        // 件数拿不到时不显示
      }
    }),
  )
}

function setFilter(key) {
  if (key === 'all' && activeCategoryId.value) {
    router.push({ path: '/' })
  }
  filterKey.value = key
  loadLatest(true)
}

function goCategory(id) {
  router.push({ path: '/search', query: { categoryId: String(id) } })
}

function heroSearch() {
  const keyword = heroKeyword.value.trim()
  const query = {}
  if (keyword) query.keyword = keyword
  if (heroCategory.value) query.categoryId = heroCategory.value
  router.push({ path: '/search', query })
}

// 公告：每条停留约 3s 上移 24px（motion.md §10），减少动态效果时不自动滚动
const tickerIndex = ref(0)
const reduced = usePreferredReducedMotion()
const ticker = useIntervalFn(
  () => {
    if (announcements.value.length > 1) tickerIndex.value = (tickerIndex.value + 1) % announcements.value.length
  },
  3000,
  { immediate: false },
)
watch(
  [reduced, () => announcements.value.length],
  ([value, count]) => (value === 'reduce' || count < 2 ? ticker.pause() : ticker.resume()),
  { immediate: true },
)

// Hero 浮动卡 3D 倾斜：根据鼠标位置设置 --rx/--ry（最大 10°）
const tilt = reactive({ rx: 0, ry: 0 })
const tiltStyle = computed(() => ({ '--rx': `${tilt.rx}deg`, '--ry': `${tilt.ry}deg` }))
function onTilt(event) {
  if (reduced.value === 'reduce' || event.pointerType !== 'mouse') return
  const rect = event.currentTarget.getBoundingClientRect()
  const px = (event.clientX - rect.left) / rect.width - 0.5
  const py = (event.clientY - rect.top) / rect.height - 0.5
  tilt.rx = Number((-py * 10).toFixed(2))
  tilt.ry = Number((px * 12).toFixed(2))
}
function resetTilt() {
  tilt.rx = 0
  tilt.ry = 0
}

function bindObserver() {
  if (observer) observer.disconnect()
  if (!sentinelRef.value) return
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0]?.isIntersecting && !loading.value && !finished.value && records.value.length) onLoadMore()
    },
    { rootMargin: '200px' },
  )
  observer.observe(sentinelRef.value)
}

watch(sentinelRef, () => bindObserver())

watch(
  () => route.query.categoryId,
  async (value) => {
    activeCategoryId.value = value != null && value !== '' ? String(value) : ''
    await loadLatest(true)
  },
)

watch(
  () => userStore.interestsVersion,
  () => loadRecommend(),
)

onMounted(async () => {
  activeCategoryId.value = route.query.categoryId ? String(route.query.categoryId) : ''
  await Promise.all([loadCategories(), loadAnnouncements(), loadRecommend(), loadLatest(true), loadStats()])
  bindObserver()
})

onBeforeUnmount(() => {
  if (observer) observer.disconnect()
})
</script>

<style scoped lang="scss">
.home-page {
  padding: 28px 0 24px;
}

/* ---------- Hero ---------- */
.hero {
  display: grid;
  grid-template-columns: 7fr 5fr;
  gap: 24px;
  align-items: stretch;
}

.hero-main {
  position: relative;
  overflow: hidden;
  padding: 48px 48px 40px;
  border: 1px solid var(--ct-border);
  border-radius: var(--ct-radius-xl);
  background: var(--ct-surface);
  box-shadow: var(--ct-shadow-2);

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background: var(--ct-gradient-mesh);
  }

  &::after {
    content: '';
    position: absolute;
    top: -180px;
    right: -160px;
    width: 420px;
    height: 420px;
    border-radius: 50%;
    background: conic-gradient(from 120deg, #5b5cf0, #c264f0, #ff6b4a, #14c9a2, #5b5cf0);
    filter: blur(70px);
    opacity: 0.25;
    animation: ct-spin 18s linear infinite;
  }

  > * {
    position: relative;
    z-index: 1;
  }

  h1 {
    margin: 22px 0 0;
    font-size: var(--ct-fs-display);
    font-weight: 800;
    line-height: 1.15;
    letter-spacing: -0.025em;
  }
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px 6px 6px;
  border: 1px solid var(--ct-border);
  border-radius: 99px;
  background: var(--ct-surface);
  box-shadow: var(--ct-shadow-1);
  color: var(--ct-text-2);
  font-size: 13px;
  font-weight: 600;
}

.eyebrow__live {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--ct-mint-soft);

  &::before {
    content: '';
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--ct-mint);
    animation: ct-pulse 1.6s infinite;
  }
}

.lead {
  max-width: 560px;
  margin: 14px 0 0;
  color: var(--ct-text-2);
  font-size: 16px;
}

.hero-search {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 600px;
  height: 64px;
  margin-top: 28px;
  padding: 8px 8px 8px 10px;
  border: 1px solid var(--ct-border);
  border-radius: var(--ct-radius-pill);
  background: var(--ct-surface);
  box-shadow: var(--ct-shadow-3);
  transition: box-shadow var(--ct-dur-base), border-color var(--ct-dur-base);

  &:focus-within {
    border-color: var(--ct-primary);
    box-shadow: var(--ct-shadow-3), var(--ct-ring);
  }

  input {
    flex: 1;
    min-width: 0;
    border: 0;
    outline: 0;
    background: none;
    color: var(--ct-text);
    font: inherit;
    font-size: 16px;

    &::placeholder {
      color: var(--ct-text-3);
    }
  }
}

.hero-search__cat {
  width: 128px;
  flex: none;
  border-right: 1px solid var(--ct-border);
  padding-right: 6px;

  :deep(.el-select__wrapper) {
    background: transparent;
    box-shadow: none !important;
    font-weight: 700;
  }
}

.hero-search__icon {
  color: var(--ct-text-3);
}

.hero-search__btn {
  height: 48px;
  padding: 0 26px;
  font-size: 15px;
}

.hot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 18px;
  font-size: 13px;

  .ct-chip {
    background: color-mix(in srgb, var(--ct-surface) 60%, transparent);
  }
}

.hot__label {
  color: var(--ct-text-3);
}

.stats {
  display: flex;
  gap: 40px;
  margin-top: 34px;

  b {
    display: block;
    font-size: 28px;
    font-weight: 800;
    line-height: 1.2;
  }

  > div > span {
    color: var(--ct-text-3);
    font-size: 13px;
  }
}

.hero-side {
  position: relative;
  min-height: 480px;
  overflow: hidden;
  border-radius: var(--ct-radius-xl);
  background: var(--ct-gradient-brand);
  box-shadow: var(--ct-shadow-glow);
  perspective: 1000px;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background:
      radial-gradient(circle at 20% 20%, rgba(255, 255, 255, 0.35), transparent 40%),
      radial-gradient(circle at 80% 90%, rgba(255, 107, 74, 0.55), transparent 45%);
  }
}

.hero-side__grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.08) 1px, transparent 1px);
  background-size: 32px 32px;
  -webkit-mask-image: radial-gradient(circle at 50% 50%, #000 30%, transparent 75%);
  mask-image: radial-gradient(circle at 50% 50%, #000 30%, transparent 75%);
}

.hero-side__stack {
  position: absolute;
  inset: 0;
  transform: rotateX(var(--rx, 0deg)) rotateY(var(--ry, 0deg));
  transform-style: preserve-3d;
  transition: transform 0.4s var(--ct-ease-out);
}

.float-card {
  position: absolute;
  width: 190px;
  padding: 10px;
  border-radius: 22px;
  background: color-mix(in srgb, var(--ct-surface) 92%, transparent);
  box-shadow: var(--ct-shadow-4);
  color: var(--ct-text);
  animation: ct-float-y 6s ease-in-out infinite;

  img {
    display: block;
    width: 100%;
    aspect-ratio: 1.15;
    border-radius: 16px;
    object-fit: cover;
  }

  h4 {
    margin: 10px 4px 4px;
    overflow: hidden;
    font-size: 13px;
    font-weight: 700;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  p {
    margin: 0 4px 4px;
    color: var(--ct-price);
    font-size: 18px;
    font-weight: 800;
  }
}

.fc1 { top: 40px; left: 34px; --r: -8deg; }
.fc2 { top: 170px; right: 28px; --r: 7deg; animation-delay: -2s; }
.fc3 { bottom: 28px; left: 70px; width: 180px; --r: -2deg; animation-delay: -4s; }

.bubble-note {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px 10px 10px;
  border-radius: 18px;
  background: color-mix(in srgb, var(--ct-surface) 95%, transparent);
  box-shadow: var(--ct-shadow-3);
  color: var(--ct-text);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  animation: ct-float-y 5s ease-in-out infinite;

  small {
    display: block;
    color: var(--ct-text-3);
    font-weight: 500;
  }
}

.bubble-note__ico {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: var(--ct-sun-soft);
  color: var(--ct-sun-ink);

  &--mint {
    background: var(--ct-mint-soft);
    color: var(--ct-mint-ink);
  }
}

.bn1 { top: 30px; right: 24px; animation-delay: -1s; }
.bn2 { right: 36px; bottom: 70px; animation-delay: -3s; }

/* ---------- 公告 ---------- */
.ticker {
  display: flex;
  align-items: center;
  gap: 14px;
  height: 52px;
  margin-top: 24px;
  padding: 0 18px;
  border-radius: var(--ct-radius-pill);
}

.ticker__lbl {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--ct-accent);
  font-weight: 800;
  white-space: nowrap;
}

.ticker__vp {
  position: relative;
  flex: 1;
  height: 24px;
  overflow: hidden;

  ul {
    margin: 0;
    padding: 0;
    list-style: none;
    transition: transform var(--ct-dur-slower) var(--ct-ease-emphasized);
  }

  li {
    height: 24px;
    overflow: hidden;
    line-height: 24px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  a {
    color: var(--ct-text-2);

    &:hover {
      color: var(--ct-primary);
    }
  }
}

.ticker__all {
  flex: none;
  padding: 6px 14px;
  border-radius: var(--ct-radius-pill);
  background: var(--ct-primary-soft);
  color: var(--ct-primary);
  font-size: 12px;
  font-weight: 700;
}

/* ---------- 分类图块 ---------- */
.cats {
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
  gap: 16px;
}

.cat-tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 8px 16px;
  border: 1px solid var(--ct-border);
  border-radius: var(--ct-radius-lg);
  background: var(--ct-surface);
  color: var(--ct-text);
  font: inherit;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition:
    opacity var(--ct-dur-slower) var(--ct-ease-out),
    transform var(--ct-dur-slow) var(--ct-ease-spring),
    box-shadow var(--ct-dur-slow);

  &.is-in:hover,
  &:not([data-reveal]):hover {
    transform: translateY(-6px) scale(1.02);
    box-shadow: var(--ct-shadow-3);
    transition-delay: 0s;
  }

  &:hover .cat-tile__ci {
    transform: rotate(-8deg) scale(1.08);
  }

  small {
    margin-top: -6px;
    color: var(--ct-text-3);
    font-size: 12px;
    font-weight: 500;
  }
}

.cat-tile__ci {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: 18px;
  background: linear-gradient(135deg, var(--c1), var(--c2));
  box-shadow: 0 12px 24px -10px var(--c2);
  color: #fff;
  font-size: 26px;
  transition: transform var(--ct-dur-slow) var(--ct-ease-spring);
}

/* ---------- 推荐 ---------- */
.rec {
  padding: 28px;
  border: 1px solid var(--ct-border);
  border-radius: var(--ct-radius-xl);
  background: linear-gradient(180deg, var(--ct-primary-soft), transparent 70%);
}

.rec__spark {
  color: var(--ct-primary);
}

.rec-ctrl {
  display: flex;
  gap: 8px;
}

.rec-ctrl__btn {
  border: 1px solid var(--ct-border);
  background: var(--ct-surface);
}

/* ---------- 最新发布 ---------- */
.feed-bar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
}

.block-error {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 40px 0;
  border: 1px dashed var(--ct-border-strong);
  border-radius: var(--ct-radius-lg);
  color: var(--ct-text-3);
}

.feed-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 28px 0;
  color: var(--ct-text-2);

  p {
    margin: 0;
  }
}

.feed-empty__art {
  display: grid;
  place-items: center;
  width: 88px;
  height: 88px;
  border-radius: 28px;
  background: var(--ct-gradient-brand);
  box-shadow: var(--ct-shadow-glow);
  color: #fff;
  font-size: 40px;
  transform: rotate(-6deg);
}

.home-more {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  margin-top: 24px;
}

.home-more__loading {
  width: 100%;
}

.home-more__sk {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--ct-gutter);
}

.loading-cap {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin: 20px 0 0;
  color: var(--ct-text-3);
  font-size: 13px;
}

.loading-cap__spin {
  width: 14px;
  height: 14px;
  border: 2px solid var(--ct-primary-soft-2);
  border-top-color: var(--ct-primary);
  border-radius: 50%;
  animation: ct-spin 0.7s linear infinite;
}

.home-more__end {
  color: var(--ct-text-3);
  font-size: 13px;
}

.home-more__sentinel {
  width: 100%;
  height: 1px;
}

/* ---------- 响应式 ---------- */
@media (max-width: 1024px) {
  .hero {
    grid-template-columns: 1fr;
  }

  .hero-side {
    min-height: 280px;
  }

  .fc3 {
    display: none;
  }

  .cats {
    display: flex;
    overflow-x: auto;

    .cat-tile {
      flex: 0 0 112px;
    }
  }

  .home-more__sk {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .hero-main {
    padding: 28px 20px;

    h1 {
      font-size: 32px;
    }
  }

  .stats {
    gap: 24px;
  }

  .feed-bar {
    flex-direction: column;
    align-items: flex-start;
  }

  .home-more__sk {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero-main::after,
  .float-card,
  .bubble-note {
    animation: none;
  }
}
</style>
