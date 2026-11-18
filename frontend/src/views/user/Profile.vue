<template>
  <div class="profile-page" data-testid="profile-page">
    <!-- 封面横幅 + 玻璃资料卡 -->
    <section class="hero">
      <div class="hero__cover" aria-hidden="true">
        <span class="blob b1" />
        <span class="blob b2" />
        <span class="blob b3" />
      </div>
      <div class="hero__card">
        <el-avatar :size="104" :src="userStore.userInfo?.avatar" class="hero__avatar">{{ avatarText }}</el-avatar>
        <div class="hero__info">
          <div class="hero__name">
            <h1>{{ displayName }}</h1>
            <span class="ct-badge" :class="authBadgeCls">
              <el-icon v-if="authStatus === StudentAuthStatus.APPROVED"><CircleCheck /></el-icon>
              {{ enumLabel(STUDENT_AUTH_STATUS_LABELS, authStatus, '未认证') }}
            </span>
          </div>
          <p class="hero__sub">
            {{ userStore.userInfo?.school || '未填写学校' }}
            <template v-if="joinDays"> · 加入 <span class="num">{{ joinDays }}</span> 天</template>
          </p>
          <div class="hero__stats">
            <button type="button" @click="onTabChange('goods'); goodsFilter = GoodsStatus.ON_SHELF">
              <b><NumberRoll :value="goodsLoading ? null : goodsCount(GoodsStatus.ON_SHELF)" /></b><span>在售</span>
            </button>
            <button type="button" @click="onTabChange('goods'); goodsFilter = GoodsStatus.SOLD">
              <b><NumberRoll :value="goodsLoading ? null : goodsCount(GoodsStatus.SOLD)" /></b><span>已售</span>
            </button>
            <button type="button" @click="onTabChange('favorites')">
              <b><NumberRoll :value="favLoading ? null : favTotal" /></b><span>收藏</span>
            </button>
            <button type="button" @click="onTabChange('reviews')">
              <b><NumberRoll :value="reviewLoading ? null : reviewTotal" /></b><span>收到评价</span>
            </button>
          </div>
        </div>
        <CreditRing :value="userStore.userInfo?.creditScore ?? null" :size="96" :stroke="8" label="信誉分" />
      </div>
    </section>

    <div v-if="authStatus !== StudentAuthStatus.APPROVED" class="auth-banner" :class="{ 'is-pending': authStatus === StudentAuthStatus.PENDING }">
      <el-icon><Postcard /></el-icon>
      <span v-if="authStatus === StudentAuthStatus.PENDING">学生认证审核中，通过后即可发布和下单</span>
      <span v-else>完成学生认证后才能发布和下单</span>
      <el-button v-if="authStatus !== StudentAuthStatus.PENDING" type="primary" round size="small" @click="onTabChange('settings')">去认证</el-button>
    </div>

    <div class="body">
      <!-- 侧导航 -->
      <nav class="side-nav" aria-label="个人中心">
        <button
          v-for="item in NAV"
          :key="item.name"
          type="button"
          class="side-nav__item"
          :class="{ 'is-on': activeTab === item.name }"
          @click="onTabChange(item.name)"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
          <em v-if="item.name === 'goods'" class="num">{{ myGoods.length }}</em>
          <em v-else-if="item.name === 'favorites'" class="num">{{ favTotal }}</em>
          <em v-else-if="item.name === 'orders' && orderCounts[OrderStatus.PENDING]" class="is-hot num">{{ orderCounts[OrderStatus.PENDING] }}</em>
        </button>
        <hr />
        <button type="button" class="side-nav__item" :class="{ 'is-on': activeTab === 'settings' }" @click="onTabChange('settings')">
          <el-icon><Setting /></el-icon><span>学生认证 / 账号设置</span>
        </button>
      </nav>

      <main class="content">
        <Transition name="tab" mode="out-in">
          <!-- 我的发布 -->
          <div v-if="activeTab === 'goods'" key="goods" class="tab-pane">
            <div class="order-cards">
              <router-link
                v-for="card in ORDER_CARDS"
                :key="card.status"
                class="order-card ct-card"
                :class="`is-${card.tone}`"
                :to="{ path: '/orders', query: { role: OrderRole.BUYER, status: card.status } }"
              >
                <span class="order-card__ico"><el-icon><component :is="card.icon" /></el-icon></span>
                <div>
                  <b class="num">{{ orderCounts[card.status] ?? '—' }}</b>
                  <span>{{ ORDER_STATUS_LABELS[card.status] }}</span>
                </div>
              </router-link>
            </div>

            <div class="ct-card panel">
              <div class="panel__head">
                <h2>我的商品</h2>
                <Segmented v-model="goodsFilter" :options="goodsFilterOptions" />
                <el-button type="primary" round class="panel__new" @click="$router.push('/publish')">
                  <el-icon class="el-icon--left"><Plus /></el-icon>发布新闲置
                </el-button>
              </div>

              <div v-if="goodsLoading && !myGoods.length" class="my-grid">
                <div v-for="n in 4" :key="n" class="ct-sk" style="aspect-ratio: 4 / 4.6; border-radius: 20px" />
              </div>
              <TransitionGroup v-else name="grid" tag="div" class="my-grid">
                <article v-for="item in filteredGoods" :key="item.id" class="my-card" :class="{ 'is-sold': item.status === GoodsStatus.SOLD }">
                  <div class="my-card__cover" @click="goGoods(item.id)">
                    <img :src="item.coverUrl" :alt="item.title" loading="lazy" />
                    <span class="ct-badge my-card__status" :class="statusBadgeCls(item.status)">{{ item.status === GoodsStatus.ON_SHELF ? '在售' : enumLabel(GOODS_STATUS_LABELS, item.status) }}</span>
                    <div v-if="item.status === GoodsStatus.SOLD" class="my-card__mask">已售出</div>
                  </div>
                  <div class="my-card__body">
                    <h3 @click="goGoods(item.id)">{{ item.title }}</h3>
                    <div class="my-card__row">
                      <b class="num">¥{{ formatPrice(item.price) }}</b>
                      <span class="num"><el-icon><View /></el-icon>{{ item.viewCount ?? 0 }}</span>
                    </div>
                    <p v-if="item.auditReason" class="audit-reason">审核意见：{{ item.auditReason }}</p>
                  </div>
                  <div class="my-card__ops">
                    <el-button v-if="canEdit(item)" link type="primary" @click="editGoods(item)">编辑</el-button>
                    <el-button v-if="item.status === GoodsStatus.ON_SHELF" link @click="changeGoodsStatus(item, GoodsStatus.OFF_SHELF)">下架</el-button>
                    <el-button v-if="item.status === GoodsStatus.OFF_SHELF" link type="primary" @click="changeGoodsStatus(item, GoodsStatus.ON_SHELF)">上架</el-button>
                    <el-button v-if="canDelete(item)" link type="danger" @click="onDeleteGoods(item)">删除</el-button>
                  </div>
                </article>
              </TransitionGroup>
              <div v-if="!goodsLoading && !filteredGoods.length" class="tab-empty">
                <el-icon><Box /></el-icon>
                <p>{{ myGoods.length ? '这个状态下还没有商品' : '还没有发布闲置' }}</p>
                <el-button v-if="!myGoods.length" type="primary" round @click="$router.push('/publish')">发布闲置</el-button>
              </div>
            </div>

            <div class="bottom-row">
              <div class="ct-card panel">
                <div class="panel__head">
                  <h2>兴趣偏好 <small class="num">{{ userStore.interests.length }}/5</small></h2>
                  <el-button link type="primary" @click="userStore.openInterestPicker()">修改</el-button>
                </div>
                <div class="interest-chips">
                  <span v-for="item in userStore.interests" :key="item.id" class="ct-chip is-sm is-soft is-active">{{ item.name }}</span>
                  <span v-if="!userStore.interests.length" class="muted">还没有选择兴趣分类，选好后首页推荐会更懂你</span>
                </div>
              </div>
              <div class="ct-card panel">
                <div class="panel__head">
                  <h2>收到的评价</h2>
                  <el-button v-if="reviewTotal > 3" link type="primary" @click="onTabChange('reviews')">全部 {{ reviewTotal }} 条</el-button>
                </div>
                <ReviewList :items="reviews.slice(0, 3)" empty-text="还没有评价，完成一笔交易后就能收到评价" />
              </div>
            </div>
          </div>

          <!-- 我的收藏 -->
          <div v-else-if="activeTab === 'favorites'" key="favorites" class="tab-pane">
            <div class="ct-card panel">
              <div class="panel__head"><h2>我的收藏 <small class="num">{{ favTotal }}</small></h2></div>
              <GoodsGrid :items="favorites" :loading="favLoading" :skeleton-count="3">
                <template #empty>
                  <div class="tab-empty">
                    <el-icon><Star /></el-icon>
                    <p>还没有收藏</p>
                    <el-button type="primary" round @click="$router.push('/')">去首页逛逛</el-button>
                  </div>
                </template>
              </GoodsGrid>
            </div>
          </div>

          <!-- 收到的评价 -->
          <div v-else-if="activeTab === 'reviews'" key="reviews" class="tab-pane">
            <div class="ct-card panel">
              <div class="panel__head"><h2>收到的评价 <small class="num">{{ reviewTotal }}</small></h2></div>
              <ReviewList :items="reviews" empty-text="还没有评价，完成一笔交易后就能收到评价" />
            </div>
          </div>

          <!-- 兴趣偏好 -->
          <div v-else-if="activeTab === 'interests'" key="interests" class="tab-pane">
            <div class="ct-card panel">
              <div class="panel__head">
                <h2>兴趣偏好 <small class="num">{{ userStore.interests.length }}/5</small></h2>
                <el-button type="primary" round @click="userStore.openInterestPicker()">修改兴趣</el-button>
              </div>
              <p class="muted">首页「猜你喜欢」会优先推荐这些分类的闲置。</p>
              <div class="interest-chips">
                <span v-for="item in userStore.interests" :key="item.id" class="ct-chip is-soft is-active">{{ item.name }}</span>
                <span v-if="!userStore.interests.length" class="muted">还没有选择兴趣分类</span>
              </div>
            </div>
          </div>

          <!-- 账号设置 -->
          <div v-else key="settings" class="tab-pane">
            <!-- 账号设置：按原型面板风格重排（表单字段、接口和校验逻辑不变） -->
            <div class="settings">
              <section class="ct-card panel set-sec">
                <header class="set-sec__head">
                  <span class="set-sec__ico"><el-icon><User /></el-icon></span>
                  <div>
                    <h2>个人资料</h2>
                    <p>头像、昵称和手机号会展示在你的商品和聊天里</p>
                  </div>
                </header>
                <div class="set-profile">
                  <div class="set-avatar">
                    <el-avatar :size="88" :src="profileForm.avatar">{{ avatarText }}</el-avatar>
                    <el-button round size="small" class="ct-btn-ghost" :loading="uploading" @click="avatarInput?.click()">
                      <el-icon class="el-icon--left"><Camera /></el-icon>更换头像
                    </el-button>
                    <input ref="avatarInput" class="hidden-file" type="file" accept="image/*" @change="onAvatarChange" />
                  </div>
                  <el-form label-position="top" class="set-form">
                    <div class="set-grid">
                      <el-form-item label="昵称">
                        <el-input v-model="profileForm.nickname" maxlength="20" show-word-limit />
                      </el-form-item>
                      <el-form-item label="手机号">
                        <el-input v-model="profileForm.phone" maxlength="11" />
                      </el-form-item>
                      <el-form-item label="学校" class="set-grid__full">
                        <el-input v-model="profileForm.school" maxlength="50" disabled />
                        <div class="form-tip">学生认证通过后自动更新</div>
                      </el-form-item>
                    </div>
                    <div class="set-actions">
                      <el-button type="primary" round :loading="savingProfile" @click="saveProfile">保存资料</el-button>
                    </div>
                  </el-form>
                </div>
              </section>

              <section class="ct-card panel set-sec">
                <header class="set-sec__head">
                  <span class="set-sec__ico is-sun"><el-icon><Sunny /></el-icon></span>
                  <div>
                    <h2>外观</h2>
                    <p>默认跟随系统，手动选择后保存在本机浏览器</p>
                  </div>
                </header>
                <div class="theme-pick" data-testid="theme-mode">
                  <Segmented :model-value="themeMode" :options="THEME_OPTIONS" @change="onThemeMode" />
                </div>
                <div class="theme-cards" aria-hidden="true">
                  <button
                    v-for="item in THEME_OPTIONS"
                    :key="item.value"
                    type="button"
                    tabindex="-1"
                    class="theme-card"
                    :class="[`is-${item.value}`, { 'is-on': themeMode === item.value }]"
                    @click="onThemeMode(item.value, $event)"
                  >
                    <span class="theme-card__mock"><i /><i /><i /></span>
                    <span>{{ item.label }}</span>
                  </button>
                </div>
              </section>

              <section class="ct-card panel set-sec">
                <header class="set-sec__head">
                  <span class="set-sec__ico is-mint"><el-icon><Postcard /></el-icon></span>
                  <div>
                    <h2>学生认证</h2>
                    <p>认证通过后才能发布闲置和下单，信息仅用于校验身份</p>
                  </div>
                  <span class="ct-badge" :class="authBadgeCls">{{ enumLabel(STUDENT_AUTH_STATUS_LABELS, authStatus, '未认证') }}</span>
                </header>
                <ol class="auth-steps">
                  <li :class="{ 'is-done': authStatus !== StudentAuthStatus.NONE }"><span>1</span>提交资料</li>
                  <li :class="{ 'is-done': authStatus === StudentAuthStatus.APPROVED || authStatus === StudentAuthStatus.REJECTED, 'is-on': authStatus === StudentAuthStatus.PENDING }"><span>2</span>管理员审核</li>
                  <li :class="{ 'is-done': authStatus === StudentAuthStatus.APPROVED, 'is-err': authStatus === StudentAuthStatus.REJECTED }"><span>3</span>{{ authStatus === StudentAuthStatus.REJECTED ? '未通过' : '认证完成' }}</li>
                </ol>
                <el-alert v-if="authStatus === StudentAuthStatus.APPROVED" title="已通过学生认证" type="success" :closable="false" show-icon />
                <el-alert v-else-if="authStatus === StudentAuthStatus.PENDING" title="认证审核中，请耐心等待" type="warning" :closable="false" show-icon />
                <el-alert v-else-if="authStatus === StudentAuthStatus.REJECTED" title="认证未通过，请核对信息后重新提交" type="error" :closable="false" show-icon class="mb" />
                <el-form
                  v-if="authStatus === StudentAuthStatus.NONE || authStatus === StudentAuthStatus.REJECTED"
                  label-position="top"
                  class="set-form"
                >
                  <div class="set-grid set-grid--3">
                    <el-form-item label="真实姓名">
                      <el-input v-model="verifyForm.realName" maxlength="20" />
                    </el-form-item>
                    <el-form-item label="学号">
                      <el-input v-model="verifyForm.studentNo" maxlength="32" />
                    </el-form-item>
                    <el-form-item label="学校">
                      <el-input v-model="verifyForm.school" maxlength="50" />
                    </el-form-item>
                  </div>
                  <el-form-item label="学生证照片">
                    <button type="button" class="card-upload" :class="{ 'has-img': verifyForm.cardImageUrl }" @click="cardInput?.click()">
                      <img v-if="verifyForm.cardImageUrl" :src="verifyForm.cardImageUrl" alt="学生证照片" />
                      <span v-else class="card-upload__empty">
                        <el-icon><UploadFilled /></el-icon>
                        {{ uploadingCard ? '上传中…' : '上传学生证照片' }}
                      </span>
                    </button>
                    <input ref="cardInput" class="hidden-file" type="file" accept="image/*" @change="onCardChange" />
                  </el-form-item>
                  <div class="set-actions">
                    <el-button type="primary" round :loading="savingVerify" @click="submitVerify">提交认证</el-button>
                  </div>
                </el-form>
              </section>

              <section class="ct-card panel set-sec">
                <header class="set-sec__head">
                  <span class="set-sec__ico is-hot"><el-icon><Lock /></el-icon></span>
                  <div>
                    <h2>账号安全</h2>
                    <p>修改密码后需要重新登录</p>
                  </div>
                </header>
                <el-form label-position="top" class="set-form">
                  <div class="set-grid set-grid--3">
                    <el-form-item label="原密码">
                      <el-input v-model="passwordForm.oldPassword" type="password" show-password maxlength="32" />
                    </el-form-item>
                    <el-form-item label="新密码">
                      <el-input v-model="passwordForm.newPassword" type="password" show-password maxlength="32" placeholder="至少 6 位" />
                    </el-form-item>
                    <el-form-item label="确认新密码">
                      <el-input v-model="passwordForm.confirmPassword" type="password" show-password maxlength="32" />
                    </el-form-item>
                  </div>
                  <div class="set-actions">
                    <el-button type="primary" round :loading="savingPassword" @click="changePassword">修改密码并重新登录</el-button>
                  </div>
                </el-form>
              </section>
            </div>
          </div>
        </Transition>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { uploadFile } from '@/api/file'
