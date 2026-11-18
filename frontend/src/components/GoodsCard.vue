<template>
  <router-link class="goods-card" :class="{ 'goods-card--no-reason': showReason && !reason }" :to="`/goods/${goods.id}`" data-testid="goods-card">
    <div class="goods-card__cover">
      <img v-if="cover" :src="cover" :alt="goods.title" loading="lazy" />
      <div class="goods-card__badges">
        <span v-if="statusBadge" class="ct-badge" :class="statusBadge.cls">{{ statusBadge.text }}</span>
        <span v-if="conditionLabel" class="ct-badge ct-badge--glass">{{ conditionLabel }}</span>
        <span v-if="isDemo" class="ct-badge ct-badge--sun">演示数据</span>
      </div>
      <LikeButton
        v-if="favoritable"
        v-model="favorited"
        class="goods-card__like"
        :on-toggle="onToggleFavorite"
      />
      <span v-if="goods.categoryName" class="goods-card__loc ct-badge ct-badge--glass">
        <el-icon><CollectionTag /></el-icon>{{ goods.categoryName }}
      </span>
      <span class="goods-card__quick">
        <el-icon><View /></el-icon>快速查看
      </span>
    </div>
    <div class="goods-card__body">
      <h3 class="goods-card__title">{{ goods.title }}</h3>
      <RecommendReason v-if="reason" :reason="reason" :type="goods.reasonType" />
      <div class="goods-card__price">
        <span class="goods-card__now num"><small>¥</small>{{ formatPrice(goods.price) }}</span>
        <span v-if="showOrigin" class="goods-card__origin num">¥{{ formatPrice(goods.originalPrice) }}</span>
      </div>
      <div class="goods-card__meta">
        <el-avatar :size="22" :src="seller.avatar">
          {{ (seller.nickname || '同').slice(0, 1) }}
        </el-avatar>
        <span class="goods-card__name">{{ seller.nickname || '校园卖家' }}</span>
        <span class="goods-card__time">{{ formatRelativeTime(goods.createTime) }}</span>
      </div>
    </div>
  </router-link>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { addFavorite, removeFavorite } from '@/api/goods'
import LikeButton from '@/components/LikeButton.vue'
import RecommendReason from '@/components/RecommendReason.vue'
import { GOODS_CONDITION_LABELS, GoodsStatus, enumLabel } from '@/constants/enums'
import { useUserStore } from '@/stores/user'
import { USE_MOCK, formatPrice, formatRelativeTime } from '@/utils/format'

const props = defineProps({
  goods: { type: Object, required: true },
  /** 推荐理由：只显示后端返回的 reason，不在前端编造 */
  showReason: { type: Boolean, default: false },
  showFavorite: { type: Boolean, default: true },
})

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const seller = computed(() => {
  const goods = props.goods || {}
  return (
    goods.seller || {
      id: goods.sellerId,
      nickname: goods.sellerNickname || goods.nickname,
      avatar: goods.sellerAvatar || goods.avatar,
    }
  )
})

const cover = computed(() => props.goods.coverUrl || props.goods.images?.[0] || '')
const conditionLabel = computed(() => enumLabel(GOODS_CONDITION_LABELS, props.goods.condition, ''))
// 设计答复 #1：已预订 sun，已售出 gray
const statusBadge = computed(() => {
  if (props.goods.status === GoodsStatus.RESERVED) return { text: '已预订', cls: 'ct-badge--sun' }
  if (props.goods.status === GoodsStatus.SOLD) return { text: '已售出', cls: 'ct-badge--gray' }
  return null
})
const showOrigin = computed(
  () => props.goods.originalPrice != null && Number(props.goods.originalPrice) > Number(props.goods.price),
)
// 只有推荐接口（/recommend/home、/recommend/similar）返回 reason / reasonType；缺失时不渲染理由条
const reason = computed(() => (props.showReason && typeof props.goods.reason === 'string' ? props.goods.reason.trim() : ''))
const isMine = computed(
  () => Boolean(userStore.userInfo?.id && seller.value.id && String(userStore.userInfo.id) === String(seller.value.id)),
)
const favoritable = computed(
  () =>
    props.showFavorite &&
    !isMine.value &&
    (props.goods.status == null || props.goods.status === GoodsStatus.ON_SHELF || props.goods.status === GoodsStatus.RESERVED),
)
const isDemo = USE_MOCK

