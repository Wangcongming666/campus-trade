<template>
  <div class="detail-page" data-testid="goods-detail">
    <DemoBanner />

    <!-- 加载态：主图骨架 + 缩略骨架；右侧标题、价格块、按钮组骨架 -->
    <div v-if="loading && !goods" class="detail detail--sk" aria-hidden="true">
      <div class="gallery">
        <div class="thumbs"><div v-for="n in 4" :key="n" class="ct-sk thumb-sk" /></div>
        <div class="ct-sk stage-sk" />
      </div>
      <div class="ct-card info-card">
        <div class="ct-sk" style="height: 22px; width: 80%" />
        <div class="ct-sk" style="height: 22px; width: 55%; margin-top: 10px" />
        <div class="ct-sk" style="height: 72px; margin-top: 20px; border-radius: 20px" />
        <div class="ct-sk" style="height: 52px; margin-top: 20px; border-radius: 20px" />
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else-if="!loading && !goods" class="detail-missing">
      <el-empty :description="loadError || '这件闲置已经找不到了'" :image-size="140">
        <el-button type="primary" round @click="$router.push('/')">回首页逛逛</el-button>
      </el-empty>
    </div>

    <template v-else-if="goods">
      <nav class="crumb" aria-label="面包屑">
        <router-link to="/">首页</router-link>
        <el-icon><ArrowRight /></el-icon>
        <template v-if="crumbs.parent">
          <router-link :to="{ path: '/search', query: { categoryId: String(crumbs.parent.id) } }">{{ crumbs.parent.name }}</router-link>
          <el-icon><ArrowRight /></el-icon>
        </template>
        <router-link
          v-if="goods.categoryId"
          :to="{ path: '/search', query: { categoryId: String(goods.categoryId) } }"
        >
          {{ goods.categoryName || '分类' }}
        </router-link>
        <el-icon><ArrowRight /></el-icon>
        <b>{{ goods.title }}</b>
      </nav>

      <div class="detail">
        <!-- 图集：竖排缩略图 + 1:1 主图 -->
        <div class="gallery">
          <div v-if="images.length > 1" class="thumbs">
            <button
              v-for="(image, index) in images"
              :key="`${image}-thumb-${index}`"
              type="button"
              class="thumbs__item"
              :class="{ 'is-on': index === activeIndex }"
              :aria-label="`第 ${index + 1} 张`"
              @click="setActive(index)"
            >
              <img :src="image" alt="" />
            </button>
          </div>
          <div class="stage" :class="{ 'is-single': images.length <= 1 }" @click="viewerVisible = true">
            <Transition name="stage" mode="out-in">
              <img :key="images[activeIndex]" :src="images[activeIndex]" :alt="goods.title" />
            </Transition>
            <div class="stage__tl">
              <span class="ct-badge ct-badge--glass"><el-icon><Camera /></el-icon>共 {{ images.length }} 张</span>
            </div>
            <span v-if="images.length > 1" class="stage__count ct-badge ct-badge--glass num">{{ activeIndex + 1 }} / {{ images.length }}</span>
            <div v-if="saleMask" class="stage__mask">{{ saleMask }}</div>
          </div>
          <el-image-viewer
            v-if="viewerVisible"
            :url-list="images"
            :initial-index="activeIndex"
            teleported
            @close="viewerVisible = false"
          />
        </div>

        <!-- 信息面板（sticky） -->
        <div class="info">
          <div class="ct-card info-card">
            <el-alert
              v-if="ownerAlert"
              class="info-alert"
              :type="ownerAlert.type"
              show-icon
              :closable="false"
              :title="ownerAlert.title"
            />

            <div class="info-top">
              <div class="tags">
                <span class="ct-badge" :class="statusBadge.cls">
                  <el-icon v-if="goods.status === GoodsStatus.ON_SHELF"><CircleCheck /></el-icon>{{ statusBadge.text }}
                </span>
                <span v-if="conditionLabel" class="ct-badge ct-badge--primary">{{ conditionLabel }}</span>
                <span v-if="tradeLabel" class="ct-badge ct-badge--gray"><el-icon><Van /></el-icon>{{ tradeLabel }}</span>
                <span v-if="sameSchool" class="ct-badge ct-badge--sun">同校</span>
              </div>
              <el-button
                v-if="!isOwner"
                link
                type="info"
                class="report-link"
                data-testid="report-goods-btn"
                @click="openReport"
              >
                <el-icon><Flag /></el-icon>举报
              </el-button>
            </div>

            <h1 class="title">{{ goods.title }}</h1>

            <div class="price-box">
              <span class="price-box__now num"><small>¥</small>{{ formatPrice(goods.price) }}</span>
              <span v-if="showOrigin" class="price-box__origin num">原价 ¥{{ formatPrice(goods.originalPrice) }}</span>
              <span v-if="saveRate" class="ct-badge ct-badge--hot price-box__save">省 {{ saveRate }}%</span>
            </div>

            <!-- 智能定价参考：数据来自 POST /price/suggest，拿不到时整块隐藏 -->
            <div v-if="suggestion && suggestion.suggestedPrice != null" class="ai-price">
              <div class="ai-price__hd">
                <span class="ai-price__ico"><el-icon><MagicStick /></el-icon></span>
                智能定价参考
                <span v-if="priceRange" class="ai-price__sub">同类 {{ priceRange.sampleCount ?? 0 }} 件样本</span>
                <span v-else class="ct-badge ct-badge--gray ai-price__few">同类样本较少</span>
              </div>
              <p v-if="!priceRange" class="ai-price__ref">
                参考价 <b class="num">¥{{ formatPrice(suggestion.suggestedPrice) }}</b>
                <span>当前标价 <span class="num">¥{{ formatPrice(goods.price) }}</span></span>
              </p>
              <template v-if="priceRange">
              <div class="range">
                <div class="range__band" :style="{ left: `${priceRange.bandLeft}%`, right: `${100 - priceRange.bandRight}%` }" />
                <div class="range__mark" :style="{ left: `${priceRange.markLeft}%` }" />
                <div class="range__tip num" :style="{ left: `${priceRange.markLeft}%` }">当前 ¥{{ formatPrice(goods.price) }}</div>
              </div>
              <div class="range-scale num">
                <span>¥{{ formatPrice(priceRange.lo) }}</span>
                <b>建议区间 ¥{{ formatPrice(priceRange.min) }} – ¥{{ formatPrice(priceRange.max) }}</b>
                <span>¥{{ formatPrice(priceRange.hi) }}</span>
              </div>
              </template>
            </div>

            <div class="meta">
              <div>
                <el-icon><CollectionTag /></el-icon>
                <p><b>{{ goods.categoryName || '—' }}</b><span>商品分类</span></p>
              </div>
              <div>
                <el-icon><Clock /></el-icon>
                <p><b>{{ formatRelativeTime(goods.createTime) }}</b><span>发布时间</span></p>
              </div>
              <div>
                <el-icon><View /></el-icon>
                <p><b class="num">{{ goods.viewCount ?? 0 }} 次</b><span>浏览</span></p>
              </div>
              <div>
                <el-icon><Star /></el-icon>
                <p><b class="num">{{ goods.favoriteCount ?? 0 }} 人</b><span>收藏</span></p>
              </div>
            </div>

            <div v-if="isOwner" class="actions actions--owner">
              <el-button v-if="canEdit" type="primary" size="large" @click="goEdit">
                <el-icon class="el-icon--left"><Edit /></el-icon>编辑商品
              </el-button>
              <el-popconfirm
                v-if="canTakeDown"
                title="确认下架这件商品吗？下架后买家将无法继续购买。"
                confirm-button-text="确认下架"
                cancel-button-text="取消"
                width="260"
                @confirm="takeDown"
              >
                <template #reference>
                  <el-button size="large" class="ct-btn-ghost">下架</el-button>
                </template>
              </el-popconfirm>
            </div>
            <div v-else class="actions">
              <LikeButton
                v-if="canFavorite"
                v-model="goods.favorited"
                v-model:count="goods.favoriteCount"
                variant="big"
                :on-toggle="onToggleFavorite"
              />
              <el-button size="large" class="ct-btn-ghost act-btn" :loading="chatLoading" @click="contactSeller">
                <el-icon class="el-icon--left"><ChatDotRound /></el-icon>联系卖家
              </el-button>
              <el-button
                size="large"
                class="ct-btn-hot act-btn"
                data-testid="buy-btn"
                :disabled="!canBuy"
                @click="openBuy"
              >
                <el-icon class="el-icon--left"><Lightning /></el-icon>{{ buyText }}
              </el-button>
            </div>
          </div>

          <router-link v-if="seller.id" class="ct-card seller" :to="`/users/${seller.id}`">
            <el-avatar :size="48" :src="seller.avatar">{{ (seller.nickname || '卖').slice(0, 1) }}</el-avatar>
            <div class="seller__body">
              <div class="seller__nm">
                {{ seller.nickname || '校园卖家' }}
                <span v-if="sellerProfile?.studentVerified" class="ct-badge ct-badge--mint"><el-icon><CircleCheck /></el-icon>已认证</span>
              </div>
              <div class="seller__sub">
                {{ seller.school || sellerProfile?.school || '校园卖家' }}
                <template v-if="reviewTotal"> · 收到 {{ reviewTotal }} 条评价</template>
              </div>
            </div>
            <CreditRing :value="seller.creditScore ?? sellerProfile?.creditScore ?? null" :size="60" :stroke="6" label="信誉分" />
          </router-link>

          <div class="safety">
            <el-icon><CircleCheck /></el-icon>
            <span>建议在校内公共场所当面验货交易，不要提前转账；遇到可疑情况可以<a href="#" @click.prevent="openReport">举报</a>。</span>
          </div>
        </div>
      </div>

      <!-- 描述 / 评价 -->
      <section class="desc">
        <div class="ct-card desc-card">
          <el-tabs v-model="descTab" class="ct-tabs">
            <el-tab-pane label="商品描述" name="desc">
              <p class="desc-text">{{ goods.description || '卖家暂未填写描述' }}</p>
              <div class="spec">
                <span v-if="conditionLabel" class="ct-chip is-sm">成色：{{ conditionLabel }}</span>
                <span v-if="tradeLabel" class="ct-chip is-sm">{{ tradeLabel }}</span>
                <span v-if="showOrigin" class="ct-chip is-sm">原价 ¥{{ formatPrice(goods.originalPrice) }}</span>
              </div>
            </el-tab-pane>
            <el-tab-pane :label="`卖家评价 ${reviewTotal}`" name="reviews">
              <ReviewList :items="reviews" empty-text="卖家还没有收到评价" />
            </el-tab-pane>
          </el-tabs>
        </div>
        <div class="ct-card desc-card">
          <h3 class="desc-card__title">最近评价</h3>
          <ReviewList :items="reviews.slice(0, 3)" empty-text="还没有评价，完成一笔交易后就能收到评价" />
        </div>
      </section>

      <section class="ct-section">
        <div class="ct-section-head">
          <div>
            <h2 class="ct-section-title">看了这件的同学还在看</h2>
            <p class="ct-section-sub">相似商品推荐 · 与当前商品成色和价格相近</p>
          </div>
        </div>
        <GoodsGrid :items="similarList" :loading="similarLoading" :empty-text="similarEmptyText" :skeleton-count="4" show-reason />
      </section>
    </template>

    <el-dialog v-model="buyVisible" title="确认下单" width="440px" :close-on-click-modal="orderState !== 'loading'">
      <div class="buy-goods">
        <img :src="images[0]" alt="" />
        <div>
          <p>{{ goods?.title }}</p>
          <strong class="num">¥{{ formatPrice(goods?.price) }}</strong>
        </div>
      </div>
      <el-input
        v-model="buyRemark"
        type="textarea"
        :rows="3"
        maxlength="200"
        show-word-limit
        placeholder="给卖家留言（选填），比如方便面交的时间地点"
      />
      <template #footer>
        <el-button :disabled="orderState === 'loading' || orderState === 'success'" @click="buyVisible = false">再想想</el-button>
        <!-- motion.md §6：idle → loading → success（打勾 + 撒花）| error（抖动） -->
        <el-button
          ref="orderBtnRef"
          class="ct-btn-hot order-btn"
          :class="`is-${orderState}`"
          :disabled="orderState === 'loading' || orderState === 'success'"
          data-testid="confirm-buy"
          @click="confirmBuy"
        >
          <template v-if="orderState === 'loading'"><span class="ct-spinner" />正在预订…</template>
          <template v-else-if="orderState === 'success'">
            <svg class="ct-check-draw" viewBox="0 0 24 24"><path d="M20 6 9 17l-5-5" /></svg>已预订
          </template>
          <template v-else-if="orderState === 'error'">{{ orderErrorText }}</template>
          <template v-else>确认下单</template>
        </el-button>
      </template>
    </el-dialog>

    <ReportDialog
      v-model="reportVisible"
      :target-type="ReportTargetType.GOODS"
      :target-id="goodsId"
      :target-name="goods?.title"
    />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCategoryList } from '@/api/category'