import { deleteGoods, updateGoodsStatus } from '@/api/goods'
import { getOrderList } from '@/api/order'
import { getReviewList } from '@/api/review'
import CreditRing from '@/components/CreditRing.vue'
import NumberRoll from '@/components/NumberRoll.vue'
import ReviewList from '@/components/ReviewList.vue'
import Segmented from '@/components/Segmented.vue'
import { switchThemeWithTransition, themeMode } from '@/composables/useTheme'
import {
  getMyFavorites,
  getMyGoods,
  updatePassword,
  updateUserProfile,
  verifyStudent,
} from '@/api/user'
import GoodsGrid from '@/components/GoodsGrid.vue'
import {
  GOODS_STATUS_LABELS,
  GOODS_STATUS_TAG_TYPES,
  GoodsStatus,
  ORDER_STATUS_LABELS,
  OrderRole,
  OrderStatus,
  STUDENT_AUTH_STATUS_LABELS,
  STUDENT_AUTH_STATUS_TAG_TYPES,
  StudentAuthStatus,
  enumLabel,
  enumTagType,
} from '@/constants/enums'
import { useUserStore } from '@/stores/user'
import { formatPrice } from '@/utils/format'

const THEME_OPTIONS = [
  { label: '浅色', value: 'light' },
  { label: '深色', value: 'dark' },
  { label: '跟随系统', value: 'auto' },
]

