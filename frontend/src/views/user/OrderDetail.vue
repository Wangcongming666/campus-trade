<template>
  <div class="order-detail-page" data-testid="order-detail" v-loading="loading">
    <el-empty v-if="!loading && !order" :description="loadError || '订单不存在'" />
    <article v-else-if="order" class="order-card">
      <div class="order-card__top">
        <span>订单号 {{ order.id }}</span>
        <el-tag size="small" :type="enumTagType(ORDER_STATUS_TAG_TYPES, order.status)">
          {{ enumLabel(ORDER_STATUS_LABELS, order.status) }}
        </el-tag>
      </div>
      <div class="order-card__body">
        <img v-if="order.goodsCover" :src="order.goodsCover" :alt="order.goodsTitle" />
        <div>
          <h2>
            <router-link :to="`/goods/${order.goodsId}`">{{ order.goodsTitle }}</router-link>
          </h2>
          <p>买家：{{ order.buyer?.nickname || '—' }}</p>
          <p>卖家：{{ order.seller?.nickname || '—' }}</p>
          <p v-if="order.remark">备注：{{ order.remark }}</p>
          <p v-if="order.cancelReason">取消原因：{{ order.cancelReason }}</p>
          <p v-if="order.payTime">付款时间：{{ order.payTime }}</p>
          <p v-if="order.completeTime">完成时间：{{ order.completeTime }}</p>
          <p>下单时间：{{ order.createTime }}</p>
        </div>
        <strong class="price">¥{{ formatPrice(order.price) }}</strong>
      </div>
      <div class="order-card__actions">
        <el-button @click="goBack">返回</el-button>
        <el-button type="primary" @click="router.push('/orders')">我的订单</el-button>
      </div>
    </article>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getOrderDetail } from '@/api/order'
import {
  ORDER_STATUS_LABELS,
  ORDER_STATUS_TAG_TYPES,
  enumLabel,
  enumTagType,
} from '@/constants/enums'
import {
  BizCode,
  API_OFFLINE_TEXT,
  FORBIDDEN_TEXT,
  bizErrorText,
  isApiOffline,
  isForbidden,
} from '@/constants/errorCodes'
import { formatPrice } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const order = ref(null)
const loadError = ref('')

function goBack() {
  // history.length 在首个打开的标签里通常 > 1（含 about:blank），不能用来判断是否有站内上一页
  if (window.history.state?.back) router.back()
  else router.replace('/orders')
}

async function load() {
  const id = String(route.params.id || '')
  if (!id) {
    order.value = null
    loadError.value = '订单不存在'
    return
  }
  loading.value = true
  try {
    order.value = await getOrderDetail(id, { silent: true })
    loadError.value = ''
  } catch (error) {
    order.value = null
    if (isForbidden(error)) {
      ElMessage.warning(bizErrorText(error, FORBIDDEN_TEXT))
      goBack()
      return
    }
    if (isApiOffline(error)) loadError.value = API_OFFLINE_TEXT
    else if (Number(error.code) === BizCode.ORDER_NOT_FOUND) loadError.value = '订单不存在'
    else loadError.value = bizErrorText(error, '订单不存在')
  } finally {
    loading.value = false
  }
}

watch(() => route.params.id, load, { immediate: true })
</script>

<style scoped lang="scss">
.order-detail-page {
  padding: 24px 0 48px;
}

.order-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--ct-radius-lg);
  padding: 20px 24px;
}

.order-card__top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.order-card__body {
  display: flex;
  gap: 16px;
  align-items: flex-start;

  img {
    width: 96px;
    height: 96px;
    object-fit: cover;
    border-radius: 8px;
    flex-shrink: 0;
  }

  h2 {
    margin: 0 0 8px;
    font-size: 18px;
  }

  p {
    margin: 0 0 4px;
    color: var(--color-text-secondary);
    font-size: 13px;
  }
}

.price {
  margin-left: auto;
  font-size: 20px;
  color: var(--ct-price);
  font-weight: 800;
}

.order-card__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 20px;
}
</style>