import { createChatSession } from '@/api/chat'
import { addFavorite, getGoodsDetail, removeFavorite, updateGoodsStatus } from '@/api/goods'
import { createOrder } from '@/api/order'
import { suggestPrice } from '@/api/price'
import { getSimilarGoods } from '@/api/recommend'
import { getReviewList } from '@/api/review'
import { getUserProfile } from '@/api/user'
import CreditRing from '@/components/CreditRing.vue'
import DemoBanner from '@/components/DemoBanner.vue'
import GoodsGrid from '@/components/GoodsGrid.vue'
import LikeButton from '@/components/LikeButton.vue'
import ReportDialog from '@/components/ReportDialog.vue'
import ReviewList from '@/components/ReviewList.vue'
import { confettiFrom, shake } from '@/composables/useMotion'
import {
  GOODS_CONDITION_LABELS,
  GOODS_STATUS_LABELS,
  GoodsStatus,
  ReportTargetType,
  Role,
  TRADE_METHOD_LABELS,
  enumLabel,
} from '@/constants/enums'
import { API_OFFLINE_TEXT, BizCode, bizErrorText, isApiOffline } from '@/constants/errorCodes'
import { useChatStore } from '@/stores/chat'
import { useUserStore } from '@/stores/user'
import { formatPrice, formatRelativeTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const chatStore = useChatStore()

const loading = ref(false)
const loadError = ref('')
const goods = ref(null)
const similarList = ref([])
const similarLoading = ref(false)
const similarOffline = ref(false)
const reportVisible = ref(false)
const similarEmptyText = computed(() => (similarOffline.value ? API_OFFLINE_TEXT : '暂时没有相似商品'))
const activeIndex = ref(0)
const viewerVisible = ref(false)
const chatLoading = ref(false)
const buyVisible = ref(false)
const buyRemark = ref('')
const orderState = ref('idle')
const orderErrorText = ref('')
const orderBtnRef = ref()
const descTab = ref('desc')
const reviews = ref([])
const reviewTotal = ref(0)
const sellerProfile = ref(null)
const suggestion = ref(null)
const categoryList = ref([])

const goodsId = computed(() => String(route.params.id))
const images = computed(() => {
  const item = goods.value
  if (!item) return []
  const list = item.images?.length ? item.images : item.coverUrl ? [item.coverUrl] : []
  return list.map(String)
})
const seller = computed(() => goods.value?.seller || {})
const isOwner = computed(() => {
  const myId = userStore.userInfo?.id
  const sellerId = seller.value.id
  return Boolean(myId && sellerId && String(myId) === String(sellerId))
})
const showOrigin = computed(
  () => goods.value?.originalPrice != null && Number(goods.value.originalPrice) > Number(goods.value.price),
)
const saveRate = computed(() => {
  if (!showOrigin.value) return 0
  return Math.round((1 - Number(goods.value.price) / Number(goods.value.originalPrice)) * 100)
})
const conditionLabel = computed(() => enumLabel(GOODS_CONDITION_LABELS, goods.value?.condition, ''))
const tradeLabel = computed(() => enumLabel(TRADE_METHOD_LABELS, goods.value?.tradeMethod, ''))
const sameSchool = computed(() => {
  const mine = userStore.userInfo?.school
  return Boolean(mine && seller.value.school && mine === seller.value.school && !isOwner.value)
})
// 设计答复 #1：在售 mint，已预订 sun，已售出 gray
const statusBadge = computed(() => {
  const status = goods.value?.status
  const text = status === GoodsStatus.ON_SHELF ? '在售' : enumLabel(GOODS_STATUS_LABELS, status)
  const cls = {
    [GoodsStatus.ON_SHELF]: 'ct-badge--mint',
    [GoodsStatus.RESERVED]: 'ct-badge--sun',
    [GoodsStatus.SOLD]: 'ct-badge--gray',
    [GoodsStatus.OFF_SHELF]: 'ct-badge--gray',
    [GoodsStatus.PENDING_AUDIT]: 'ct-badge--sun',
    [GoodsStatus.REJECTED]: 'ct-badge--danger',
  }[status] || 'ct-badge--gray'
  return { text, cls }
})
const saleMask = computed(() => {
  const status = goods.value?.status
  if (status === GoodsStatus.RESERVED) return '已预订'
  if (status === GoodsStatus.SOLD) return '已售出'
  return ''
})
const canBuy = computed(() => goods.value?.status === GoodsStatus.ON_SHELF)
const buyText = computed(() => {
  const status = goods.value?.status
  if (status === GoodsStatus.RESERVED) return '已被预订'
  if (status === GoodsStatus.SOLD) return '已售出'
  if (status === GoodsStatus.ON_SHELF) return '立即购买'
  return '暂不可购买'
})
const canFavorite = computed(() => {
  const status = goods.value?.status
  return status === GoodsStatus.ON_SHELF || status === GoodsStatus.RESERVED
})
const canEdit = computed(() => {
  const status = goods.value?.status
  return isOwner.value && status !== GoodsStatus.RESERVED && status !== GoodsStatus.SOLD
})
const canTakeDown = computed(() => isOwner.value && goods.value?.status === GoodsStatus.ON_SHELF)
const ownerAlert = computed(() => {
  const item = goods.value
  if (!item) return null
  const privileged = isOwner.value || userStore.userInfo?.role === Role.ADMIN
  if (!privileged) return null
  if (item.status === GoodsStatus.PENDING_AUDIT) return { type: 'warning', title: '商品正在审核中，通过后会出现在首页和搜索结果里' }
  if (item.status === GoodsStatus.REJECTED || item.auditReason) {
    return { type: 'error', title: `审核未通过：${item.auditReason || '请修改后重新提交'}` }
  }
  return null
})
const crumbs = computed(() => {
  const leaf = categoryList.value.find((item) => String(item.id) === String(goods.value?.categoryId))
  if (!leaf?.parentId) return { parent: null }
  const parent = categoryList.value.find((item) => String(item.id) === String(leaf.parentId))
  return { parent: parent || null }
})
const priceRange = computed(() => {
  const data = suggestion.value
  const price = Number(goods.value?.price)
  if (!data || data.minPrice == null || data.maxPrice == null || !Number.isFinite(price)) return null
  // 设计答复 #5：同类样本不足 5 件时隐藏区间条，只显示「同类样本较少」和参考价（发布页同一规则）
  if (Number(data.sampleCount || 0) < 5) return null
  const min = Number(data.minPrice)
  const max = Number(data.maxPrice)
  const lo = Math.max(0, Math.floor(Math.min(min, price) * 0.7))
  const hi = Math.ceil(Math.max(max, price) * 1.3) || 1
  const pct = (value) => Math.min(100, Math.max(0, ((value - lo) / (hi - lo || 1)) * 100))
  return {
    min,
    max,
    lo,
    hi,
    sampleCount: data.sampleCount,
    bandLeft: pct(min),
    bandRight: pct(max),
    markLeft: pct(price),
  }
})

function setActive(index) {
  activeIndex.value = index
}

function ensureLogin() {
  if (userStore.isLoggedIn) return true
  router.push({ path: '/login', query: { redirect: route.fullPath } })
  return false
}

async function loadDetail() {
  loading.value = true
  goods.value = null
  loadError.value = ''
  activeIndex.value = 0
  suggestion.value = null
  sellerProfile.value = null
  reviews.value = []
  reviewTotal.value = 0
  try {
    goods.value = await getGoodsDetail(goodsId.value)
  } catch (error) {
    if (error?.code === BizCode.GOODS_OFF_SHELF) loadError.value = '这件闲置已经下架了'
    else if (error?.code === BizCode.GOODS_NOT_FOUND) loadError.value = '这件闲置已经找不到了'
    else loadError.value = error?.msg || '这件闲置已经找不到了'
  } finally {
    loading.value = false
  }
  if (goods.value) loadExtras()
}

// 附加信息全部只读，失败时对应模块隐藏
async function loadExtras() {
  const item = goods.value
  const sellerId = item?.seller?.id
  const tasks = []
  if (sellerId) {
    tasks.push(
      getUserProfile(sellerId)
        .then((data) => {
          sellerProfile.value = data
        })
        .catch(() => {}),
      getReviewList({ userId: String(sellerId), pageNum: 1, pageSize: 10 })
        .then((data) => {
          reviews.value = data?.records || (Array.isArray(data) ? data : [])
          reviewTotal.value = data?.total ?? reviews.value.length
        })
        .catch(() => {}),
    )
  }
  if (userStore.isLoggedIn && item?.categoryId && item?.condition && item?.originalPrice != null) {
    tasks.push(
      suggestPrice({
        categoryId: String(item.categoryId),
        condition: item.condition,
        originalPrice: Number(item.originalPrice),
        title: item.title,
      })
        .then((data) => {
          suggestion.value = data
        })
        .catch(() => {}),
    )
  }
  if (!categoryList.value.length) {
    tasks.push(
      getCategoryList()
        .then((data) => {
          categoryList.value = Array.isArray(data) ? data : data?.records || []
        })
        .catch(() => {}),
    )
  }
  await Promise.all(tasks)
}

async function loadSimilar() {
  similarLoading.value = true
  try {
    const data = await getSimilarGoods(goodsId.value, { size: 4 })
    similarList.value = (Array.isArray(data) ? data : data?.records || []).slice(0, 4)
    similarOffline.value = false
  } catch (error) {
    similarList.value = []
    similarOffline.value = isApiOffline(error)
  } finally {
    similarLoading.value = false
  }
}

async function onToggleFavorite(next) {
  if (!ensureLogin()) throw new Error('need login')
  if (next) {
    await addFavorite(goodsId.value)
    ElMessage.success('已加入收藏，降价会第一时间提醒你')
  } else {
    await removeFavorite(goodsId.value)
    ElMessage.success('已取消收藏')
  }
}

async function contactSeller() {
  if (!ensureLogin()) return
  chatLoading.value = true
  try {
    const session = await createChatSession({
      goodsId: goodsId.value,
      toUserId: String(seller.value.id),
    })
    if (session?.id) {
      chatStore.upsertSession(session)
    }
    const sessionId = String(session?.id || '')
    router.push({ path: '/chat', query: sessionId ? { sessionId } : {} })
  } finally {
    chatLoading.value = false
  }
}

function openReport() {
  if (!userStore.token) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  reportVisible.value = true
}

function openBuy() {
  if (!ensureLogin()) return
  buyRemark.value = ''
  orderState.value = 'idle'
  buyVisible.value = true
}

async function confirmBuy() {
  if (orderState.value === 'loading' || orderState.value === 'success') return
  const btn = orderBtnRef.value?.$el
  orderState.value = 'loading'
  try {
    const order = await createOrder({
      goodsId: goodsId.value,
      remark: buyRemark.value.trim() || undefined,
    })
    orderState.value = 'success'
    confettiFrom(btn)
    ElMessage.success('预订成功，快和卖家约个面交时间吧')
    const orderId = order?.id
    setTimeout(() => {
      buyVisible.value = false
      router.push(orderId ? { path: '/orders', query: { id: String(orderId) } } : '/orders')
    }, 1200)
  } catch (error) {
    orderErrorText.value = error?.code === BizCode.GOODS_RESERVED ? '已被预订' : bizErrorText(error, '下单失败')
    orderState.value = 'error'
    shake(btn)
    setTimeout(() => {
      if (orderState.value === 'error') orderState.value = 'idle'
    }, 1600)
  }
}

function goEdit() {
  router.push({ path: '/publish', query: { id: goodsId.value } })
}

async function takeDown() {
  await updateGoodsStatus(goodsId.value, { status: GoodsStatus.OFF_SHELF })
  ElMessage.success('商品已下架')
  await loadDetail()
}

watch(
  goodsId,
  async () => {
    await Promise.all([loadDetail(), loadSimilar()])
  },
  { immediate: true },
)
</script>

<style scoped lang="scss">
.detail-page {
  padding-bottom: 24px;
}

.crumb {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin: 24px 0 20px;
  color: var(--ct-text-3);
  font-size: 13px;

  a {
    color: var(--ct-text-3);

    &:hover {
      color: var(--ct-primary);
    }
  }

  b {
    max-width: 360px;
    overflow: hidden;
    color: var(--ct-text-2);
    font-weight: 600;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.detail {
  display: grid;
  grid-template-columns: 7fr 5fr;
  gap: 32px;
  align-items: start;
}

.detail--sk {
  margin-top: 60px;
}

.thumb-sk {
  aspect-ratio: 1;
  border-radius: 16px;
}

.stage-sk {
  aspect-ratio: 1;
  border-radius: var(--ct-radius-xl);
}

.detail-missing {
  padding: 60px 0 20px;
}

/* ---------- 图集 ---------- */
.gallery {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  gap: 16px;
}

.thumbs {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.thumbs__item {
  position: relative;
  aspect-ratio: 1;
  padding: 0;
  overflow: hidden;
  border: 0;
  border-radius: 16px;
  background: var(--ct-surface-sunken);
  outline: 2px solid transparent;
  outline-offset: 2px;
  opacity: 0.65;
  cursor: pointer;
  transition:
    outline-color var(--ct-dur-base),
    transform var(--ct-dur-base) var(--ct-ease-out),
    opacity var(--ct-dur-base);

  img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  &:hover {
    opacity: 1;
    transform: scale(1.04);
  }

  &.is-on {
    outline-color: var(--ct-primary);
    opacity: 1;
  }
}

.stage {
  position: relative;
  aspect-ratio: 1 / 1;
  overflow: hidden;
  border-radius: var(--ct-radius-xl);
  background: var(--ct-surface-sunken);
  box-shadow: var(--ct-shadow-3);
  cursor: zoom-in;

  &.is-single {
    grid-column: 1 / -1;
  }

  > img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 0.6s var(--ct-ease-out);
  }

  &:hover > img {
    transform: scale(1.06);
  }
}

/* 详情图集切换：旧图淡出缩到 0.97（160ms），新图从 1.03 淡入（360ms） */
.stage-enter-active {
  transition:
    opacity var(--ct-dur-slow) var(--ct-ease-out),
    transform var(--ct-dur-slow) var(--ct-ease-out);
}

.stage-leave-active {
  transition:
    opacity var(--ct-dur-fast) var(--ct-ease-in),
    transform var(--ct-dur-fast) var(--ct-ease-in);
}

.stage-enter-from {
  opacity: 0;
  transform: scale(1.03);
}

.stage-leave-to {
  opacity: 0;
  transform: scale(0.97);
}

.stage__tl {
  position: absolute;
  top: 16px;
  left: 16px;
  z-index: 2;
  display: flex;
  gap: 8px;
}

.stage__count {
  position: absolute;
  right: 16px;
  bottom: 16px;
  z-index: 2;
}

.stage__mask {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: grid;
  place-items: center;
  background: rgba(20, 23, 40, 0.45);
  color: #fff;
  font-size: 32px;
  font-weight: 800;
  letter-spacing: 0.2em;
  backdrop-filter: grayscale(1);
}

/* ---------- 信息面板 ---------- */
.info {
  position: sticky;
  top: calc(var(--ct-header-h) + 20px);
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.info-card {
  padding: 28px;
}

.info-alert {
  margin-bottom: 16px;
  border-radius: var(--ct-radius-md);
}

.info-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.report-link {
  flex: none;
}

.title {
  margin: 14px 0 0;
  font-size: var(--ct-fs-h3);
  font-weight: 800;
  line-height: 1.35;
  letter-spacing: -0.01em;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.price-box {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  margin-top: 18px;
  padding: 18px 20px;
  border-radius: var(--ct-radius-lg);
  background: linear-gradient(135deg, var(--ct-accent-soft), transparent);
}

.price-box__now {
  color: var(--ct-price);
  font-size: var(--ct-fs-price-lg);
  font-weight: 800;
  line-height: 1;

  small {
    font-size: 20px;
  }
}

.price-box__origin {
  color: var(--ct-text-3);
  font-size: 13px;
  text-decoration: line-through;
}

.price-box__save {
  align-self: center;
  margin-left: auto;
}

.ai-price {
  margin-top: 14px;
  padding: 16px 18px;
  border: 1px dashed var(--ct-primary-soft-2);
  border-radius: var(--ct-radius-lg);
  background: var(--ct-surface-2);
}

.ai-price__hd {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 700;
}

.ai-price__ico {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  background: var(--ct-gradient-brand);
  color: #fff;
}

.ai-price__sub {
  margin-left: auto;
  color: var(--ct-text-3);
  font-weight: 500;
}

.ai-price__few {
  margin-left: auto;
}

.ai-price__ref {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 12px 0 0;
  color: var(--ct-text-2);
  font-size: 13px;

  b {
    color: var(--ct-price);
    font-size: 18px;
    font-weight: 800;
  }

  > span {
    margin-left: auto;
    color: var(--ct-text-3);
    font-size: 12px;
  }
}

.range {
  position: relative;
  height: 10px;
  margin: 40px 0 8px;
  border-radius: 99px;
  background: var(--ct-surface-sunken);
}

.range__band {
  position: absolute;
  top: 0;
  bottom: 0;
  border-radius: 99px;
  background: linear-gradient(90deg, var(--ct-mint), var(--ct-primary));
}

/* 定价区间标记：从最左侧弹到当前价格，900ms spring，延迟 300ms */
.range__mark {
  position: absolute;
  top: 50%;
  width: 20px;
  height: 20px;
  margin: -10px;
  border: 4px solid var(--ct-accent);
  border-radius: 50%;
  background: var(--ct-surface);
  box-shadow: var(--ct-shadow-2);
  animation: mark-in 0.9s var(--ct-ease-spring) 0.3s both;
}

@keyframes mark-in {
  from {
    left: 0;
    opacity: 0;
  }
}

.range__tip {
  position: absolute;
  bottom: 20px;
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--ct-text);
  color: var(--ct-surface);
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
  transform: translateX(-50%);
}

.range-scale {
  display: flex;
  justify-content: space-between;
  color: var(--ct-text-3);
  font-size: 12px;

  b {
    color: var(--ct-mint);
    font-weight: 700;
  }
}

.meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;

  > div {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 12px;
    border-radius: 14px;
    background: var(--ct-surface-2);
    font-size: 13px;
  }

  .el-icon {
    color: var(--ct-primary);
    font-size: 16px;
  }

  p {
    min-width: 0;
    margin: 0;
  }

  b {
    display: block;
    overflow: hidden;
    font-size: 13px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    color: var(--ct-text-3);
    font-size: 12px;
  }
}

.actions {
  display: grid;
  grid-template-columns: auto 1fr 1.3fr;
  gap: 12px;
  margin-top: 22px;

  .el-button {
    height: 52px;
    margin: 0;
    border-radius: var(--ct-radius-lg);
    font-size: 15px;
  }

  &--owner {
    grid-template-columns: 1fr 1fr;
  }
}

.actions:not(.actions--owner) > .act-btn:first-child {
  grid-column: 1 / 3;
}

/* ---------- 卖家卡 ---------- */
.seller {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px 24px 26px;
  color: inherit;
  transition: box-shadow var(--ct-dur-slow);

  &:hover {
    box-shadow: var(--ct-shadow-3);
  }
}

.seller__body {
  flex: 1;
  min-width: 0;
}

.seller__nm {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 800;
}

.seller__sub {
  margin-top: 2px;
  color: var(--ct-text-3);
  font-size: 12px;
}

.safety {
  display: flex;
  gap: 10px;
  padding: 14px 18px;
  border-radius: var(--ct-radius-lg);
  background: var(--ct-mint-soft);
  color: var(--ct-text-2);
  font-size: 13px;

  .el-icon {
    flex: none;
    margin-top: 3px;
    color: var(--ct-mint);
  }

  a {
    color: var(--ct-primary);
    font-weight: 600;
  }
}

/* ---------- 描述 / 评价 ---------- */
.desc {
  display: grid;
  grid-template-columns: 7fr 5fr;
  gap: 32px;
  margin-top: 48px;
  align-items: start;
}

.desc-card {
  padding: 8px 28px 24px;
}

.desc-card__title {
  margin: 20px 0 8px;
  font-size: 16px;
  font-weight: 800;
}

.desc-text {
  margin: 0;
  color: var(--ct-text-2);
  line-height: var(--ct-lh-loose);
  white-space: pre-wrap;
}

.spec {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;

  .ct-chip {
    cursor: default;

    &:hover {
      transform: none;
      border-color: var(--ct-border);
      color: var(--ct-text-2);
    }
  }
}

/* ---------- 下单弹窗 ---------- */
.buy-goods {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 10px;
  border-radius: 16px;
  background: var(--ct-surface-2);

  img {
    width: 56px;
    height: 56px;
    border-radius: 12px;
    object-fit: cover;
  }

  p {
    margin: 0 0 4px;
    font-weight: 600;
  }

  strong {
    color: var(--ct-price);
    font-size: 18px;
    font-weight: 800;
  }
}

.order-btn {
  min-width: 132px;
  gap: 6px;

  :deep(> span) {
    gap: 6px;
  }

  &.is-success {
    background-image: linear-gradient(135deg, #16b07b, #14c9a2) !important;
    box-shadow: 0 10px 30px -8px rgba(22, 176, 123, 0.55) !important;
    opacity: 1 !important;
  }

  &.is-loading {
    opacity: 1 !important;
  }
}

@media (max-width: 1024px) {
  .detail,
  .desc {
    grid-template-columns: 1fr;
  }

  .gallery {
    grid-template-columns: 1fr;
  }

  .thumbs {
    flex-direction: row;
    order: 2;

    .thumbs__item {
      width: 72px;
    }
  }

  .info {
    position: static;
  }
}

@media (prefers-reduced-motion: reduce) {
  .range__mark {
    animation: none;
  }
}
</style>