function onThemeMode(mode, event) {
  // 分段控件只回传值，用当前获得焦点的按钮作为圆形扩散的起点
  const origin = event?.currentTarget ? event : { currentTarget: document.activeElement }
  switchThemeWithTransition(mode, origin)
}

const TAB_NAMES = ['goods', 'favorites', 'reviews', 'interests', 'settings']

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const displayName = computed(
  () => userStore.userInfo?.nickname || userStore.userInfo?.account || userStore.userInfo?.username || '同学',
)
const avatarText = computed(() => displayName.value.slice(0, 1))
const authStatus = computed(() => userStore.userInfo?.studentAuthStatus || StudentAuthStatus.NONE)
const authBadgeCls = computed(
  () =>
    ({
      [StudentAuthStatus.APPROVED]: 'ct-badge--mint',
      [StudentAuthStatus.PENDING]: 'ct-badge--sun',
      [StudentAuthStatus.REJECTED]: 'ct-badge--danger',
    })[authStatus.value] || 'ct-badge--gray',
)
const activeTab = ref(TAB_NAMES.includes(route.query.tab) ? String(route.query.tab) : 'goods')

const goodsFilter = ref(String(route.query.status || 'all'))
const reviews = ref([])
const reviewTotal = ref(0)
const reviewLoading = ref(false)
const favTotal = ref(0)
const orderCounts = ref({})
const NAV = [
  { name: 'goods', label: '我的发布', icon: 'Goods' },
  { name: 'orders', label: '我的订单', icon: 'Tickets' },
  { name: 'favorites', label: '我的收藏', icon: 'Star' },
  { name: 'reviews', label: '收到的评价', icon: 'ChatLineSquare' },
  { name: 'interests', label: '兴趣偏好', icon: 'MagicStick' },
]
const ORDER_CARDS = [
  { status: OrderStatus.PENDING, icon: 'Wallet', tone: 'sun' },
  { status: OrderStatus.PAID, icon: 'Van', tone: 'primary' },
  { status: OrderStatus.COMPLETED, icon: 'CircleCheck', tone: 'mint' },
  { status: OrderStatus.CANCELLED, icon: 'CircleClose', tone: 'gray' },
]
const GOODS_FILTERS = [
  { label: '全部', value: 'all' },
  { label: '在售', value: GoodsStatus.ON_SHELF },
  { label: '审核中', value: GoodsStatus.PENDING_AUDIT },
  { label: '已预订', value: GoodsStatus.RESERVED },
  { label: '已售出', value: GoodsStatus.SOLD },
  { label: '已下架', value: GoodsStatus.OFF_SHELF },
  { label: '未通过', value: GoodsStatus.REJECTED },
]
const myGoods = ref([])
const goodsLoading = ref(false)
const favorites = ref([])
const favLoading = ref(false)