const favorited = ref(Boolean(props.goods.favorited))
watch(
  () => props.goods.favorited,
  (value) => {
    favorited.value = Boolean(value)
  },
)

async function onToggleFavorite(next) {
  if (!userStore.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    throw new Error('need login')
  }
  if (next) {
    await addFavorite(props.goods.id)
    ElMessage.success('已加入收藏，降价会第一时间提醒你')
  } else {
    await removeFavorite(props.goods.id)
  }
  // eslint-disable-next-line vue/no-mutating-props
  props.goods.favorited = next
}
</script>

<style scoped lang="scss">
/* 推荐位里缺 reason 的卡片：不随同行卡片拉伸，高度收紧，不留空白 */
.goods-card--no-reason {
  align-self: start;
}

.goods-card {
  position: relative;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--ct-border);
  border-radius: var(--ct-radius-lg);
  background: var(--ct-surface);
  box-shadow: var(--ct-shadow-1);
  color: inherit;
  transition:
    transform var(--ct-dur-slow) var(--ct-ease-out),
    box-shadow var(--ct-dur-slow) var(--ct-ease-out),
    border-color var(--ct-dur-slow);

  &:hover {
    transform: translateY(-6px);
    border-color: transparent;
    box-shadow: var(--ct-shadow-3);
  }
}

.goods-card__cover {
  position: relative;
  aspect-ratio: 1 / 1;
  overflow: hidden;
  background: linear-gradient(135deg, var(--ct-primary-soft), var(--ct-primary-soft-2));

  img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform var(--ct-dur-slower) var(--ct-ease-out);
  }
}

.goods-card:hover .goods-card__cover img {
  transform: scale(1.06);
}

.goods-card__badges {
  position: absolute;
  left: 12px;
  top: 12px;
  z-index: 2;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.goods-card__like {
  position: absolute;
  right: 12px;
  top: 12px;
  z-index: 3;
}

.goods-card__loc {
  position: absolute;
  left: 12px;
  bottom: 12px;
  z-index: 2;
  transition: opacity var(--ct-dur-base);
}

.goods-card__quick {
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 12px;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 38px;
  border-radius: 12px;
  background: var(--ct-glass);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  color: var(--ct-text);
  font-size: 13px;
  font-weight: 700;
  opacity: 0;
  transform: translateY(12px);
  transition: all var(--ct-dur-base) var(--ct-ease-out);
}

.goods-card:hover {
  .goods-card__quick {
    opacity: 1;
    transform: none;
  }

  .goods-card__loc {
    opacity: 0;
  }
}

.goods-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px 16px;
}

.goods-card__title {
  height: 2.8em;
  margin: 0;
  overflow: hidden;
  color: var(--ct-text);
  font-size: 15px;
  font-weight: 600;
  line-height: var(--ct-lh-snug);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.goods-card__price {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: auto;
}

.goods-card__now {
  color: var(--ct-price);
  font-size: var(--ct-fs-price);
  font-weight: 800;
  line-height: 1;

  small {
    margin-right: 1px;
    font-size: 0.6em;
  }
}

.goods-card__origin {
  color: var(--ct-text-3);
  font-size: 12px;
  text-decoration: line-through;
}

.goods-card__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: var(--ct-text-3);
  font-size: 12px;
}

.goods-card__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--ct-text-2);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.goods-card__time {
  flex-shrink: 0;
}

@media (hover: none) {
  .goods-card:hover {
    transform: none;
  }
}

@media (max-width: 768px) {
  .goods-card__body {
    padding: 12px;
  }

  .goods-card__now {
    font-size: 18px;
  }
}
</style>
