<template>
  <div class="dash" data-testid="admin-dashboard">
    <Teleport to="#admin-topbar-extra" defer>
      <Segmented v-model="days" :options="RANGE_OPTIONS" @change="loadData" />
    </Teleport>

    <el-empty v-if="offline && !loading" :description="API_OFFLINE_TEXT" />
    <template v-else>
      <!-- KPI -->
      <div class="kpi-grid">
        <article v-for="(item, index) in kpis" :key="item.key" v-reveal="index" class="kpi" :class="`is-${item.tone}`">
          <div class="kpi__top">
            <span class="kpi__ico"><el-icon><component :is="item.icon" /></el-icon></span>
            <span class="kpi__label">{{ item.label }}</span>
          </div>
          <div class="kpi__value">
            <div v-if="!loaded" class="ct-sk" style="width: 90px; height: 32px" />
            <NumberRoll v-else :value="item.value" :decimals="item.decimals || 0" :prefix="item.prefix || ''" />
          </div>
          <div class="kpi__foot">
            <span v-if="item.delta != null" class="kpi__delta">近 {{ days }} 天 +{{ item.delta }}</span>
            <router-link v-else-if="item.link" :to="item.link" class="kpi__link">{{ item.linkText }} →</router-link>
            <span v-else class="kpi__sub">{{ item.sub }}</span>
            <svg v-if="item.spark?.length > 1" class="spark" viewBox="0 0 100 32" preserveAspectRatio="none" aria-hidden="true">
              <defs>
                <linearGradient :id="`sg-${item.key}`" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0" :stop-color="item.color" stop-opacity="0.28" />
                  <stop offset="1" :stop-color="item.color" stop-opacity="0" />
                </linearGradient>
              </defs>
              <path :d="sparkPath(item.spark, true)" :fill="`url(#sg-${item.key})`" />
              <path :d="sparkPath(item.spark)" fill="none" :stroke="item.color" stroke-width="2" vector-effect="non-scaling-stroke" stroke-linecap="round" />
            </svg>
          </div>
        </article>
      </div>

      <!-- 趋势 + 分类占比 -->
      <div class="row row--8-4">
        <section class="ct-card chart-card">
          <header class="chart-card__head">
            <div>
              <h3>增长趋势</h3>
              <p>新增用户 / 发布 / 订单 · 近 {{ days }} 天</p>
            </div>
          </header>
          <div class="chart-wrap">
            <div ref="lineRef" class="chart chart--line" />
            <div v-if="!loaded" class="ct-sk chart-sk" />
            <el-empty v-else-if="!hasTrend" class="chart-empty" :image-size="60" description="所选时间范围内暂无数据" />
          </div>
        </section>
        <section class="ct-card chart-card">
          <header class="chart-card__head">
            <div>
              <h3>分类占比</h3>
              <p>各分类商品数量</p>
            </div>
          </header>
          <div class="chart-wrap">
            <div ref="pieRef" class="chart chart--pie" />
            <div v-if="!loaded" class="ct-sk chart-sk" />
            <el-empty v-else-if="!shareTotal" class="chart-empty" :image-size="60" description="所选时间范围内暂无数据" />
          </div>
        </section>
      </div>

      <!-- 待审核 + 热门商品 -->
      <div class="row row--8-4">
        <section class="ct-card chart-card">
          <header class="chart-card__head">
            <div>
              <h3>待审核商品</h3>
              <p>最早提交的 5 件 · 共 {{ stats.pendingGoodsAudit || 0 }} 件</p>
            </div>
            <el-button round class="ct-btn-soft" @click="$router.push('/admin/audit')">进入审核中心</el-button>
          </header>
          <el-table v-loading="pendingLoading" :data="pendingList" class="pending-table" row-key="id">
            <el-table-column label="商品" min-width="160">
              <template #default="{ row }">
                <div class="cell-goods">
                  <img :src="row.coverUrl" alt="" />
                  <span>{{ row.title }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="卖家" min-width="84">
              <template #default="{ row }">{{ row.seller?.nickname || '—' }}</template>
            </el-table-column>
            <el-table-column label="价格" width="88">
              <template #default="{ row }"><span class="num price">¥{{ formatPrice(row.price) }}</span></template>
            </el-table-column>
            <el-table-column label="提交时间" min-width="96">
              <template #default="{ row }"><span class="num muted">{{ formatRelativeTime(row.createTime) }}</span></template>
            </el-table-column>
            <el-table-column label="操作" width="76" fixed="right">
              <template #default>
                <el-button link type="primary" @click="$router.push('/admin/audit')">去审核</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <div class="all-done">
                <span>🎉</span>
                <p>全部处理完了</p>
              </div>
            </template>
          </el-table>
        </section>
        <section class="ct-card chart-card">
          <header class="chart-card__head">
            <div>
              <h3>热门商品</h3>
              <p>按浏览量 Top {{ hotList.length || 10 }}</p>
            </div>
          </header>
          <ol v-if="hotList.length" class="hot-list">
            <li v-for="(item, index) in hotList" :key="item.id || index">
              <span class="hot-list__rank num" :class="{ 'is-top': index < 3 }">{{ index + 1 }}</span>
              <span class="hot-list__title">{{ (item.title || '').replace('【演示】', '') }}</span>
              <span class="hot-list__bar"><i :style="{ width: `${hotPercent(item)}%` }" /></span>
              <b class="num">{{ item.viewCount || 0 }}</b>
            </li>
          </ol>
          <div v-else-if="!loaded" class="ct-sk" style="height: 240px; border-radius: 16px" />
          <el-empty v-else :image-size="60" description="所选时间范围内暂无数据" />
        </section>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { getAdminStats, getAuditGoods } from '@/api/admin'
import NumberRoll from '@/components/NumberRoll.vue'
import Segmented from '@/components/Segmented.vue'
import { API_OFFLINE_TEXT, isApiOffline } from '@/constants/errorCodes'
import { useChart } from '@/composables/useChart'
import { isReducedMotion } from '@/composables/useMotion'
import { isDark } from '@/composables/useTheme'
import { formatPrice, formatRelativeTime } from '@/utils/format'

const RANGE_OPTIONS = [
  { label: '近 7 天', value: 7 },
  { label: '近 30 天', value: 30 },
]

const loading = ref(false)
const loaded = ref(false)
const offline = ref(false)
const days = ref(7)
const stats = ref({
  userCount: 0,
  goodsCount: 0,
  orderCount: 0,
  gmv: 0,
  pendingGoodsAudit: 0,
  pendingStudentVerify: 0,
  trend: [],
  categoryShares: [],
  hotGoods: [],
})
const pendingList = ref([])
const pendingLoading = ref(false)

// design-system.md §8.5 图表配色
const PALETTE = ['#5B5CF0', '#14C9A2', '#FF6B4A', '#FFC53D', '#EC4899', '#38BDF8']

const trend = computed(() => stats.value.trend || [])
const hasTrend = computed(() => trend.value.length > 0)
const sum = (key) => trend.value.reduce((total, item) => total + (Number(item[key]) || 0), 0)
const shareTotal = computed(() => (stats.value.categoryShares || []).reduce((total, item) => total + (Number(item.count) || 0), 0))
const hotList = computed(() => (stats.value.hotGoods || []).slice(0, 8))
const hotMax = computed(() => Math.max(1, ...hotList.value.map((item) => Number(item.viewCount) || 0)))

const kpis = computed(() => [
  {
    key: 'users',
    label: '注册用户',
    icon: 'User',
    tone: 'primary',
    color: PALETTE[0],
    value: stats.value.userCount,
    delta: sum('newUsers'),
    spark: trend.value.map((item) => Number(item.newUsers) || 0),
  },
  {
    key: 'goods',
    label: '商品总数',
    icon: 'Goods',
    tone: 'mint',
    color: PALETTE[1],
    value: stats.value.goodsCount,
    delta: sum('newGoods'),
    spark: trend.value.map((item) => Number(item.newGoods) || 0),
  },
  {
    key: 'orders',
    label: '订单数',
    icon: 'Tickets',
    tone: 'hot',
    color: PALETTE[2],
    value: stats.value.orderCount,
    delta: sum('newOrders'),
    spark: trend.value.map((item) => Number(item.newOrders) || 0),
  },
  {
    key: 'gmv',
    label: '成交额',
    icon: 'Wallet',
    tone: 'sun',
    color: PALETTE[3],
    value: Number(stats.value.gmv) || 0,
    decimals: 2,
    prefix: '¥',
    sub: '已完成订单金额',
  },
  {
    key: 'pending',
    label: '待审核',
    icon: 'Checked',
    tone: 'pink',
    color: PALETTE[4],
    value: (Number(stats.value.pendingGoodsAudit) || 0) + (Number(stats.value.pendingStudentVerify) || 0),
    link: '/admin/audit',
    linkText: `商品 ${stats.value.pendingGoodsAudit || 0} · 认证 ${stats.value.pendingStudentVerify || 0}`,
  },
])

function sparkPath(values, closed = false) {
  const max = Math.max(...values, 1)
  const step = 100 / Math.max(values.length - 1, 1)
  const pts = values.map((v, i) => [i * step, 30 - (v / max) * 26])
  const line = pts.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(2)},${y.toFixed(2)}`).join(' ')
  return closed ? `${line} L100,32 L0,32 Z` : line
}

function hotPercent(item) {
  return Math.round(((Number(item.viewCount) || 0) / hotMax.value) * 100)
}

// 主题切换后等 html.dark 生效再重新读取 CSS 变量，useChart 会用新 option 重新 setOption
const themeTick = ref(0)
watch(isDark, async () => {
  await nextTick()
  themeTick.value += 1
})

function cssVar(name, fallback) {
  if (typeof window === 'undefined') return fallback
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback
}
function axisTheme() {
  const border = cssVar('--ct-border', '#e6e8f2')
  const text2 = cssVar('--ct-text-2', '#50566c')
  return {
    axisLine: { lineStyle: { color: border } },
    axisTick: { show: false },
    axisLabel: { color: text2 },
    splitLine: { lineStyle: { color: border, type: 'dashed' } },
  }
}
// 设计答复 #5：只有主线带渐变面积，28% → 0%
function lineArea(hex) {
  return {
    color: {
      type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
      colorStops: [{ offset: 0, color: `${hex}47` }, { offset: 1, color: `${hex}00` }],
    },
  }
}
const chartAnim = () =>
  isReducedMotion()
    ? { animation: false }
    : { animationDuration: 1800, animationEasing: 'cubicOut', animationDurationUpdate: 600 }

const lineRef = ref()
const pieRef = ref()

// 设计答复：轴线/网格线 --ct-border，文字 --ct-text-2，tooltip 底色 --ct-surface-2
function tooltipTheme() {
  return {
    backgroundColor: cssVar('--ct-surface-2', '#fafbfe'),
    borderColor: cssVar('--ct-border', '#e6e8f2'),
    textStyle: { color: cssVar('--ct-text-2', '#50566c') },
    extraCssText: 'border-radius:12px;box-shadow:var(--ct-shadow-2);',
  }
}

const lineOption = computed(() => (themeTick.value, {
  ...chartAnim(),
  color: PALETTE.slice(0, 3),
  tooltip: { trigger: 'axis', ...tooltipTheme() },
  legend: { top: 0, right: 0, data: ['新增用户', '新增发布', '新增订单'], icon: 'roundRect', itemWidth: 12, itemHeight: 4, textStyle: { color: cssVar('--ct-text-2', '#50566c') } },
  grid: { left: 36, right: 12, top: 36, bottom: 28 },
  xAxis: { type: 'category', boundaryGap: false, data: trend.value.map((item) => String(item.date).slice(5)), ...axisTheme(), splitLine: { show: false } },
  yAxis: { type: 'value', minInterval: 1, ...axisTheme(), axisLine: { show: false } },
  series: [
    { name: '新增用户', type: 'line', smooth: 0.3, symbol: 'none', lineStyle: { width: 3 }, areaStyle: lineArea(PALETTE[0]), data: trend.value.map((item) => item.newUsers) },
    { name: '新增发布', type: 'line', smooth: 0.3, symbol: 'none', lineStyle: { width: 2 }, data: trend.value.map((item) => item.newGoods) },
    { name: '新增订单', type: 'line', smooth: 0.3, symbol: 'none', lineStyle: { width: 2, type: 'dashed' }, data: trend.value.map((item) => item.newOrders) },
  ],
}))

const pieOption = computed(() => (themeTick.value, {
  ...chartAnim(),
  color: [...PALETTE, '#8B5CF6', '#8A8FA3'],
  tooltip: { trigger: 'item', formatter: '{b}：{c}（{d}%）', ...tooltipTheme() },
  title: {
    text: String(shareTotal.value),
    subtext: '件商品',
    left: 'center',
    top: '33%',
    textStyle: { fontSize: 26, fontWeight: 800, color: cssVar('--ct-text', '#141728'), fontFamily: 'Inter Variable, Inter, sans-serif' },
    subtextStyle: { fontSize: 12, color: cssVar('--ct-text-3', '#8a8fa3') },
    itemGap: 2,
  },
  legend: { type: 'scroll', bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { color: cssVar('--ct-text-2', '#50566c') }, pageIconColor: cssVar('--ct-text-2', '#50566c'), pageIconInactiveColor: cssVar('--ct-border', '#e6e8f2'), pageTextStyle: { color: cssVar('--ct-text-3', '#8a8fa3') } },
  series: [
    {
      type: 'pie',
      radius: ['56%', '76%'],
      center: ['50%', '42%'],
      avoidLabelOverlap: true,
      label: { show: false },
      itemStyle: { borderRadius: 8, borderColor: cssVar('--ct-surface', '#fff'), borderWidth: 3 },
      emphasis: { scale: true, scaleSize: 6 },
      data: (stats.value.categoryShares || []).map((item) => ({ name: item.categoryName, value: item.count })),
    },
  ],
}))

useChart(lineRef, lineOption)
useChart(pieRef, pieOption)

async function loadPending() {
  pendingLoading.value = true
  try {
    const data = await getAuditGoods({ pageNum: 1, pageSize: 5 })
    pendingList.value = data?.records || []
  } catch {
    pendingList.value = []
  } finally {
    pendingLoading.value = false
  }
}

async function loadData() {
  loading.value = true
  try {
    const data = await getAdminStats({ days: days.value })
    stats.value = { ...stats.value, ...(data || {}) }
    offline.value = false
  } catch (error) {
    offline.value = isApiOffline(error)
  } finally {
    loading.value = false
    loaded.value = true
  }
}

onMounted(() => {
  loadData()
  loadPending()
})
</script>

<style scoped lang="scss">
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
}

.kpi {
  position: relative;
  overflow: hidden;
  padding: 18px 20px 14px;
  border: 1px solid var(--ct-border);
  border-radius: var(--ct-radius-xl);
  background: var(--ct-surface);
  box-shadow: var(--ct-shadow-1);
  transition:
    transform var(--ct-dur-base) var(--ct-ease-out),
    box-shadow var(--ct-dur-base),
    opacity 0.56s var(--ct-ease-out);

  &:hover {
    transform: translateY(-3px);
    box-shadow: var(--ct-shadow-3);
  }
}

.kpi__top {
  display: flex;
  align-items: center;
  gap: 10px;
}

.kpi__ico {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 11px;
  font-size: 16px;

  .is-primary & {
    background: var(--ct-primary-soft);
    color: var(--ct-primary);
  }

  .is-mint & {
    background: var(--ct-mint-soft);
    color: var(--ct-mint);
  }

  .is-hot & {
    background: var(--ct-accent-soft);
    color: var(--ct-accent);
  }

  .is-sun & {
    background: var(--ct-sun-soft);
    color: var(--ct-sun-ink);
  }

  .is-pink & {
    background: rgba(236, 72, 153, 0.12);
    color: #ec4899;
  }
}

.kpi__label {
  color: var(--ct-text-2);
  font-size: 13px;
  font-weight: 600;
}

.kpi__value {
  min-height: 40px;
  margin-top: 12px;
  font-size: 28px;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.kpi__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 8px;
  min-height: 32px;
  margin-top: 4px;
  font-size: 12px;
}

.kpi__delta {
  color: var(--ct-mint);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.kpi__sub {
  color: var(--ct-text-3);
}

.kpi__link {
  color: var(--ct-primary);
  font-weight: 600;
}

.spark {
  flex: none;
  width: 56px;
  height: 28px;
  overflow: visible;
}

.row {
  display: grid;
  gap: 16px;
  margin-top: 16px;

  &--8-4 {
    grid-template-columns: minmax(0, 8fr) minmax(0, 4fr);
  }
}

.chart-card {
  padding: 20px 22px;
}

.chart-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;

  h3 {
    margin: 0;
    font-size: 16px;
    font-weight: 800;
  }

  p {
    margin: 2px 0 0;
    color: var(--ct-text-3);
    font-size: 12px;
  }
}

.chart-wrap {
  position: relative;
}

.chart {
  width: 100%;
}

.chart--line,
.chart--pie {
  height: 280px;
}

.chart-sk {
  position: absolute;
  inset: 0;
  border-radius: 16px;
}

.chart-empty {
  position: absolute;
  inset: 0;
  padding: 0;
  background: var(--ct-surface);
}

.cell-goods {
  display: flex;
  align-items: center;
  gap: 10px;

  img {
    flex: none;
    width: 36px;
    height: 36px;
    border-radius: 10px;
    object-fit: cover;
  }

  span {
    overflow: hidden;
    font-weight: 600;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.price {
  color: var(--ct-price);
  font-weight: 700;
}

.muted {
  color: var(--ct-text-3);
}

.all-done {
  padding: 24px 0;
  line-height: 1.4;

  span {
    font-size: 32px;
  }

  p {
    margin: 4px 0 0;
    color: var(--ct-text-3);
  }
}

.hot-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin: 8px 0 0;
  padding: 0;
  list-style: none;

  li {
    display: grid;
    grid-template-columns: 22px minmax(0, 1fr) 70px 36px;
    align-items: center;
    gap: 10px;
    font-size: 13px;
  }

  b {
    text-align: right;
  }
}

.hot-list__rank {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 7px;
  background: var(--ct-surface-sunken);
  color: var(--ct-text-3);
  font-size: 12px;
  font-weight: 700;

  &.is-top {
    background: var(--ct-gradient-brand);
    color: #fff;
  }
}

.hot-list__title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hot-list__bar {
  height: 6px;
  overflow: hidden;
  border-radius: 99px;
  background: var(--ct-surface-sunken);

  i {
    display: block;
    height: 100%;
    border-radius: inherit;
    background: linear-gradient(90deg, var(--ct-primary), var(--ct-mint));
    transform-origin: left;
    animation: bar-grow 1.2s var(--ct-ease-out) both;
  }
}

@keyframes bar-grow {
  from {
    transform: scaleX(0);
  }
}

@media (max-width: 1279px) {
  .kpi-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .row--8-4 {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .hot-list__bar i {
    animation: none;
  }

  .kpi {
    transition: none;
  }
}
</style>