const profileForm = reactive({
  nickname: '',
  phone: '',
  school: '',
  avatar: '',
})
const verifyForm = reactive({
  realName: '',
  studentNo: '',
  school: '',
  cardImageUrl: '',
})
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const avatarInput = ref()
const cardInput = ref()
const uploading = ref(false)
const uploadingCard = ref(false)
const savingProfile = ref(false)
const savingVerify = ref(false)
const savingPassword = ref(false)

function fillProfileForm() {
  const info = userStore.userInfo || {}
  profileForm.nickname = info.nickname || ''
  profileForm.phone = info.phone || ''
  profileForm.school = info.school || ''
  profileForm.avatar = info.avatar || ''
  if (!verifyForm.school) verifyForm.school = info.school || ''
}

function onTabChange(name) {
  if (name === 'orders') {
    router.push('/orders')
    return
  }
  activeTab.value = name
  router.replace({ path: '/user', query: { tab: name } })
}

const goodsCount = (status) => myGoods.value.filter((item) => item.status === status).length
const filteredGoods = computed(() =>
  goodsFilter.value === 'all' ? myGoods.value : myGoods.value.filter((item) => item.status === goodsFilter.value),
)
const goodsFilterOptions = computed(() =>
  GOODS_FILTERS.map((item) => ({
    ...item,
    label: item.value === 'all' ? `${item.label} ${myGoods.value.length}` : `${item.label} ${goodsCount(item.value)}`,
  })).filter((item) => item.value === 'all' || goodsCount(item.value) || item.value === goodsFilter.value),
)
const joinDays = computed(() => {
  const created = userStore.userInfo?.createTime
  if (!created) return null
  const ms = Date.now() - new Date(String(created).replace(' ', 'T') + '+08:00').getTime()
  return Math.max(1, Math.ceil(ms / 86400000))
})
const statusBadgeCls = (status) =>
  ({
    [GoodsStatus.ON_SHELF]: 'ct-badge--mint',
    [GoodsStatus.PENDING_AUDIT]: 'ct-badge--primary',
    [GoodsStatus.RESERVED]: 'ct-badge--sun',
    [GoodsStatus.SOLD]: 'ct-badge--gray',
    [GoodsStatus.OFF_SHELF]: 'ct-badge--gray',
    [GoodsStatus.REJECTED]: 'ct-badge--danger',
  })[status] || 'ct-badge--gray'

async function loadReviews() {
  const myId = userStore.userInfo?.id
  if (!myId) return
  reviewLoading.value = true
  try {
    const data = await getReviewList({ userId: String(myId), pageNum: 1, pageSize: 20 })
    reviews.value = data?.records || (Array.isArray(data) ? data : [])
    reviewTotal.value = data?.total ?? reviews.value.length
  } catch {
    reviews.value = []
  } finally {
    reviewLoading.value = false
  }
}

async function loadOrderCounts() {
  const entries = await Promise.all(
    ORDER_CARDS.map(async (card) => {
      try {
        const data = await getOrderList({ role: OrderRole.BUYER, status: card.status, pageNum: 1, pageSize: 1 })
        return [card.status, data?.total ?? (Array.isArray(data) ? data.length : 0)]
      } catch {
        return [card.status, null]
      }
    }),
  )
  orderCounts.value = Object.fromEntries(entries)
}

function canEdit(item) {
  return [GoodsStatus.ON_SHELF, GoodsStatus.OFF_SHELF, GoodsStatus.REJECTED, GoodsStatus.PENDING_AUDIT].includes(item.status)
}

function canDelete(item) {
  return item.status !== GoodsStatus.RESERVED && item.status !== GoodsStatus.SOLD
}

function goGoods(id) {
  router.push(`/goods/${id}`)
}

function editGoods(item) {
  router.push({ path: '/publish', query: { id: item.id } })
}

async function loadGoods() {
  goodsLoading.value = true
  try {
    const data = await getMyGoods({ pageNum: 1, pageSize: 50 })
    myGoods.value = data?.records || (Array.isArray(data) ? data : [])
  } finally {
    goodsLoading.value = false
  }
}

async function loadFavorites() {
  favLoading.value = true
  try {
    const data = await getMyFavorites({ pageNum: 1, pageSize: 50 })
    favorites.value = data?.records || (Array.isArray(data) ? data : [])
    favTotal.value = data?.total ?? favorites.value.length
  } finally {
    favLoading.value = false
  }
}

async function changeGoodsStatus(item, status) {
  const text = status === GoodsStatus.OFF_SHELF ? '确认下架该商品？' : '确认重新上架该商品？'
  await ElMessageBox.confirm(text, '提示', { type: 'warning' })
  await updateGoodsStatus(item.id, { status })
  ElMessage.success('商品状态已更新')
  await loadGoods()
}

async function onDeleteGoods(item) {
  await ElMessageBox.confirm('删除后不可恢复，确认删除该商品？', '删除商品', {
    type: 'warning',
    confirmButtonText: '确认删除',
    confirmButtonClass: 'el-button--danger',
  })
  await deleteGoods(item.id)
  ElMessage.success('商品已删除')
  await loadGoods()
}

async function onAvatarChange(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  uploading.value = true
  try {
    const uploaded = await uploadFile(file)
    if (!uploaded?.url) throw new Error('上传失败')
    profileForm.avatar = uploaded.url
    ElMessage.success('头像已上传，请点击保存资料')
  } finally {
    uploading.value = false
  }
}

async function saveProfile() {
  if (!profileForm.nickname.trim()) {
    ElMessage.warning('请填写昵称')
    return
  }
  savingProfile.value = true
  try {
    await updateUserProfile({
      nickname: profileForm.nickname.trim(),
      phone: profileForm.phone.trim(),
      avatar: profileForm.avatar,
    })
    await userStore.fetchUserInfo()
    fillProfileForm()
    ElMessage.success('资料已保存')
  } finally {
    savingProfile.value = false
  }
}

async function onCardChange(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  uploadingCard.value = true
  try {
    const uploaded = await uploadFile(file)
    if (!uploaded?.url) throw new Error('上传失败')
    verifyForm.cardImageUrl = uploaded.url
  } finally {
    uploadingCard.value = false
  }
}

async function submitVerify() {
  if (!verifyForm.realName.trim() || !verifyForm.studentNo.trim() || !verifyForm.school.trim() || !verifyForm.cardImageUrl) {
    ElMessage.warning('请完整填写学生认证信息并上传学生证照片')
    return
  }
  savingVerify.value = true
  try {
    await verifyStudent({
      studentNo: verifyForm.studentNo.trim(),
      realName: verifyForm.realName.trim(),
      school: verifyForm.school.trim(),
      cardImageUrl: verifyForm.cardImageUrl,
    })
    await userStore.fetchUserInfo()
    ElMessage.success('已提交认证，请等待审核')
  } finally {
    savingVerify.value = false
  }
}

async function changePassword() {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    ElMessage.warning('请填写原密码和新密码')
    return
  }
  if (passwordForm.newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  savingPassword.value = true
  try {
    await updatePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    ElMessage.success('密码已修改，请重新登录')
    await userStore.logout()
    await router.replace('/login')
  } finally {
    savingPassword.value = false
  }
}

watch(
  () => route.query.tab,
  (tab) => {
    if (TAB_NAMES.includes(tab) && tab !== activeTab.value) {
      activeTab.value = String(tab)
    }
  },
)

watch(
  () => route.query.status,
  (status) => {
    if (status) goodsFilter.value = String(status)
  },
)

onMounted(async () => {
  fillProfileForm()
  if (!userStore.interests.length) {
    try {
      await userStore.loadInterests()
    } catch {
      // 兴趣加载失败时仍展示空状态
    }
  }
  await Promise.all([loadGoods(), loadFavorites(), loadReviews(), loadOrderCounts()])
})
</script>
<style scoped lang="scss">
.profile-page {
  padding: 20px 0 40px;
}

/* ---------- 横幅 + 资料卡 ---------- */
.hero {
  position: relative;
}

.hero__cover {
  position: relative;
  height: 220px;
  overflow: hidden;
  border-radius: var(--ct-radius-xl);
  background: var(--ct-gradient-brand);
}

.blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(40px);
  opacity: 0.55;
  animation: ct-float-y 9s ease-in-out infinite;

  &.b1 {
    top: -60px;
    left: 8%;
    width: 260px;
    height: 260px;
    background: #14c9a2;
  }

  &.b2 {
    top: 30px;
    right: 12%;
    width: 220px;
    height: 220px;
    background: #ff8a6b;
    animation-delay: -3s;
  }

  &.b3 {
    bottom: -90px;
    left: 45%;
    width: 240px;
    height: 240px;
    background: #ffc53d;
    animation-delay: -6s;
  }
}

.hero__card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 24px;
  margin: -84px 32px 0;
  padding: 24px 32px;
  border: 1px solid var(--ct-glass-border);
  border-radius: var(--ct-radius-xl);
  background: var(--ct-glass);
  box-shadow: var(--ct-shadow-3);
  backdrop-filter: blur(24px) saturate(1.6);
  -webkit-backdrop-filter: blur(24px) saturate(1.6);
}

.hero__avatar {
  flex: none;
  border: 4px solid var(--ct-surface);
  box-shadow: var(--ct-shadow-2);
  font-size: 36px;
}

.hero__info {
  flex: 1;
  min-width: 0;
}

.hero__name {
  display: flex;
  align-items: center;
  gap: 10px;

  h1 {
    margin: 0;
    font-size: 26px;
    font-weight: 800;
    letter-spacing: -0.01em;
  }
}

.hero__sub {
  margin: 4px 0 0;
  color: var(--ct-text-3);
  font-size: 13px;
}

.hero__stats {
  display: flex;
  gap: 4px;
  margin-top: 14px;

  button {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    padding: 6px 18px 6px 0;
    border: 0;
    background: none;
    color: inherit;
    font: inherit;
    cursor: pointer;

    &:not(:last-child) {
      margin-right: 14px;
      border-right: 1px solid var(--ct-border);
    }

    &:hover b {
      color: var(--ct-primary);
    }
  }

  b {
    font-size: 22px;
    font-weight: 800;
    line-height: 1.2;
    transition: color var(--ct-dur-base);
  }

  button > span {
    color: var(--ct-text-3);
    font-size: 12px;
  }
}

.auth-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 16px 32px 0;
  padding: 12px 18px;
  border-radius: var(--ct-radius-lg);
  background: var(--ct-primary-soft);
  color: var(--ct-text-2);
  font-size: 13px;

  span {
    flex: 1;
  }

  .el-icon {
    color: var(--ct-primary);
    font-size: 18px;
  }

  &.is-pending {
    background: var(--ct-sun-soft);

    .el-icon {
      color: var(--ct-sun-ink);
    }
  }
}

/* ---------- 主体 ---------- */
.body {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 28px;
  align-items: start;
  margin-top: 28px;
}

.side-nav {
  position: sticky;
  top: calc(var(--ct-header-h) + 20px);
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  border: 1px solid var(--ct-border);
  border-radius: var(--ct-radius-xl);
  background: var(--ct-surface);
  box-shadow: var(--ct-shadow-1);

  hr {
    width: 100%;
    margin: 8px 0;
    border: 0;
    border-top: 1px solid var(--ct-border);
  }
}

.side-nav__item {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 44px;
  padding: 0 14px;
  border: 0;
  border-radius: 14px;
  background: transparent;
  color: var(--ct-text-2);
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
  transition:
    background var(--ct-dur-base),
    color var(--ct-dur-base);

  span {
    flex: 1;
  }

  em {
    min-width: 22px;
    padding: 0 6px;
    border-radius: 11px;
    background: var(--ct-surface-sunken);
    color: var(--ct-text-3);
    font-size: 12px;
    font-style: normal;
    line-height: 22px;
    text-align: center;

    &.is-hot {
      background: var(--ct-accent);
      color: #fff;
    }
  }

  &:hover {
    background: var(--ct-surface-sunken);
    color: var(--ct-text);
  }

  &.is-on {
    background: var(--ct-primary-soft);
    color: var(--ct-primary);

    em:not(.is-hot) {
      background: var(--ct-surface);
      color: var(--ct-primary);
    }
  }
}

.content {
  min-width: 0;
}

.tab-pane {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.tab-enter-active,
.tab-leave-active {
  transition:
    opacity var(--ct-dur-base) var(--ct-ease-out),
    transform var(--ct-dur-base) var(--ct-ease-out);
}

.tab-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.tab-leave-to {
  opacity: 0;
}

.order-cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.order-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  color: inherit;
  transition:
    transform var(--ct-dur-base) var(--ct-ease-out),
    box-shadow var(--ct-dur-base);

  &:hover {
    transform: translateY(-3px);
    box-shadow: var(--ct-shadow-3);
  }

  b {
    display: block;
    font-size: 22px;
    font-weight: 800;
    line-height: 1.2;
  }

  span {
    color: var(--ct-text-3);
    font-size: 12px;
  }
}

.order-card__ico {
  display: grid;
  flex: none;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  font-size: 20px;

  .is-sun & {
    background: var(--ct-sun-soft);
    color: var(--ct-sun-ink);
  }

  .is-primary & {
    background: var(--ct-primary-soft);
    color: var(--ct-primary);
  }

  .is-mint & {
    background: var(--ct-mint-soft);
    color: var(--ct-mint);
  }

  .is-gray & {
    background: var(--ct-surface-sunken);
    color: var(--ct-text-3);
  }
}

.panel {
  padding: 22px 24px;
}

.panel__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;

  h2 {
    margin: 0;
    font-size: 18px;
    font-weight: 800;

    small {
      margin-left: 4px;
      color: var(--ct-text-3);
      font-size: 13px;
      font-weight: 600;
    }
  }

  > .el-button:last-child {
    margin-left: auto;
  }
}

.my-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.my-card {
  position: relative;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--ct-border);
  border-radius: 20px;
  background: var(--ct-surface);
  transition:
    transform var(--ct-dur-slow) var(--ct-ease-out),
    box-shadow var(--ct-dur-slow);

  &:hover {
    transform: translateY(-4px);
    box-shadow: var(--ct-shadow-3);
  }
}

.my-card__cover {
  position: relative;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  background: var(--ct-surface-sunken);
  cursor: pointer;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 0.6s var(--ct-ease-out);
  }

  .my-card:hover & img {
    transform: scale(1.06);
  }

  .is-sold & img {
    filter: grayscale(1);
  }
}

.my-card__status {
  position: absolute;
  top: 10px;
  left: 10px;
}

.my-card__mask {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(20, 23, 40, 0.42);
  color: #fff;
  font-size: 18px;
  font-weight: 800;
  letter-spacing: 0.2em;
}

.my-card__body {
  flex: 1;
  padding: 12px 14px 4px;

  h3 {
    margin: 0;
    overflow: hidden;
    font-size: 14px;
    font-weight: 700;
    text-overflow: ellipsis;
    white-space: nowrap;
    cursor: pointer;
  }
}

.my-card__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;

  b {
    color: var(--ct-price);
    font-size: 16px;
    font-weight: 800;
  }

  span {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    color: var(--ct-text-3);
    font-size: 12px;
  }
}

.audit-reason {
  margin: 6px 0 0;
  color: var(--el-color-danger);
  font-size: 12px;
}

.my-card__ops {
  display: flex;
  gap: 4px;
  padding: 6px 14px 12px;

  .el-button + .el-button {
    margin-left: 10px;
  }
}

.grid-enter-active,
.grid-leave-active {
  transition:
    opacity var(--ct-dur-base),
    transform var(--ct-dur-base) var(--ct-ease-out);
}

.grid-enter-from,
.grid-leave-to {
  opacity: 0;
  transform: scale(0.94);
}

.grid-leave-active {
  position: absolute;
}

.grid-move {
  transition: transform var(--ct-dur-slow) var(--ct-ease-out);
}

.tab-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 40px 0 24px;
  color: var(--ct-text-3);

  > .el-icon {
    display: grid;
    width: 64px;
    height: 64px;
    border-radius: 22px;
    background: var(--ct-primary-soft);
    color: var(--ct-primary);
    font-size: 28px;
    place-items: center;
  }

  p {
    margin: 0;
  }
}

.bottom-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  align-items: start;

  .panel__head {
    margin-bottom: 10px;
  }
}

.interest-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;

  .ct-chip {
    cursor: default;
  }
}

.muted {
  margin: 0 0 12px;
  color: var(--ct-text-3);
  font-size: 13px;
}

/* 账号设置（阶段 4 按原型面板风格重排） */
.settings {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.set-sec__head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 20px;

  > div {
    flex: 1;
  }

  h2 {
    margin: 0;
    font-size: 17px;
    font-weight: 800;
  }

  p {
    margin: 2px 0 0;
    color: var(--ct-text-3);
    font-size: 12px;
  }
}

.set-sec__ico {
  display: grid;
  flex: none;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 13px;
  background: var(--ct-primary-soft);
  color: var(--ct-primary);
  font-size: 18px;

  &.is-sun {
    background: var(--ct-sun-soft);
    color: var(--ct-sun-ink);
  }

  &.is-mint {
    background: var(--ct-mint-soft);
    color: var(--ct-mint-ink);
  }

  &.is-hot {
    background: var(--ct-accent-soft);
    color: var(--ct-accent);
  }
}

.set-profile {
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr);
  gap: 28px;
  align-items: start;
}

.set-avatar {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 20px 12px;
  border-radius: var(--ct-radius-lg);
  background: var(--ct-surface-2);

  .el-avatar {
    border: 3px solid var(--ct-surface);
    box-shadow: var(--ct-shadow-2);
    font-size: 30px;
  }
}

.set-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 20px;

  &--3 {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  &__full {
    grid-column: 1 / -1;
  }
}

.set-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 4px;
  border-top: 1px dashed var(--ct-border);
  margin-top: 4px;

  .el-button {
    margin-top: 14px;
  }
}

.theme-pick {
  margin-bottom: 16px;
}

.theme-cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.theme-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 10px 10px 12px;
  border: 1.5px solid var(--ct-border);
  border-radius: var(--ct-radius-lg);
  background: var(--ct-surface);
  color: var(--ct-text-2);
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition:
    border-color var(--ct-dur-base),
    box-shadow var(--ct-dur-base),
    transform var(--ct-dur-base) var(--ct-ease-out);

  &:hover {
    transform: translateY(-2px);
  }

  &.is-on {
    border-color: var(--ct-primary);
    box-shadow: var(--ct-ring);
    color: var(--ct-primary);
  }
}

/* 主题预览小样：固定色值只用于示意浅/深两种外观 */
.theme-card__mock {
  display: grid;
  grid-template-columns: 1fr 2fr;
  grid-template-rows: 10px 1fr;
  gap: 6px;
  height: 72px;
  padding: 8px;
  border-radius: 12px;
  background: #f5f6fb;

  i {
    border-radius: 5px;
    background: #ffffff;
    box-shadow: 0 1px 2px rgba(20, 23, 40, 0.08);
  }

  i:first-child {
    grid-column: 1 / -1;
    background: linear-gradient(90deg, #5b5cf0, #c264f0);
  }

  .is-dark & {
    background: #0b0d18;

    i:not(:first-child) {
      background: #191c32;
    }
  }

  .is-auto & {
    background: linear-gradient(135deg, #f5f6fb 50%, #0b0d18 50%);

    i:nth-child(2) {
      background: #ffffff;
    }

    i:nth-child(3) {
      background: #191c32;
    }
  }
}

.auth-steps {
  display: flex;
  gap: 8px;
  margin: 0 0 16px;
  padding: 0;
  list-style: none;

  li {
    display: flex;
    flex: 1;
    align-items: center;
    gap: 8px;
    padding: 10px 12px;
    border-radius: 14px;
    background: var(--ct-surface-2);
    color: var(--ct-text-3);
    font-size: 13px;
    font-weight: 600;

    span {
      display: grid;
      place-items: center;
      width: 22px;
      height: 22px;
      border-radius: 50%;
      background: var(--ct-surface-sunken);
      font-size: 12px;
    }

    &.is-on {
      color: var(--ct-sun-ink);

      span {
        background: var(--ct-sun-soft);
      }
    }

    &.is-done {
      color: var(--ct-mint-ink);

      span {
        background: var(--ct-mint);
        color: #fff;
      }
    }

    &.is-err {
      color: var(--el-color-danger);

      span {
        background: var(--el-color-danger);
        color: #fff;
      }
    }
  }
}

.card-upload {
  display: grid;
  width: 240px;
  height: 150px;
  overflow: hidden;
  padding: 0;
  place-items: center;
  border: 2px dashed var(--ct-border-strong);
  border-radius: var(--ct-radius-lg);
  background: var(--ct-surface-2);
  color: var(--ct-text-3);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
  transition: border-color var(--ct-dur-base);

  &:hover {
    border-color: var(--ct-primary);
    color: var(--ct-primary);
  }

  &.has-img {
    border-style: solid;
  }

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.card-upload__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;

  .el-icon {
    font-size: 26px;
  }
}

.hidden-file {
  display: none;
}

.form-tip {
  margin-top: 4px;
  color: var(--ct-text-3);
  font-size: 12px;
}

.mb {
  margin-bottom: 12px;
}

@media (max-width: 1024px) {
  .body {
    grid-template-columns: 1fr;
  }

  .side-nav {
    position: static;
    flex-direction: row;
    overflow-x: auto;

    hr {
      display: none;
    }
  }

  .side-nav__item {
    flex: none;
  }

  .my-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .hero__card {
    flex-wrap: wrap;
    margin: -60px 0 0;
  }

  .hero__avatar {
    width: 72px !important;
    height: 72px !important;
  }

  .order-cards,
  .my-grid,
  .bottom-row,

  .bottom-row,
  .set-profile,
  .set-grid,
  .set-grid--3,
  .theme-cards {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .blob {
    animation: none;
  }

  .my-card,
  .order-card,
  .tab-enter-active,
  .tab-leave-active,
  .grid-move {
    transition: none;
  }
}
</style>
