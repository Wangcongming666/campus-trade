<template>
  <div class="orders-page" data-testid="orders-page">
    <div class="orders-head">
      <h1>我的订单</h1>
      <el-radio-group v-model="role" @change="onRoleChange">
        <el-radio-button v-for="item in roleOptions" :key="item.value" :value="item.value">
          {{ item.label }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <el-tabs v-model="status" class="orders-tabs" @tab-change="reload">
      <el-tab-pane label="全部" name="ALL" />
      <el-tab-pane v-for="item in statusTabs" :key="item.value" :label="item.label" :name="item.value" />
    </el-tabs>

    <div v-loading="loading" class="order-list">
      <article v-for="item in records" :key="item.id" class="order-card">
        <div class="order-card__top">
          <span>订单号 {{ item.id }}</span>
          <span>{{ formatDateTime(item.createTime) }}</span>
          <el-tag size="small" :type="enumTagType(ORDER_STATUS_TAG_TYPES, item.status)">
            {{ enumLabel(ORDER_STATUS_LABELS, item.status) }}
          </el-tag>
        </div>
        <div class="order-card__body">
          <img :src="item.goodsCover" :alt="item.goodsTitle" @click="goGoods(item.goodsId)" />
          <div class="order-card__info">
            <h3 @click="goGoods(item.goodsId)">{{ item.goodsTitle }}</h3>
            <p>
              {{ role === OrderRole.BUYER ? '卖家' : '买家' }}：
              {{ peerOf(item)?.nickname || '同学' }}
            </p>
            <p v-if="item.remark">备注：{{ item.remark }}</p>
            <p v-if="item.cancelReason && item.status === OrderStatus.CANCELLED">原因：{{ item.cancelReason }}</p>
          </div>
          <div class="order-card__price">
            <strong>¥{{ formatPrice(item.price) }}</strong>
          </div>
        </div>
        <div class="order-card__actions">
          <el-button @click="openDetail(item)">详情</el-button>
          <el-button
            v-for="action in actionsOf(item)"
            :key="action"
            :type="actionButtonType(action)"
            @click="onAction(item, action)"
          >
            {{ enumLabel(ORDER_ACTION_LABELS, action) }}
          </el-button>
          <el-button
            v-if="canReview(item)"
            type="primary"
            plain
            @click="openReview(item)"
          >
            评价
          </el-button>
        </div>
      </article>
      <el-empty v-if="!loading && !records.length" :description="offline ? API_OFFLINE_TEXT : '暂无订单'" />
    </div>

    <div v-if="total > pageSize" class="orders-pager">
      <el-pagination
        background
        layout="prev, pager, next, total"
        :total="total"
        :page-size="pageSize"
        :current-page="pageNum"
        @current-change="onPageChange"
      />
    </div>

    <el-drawer v-model="detailVisible" title="订单详情" size="420px" data-testid="order-detail-drawer">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="订单号">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            {{ enumLabel(ORDER_STATUS_LABELS, detail.status) }}
          </el-descriptions-item>
          <el-descriptions-item label="商品">
            <router-link :to="`/goods/${detail.goodsId}`">{{ detail.goodsTitle }}</router-link>
          </el-descriptions-item>
          <el-descriptions-item label="金额">¥{{ formatPrice(detail.price) }}</el-descriptions-item>
          <el-descriptions-item label="买家">{{ detail.buyer?.nickname }}</el-descriptions-item>
          <el-descriptions-item label="卖家">{{ detail.seller?.nickname }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.remark" label="备注">{{ detail.remark }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.cancelReason" label="取消原因">{{ detail.cancelReason }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.payTime" label="付款时间">{{ detail.payTime }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.completeTime" label="完成时间">{{ detail.completeTime }}</el-descriptions-item>
          <el-descriptions-item label="下单时间">{{ detail.createTime }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>

    <el-dialog v-model="reasonVisible" :title="reasonTitle" width="420px">
      <el-input
        v-model="reasonText"
        type="textarea"
        :rows="4"
        maxlength="200"
        show-word-limit
        placeholder="请填写原因（选填）"
      />
      <template #footer>
        <el-button @click="reasonVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitReason">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reviewVisible" title="评价订单" width="420px">
      <el-form label-position="top">
        <el-form-item label="评分">
          <el-rate v-model="reviewForm.score" />
        </el-form-item>
        <el-form-item label="评价内容">
          <el-input
            v-model="reviewForm.content"
            type="textarea"
            :rows="4"
            maxlength="200"
            show-word-limit
            placeholder="说说这次交易的体验"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitReview">提交评价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrderDetail, getOrderList, updateOrderStatus } from '@/api/order'
import { createReview } from '@/api/review'
import { usePagination } from '@/composables/usePagination'
import {
  ORDER_ACTION_LABELS,
  ORDER_STATUS_LABELS,
  ORDER_STATUS_TAG_TYPES,
  OrderAction,
  OrderRole,
  OrderStatus,
  enumLabel,
  enumOptions,
  enumTagType,
  getAvailableOrderActions,
} from '@/constants/enums'
import { API_OFFLINE_TEXT, FORBIDDEN_TEXT, bizErrorText, isApiOffline, isForbidden } from '@/constants/errorCodes'
import { useUserStore } from '@/stores/user'
import { formatDateTime, formatPrice } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const roleOptions = enumOptions( {
  [OrderRole.BUYER]: '我买到的',
  [OrderRole.SELLER]: '我卖出的',
})
const statusTabs = enumOptions(ORDER_STATUS_LABELS)
const role = ref(route.query.role === OrderRole.SELLER ? OrderRole.SELLER : OrderRole.BUYER)
const status = ref(route.query.status ? String(route.query.status) : 'ALL')

const { records, loading, total, pageNum, pageSize, fetchData, reset } =
  usePagination(getOrderList, { pageSize: 10 })
const offline = ref(false)

function listParams() {
  const params = { role: role.value }
  if (status.value && status.value !== 'ALL') params.status = status.value
  return params
}

async function reload() {
  reset()
  syncQuery()
  try {
    await fetchData(listParams())
    offline.value = false
  } catch (error) {
    offline.value = isApiOffline(error)
  }
}

function syncQuery() {
  const query = { role: role.value }
  if (status.value && status.value !== 'ALL') query.status = status.value
  router.replace({ path: '/orders', query })
}

function onRoleChange() {
  status.value = 'ALL'
  reload()
}

async function onPageChange(page) {
  pageNum.value = page
  try {
    await fetchData(listParams())
    offline.value = false
  } catch (error) {
    offline.value = isApiOffline(error)
  }
}

function roleOf(order) {
  const myId = String(userStore.userInfo?.id || '')
  if (order?.buyer?.id && String(order.buyer.id) === myId) return OrderRole.BUYER
  if (order?.seller?.id && String(order.seller.id) === myId) return OrderRole.SELLER
  return role.value
}

function peerOf(order) {
  return roleOf(order) === OrderRole.BUYER ? order.seller : order.buyer
}

function actionsOf(order) {
  return getAvailableOrderActions(order.status, roleOf(order))
}

function actionButtonType(action) {
  if (action === OrderAction.PAY || action === OrderAction.CONFIRM) return 'primary'
  if (action === OrderAction.REFUND) return 'warning'
  return 'default'
}

function canReview(order) {
  return roleOf(order) === OrderRole.BUYER
    && order.status === OrderStatus.COMPLETED
    && !order.reviewed
}

function goGoods(id) {
  if (id) router.push(`/goods/${id}`)
}

const detailVisible = ref(false)
const detail = ref(null)
async function openDetail(order) {
  try {
    detail.value = await getOrderDetail(order.id, { silent: true })
    detailVisible.value = true
  } catch (error) {
    if (isForbidden(error)) {
      ElMessage.warning(bizErrorText(error, FORBIDDEN_TEXT))
      return
    }
    ElMessage.error(bizErrorText(error, '无法查看该订单'))
  }
}

const reasonVisible = ref(false)
const reasonText = ref('')
const reasonTitle = ref('请填写原因')
const pendingOrder = ref(null)
const pendingAction = ref('')
const acting = ref(false)

async function onAction(order, action) {
  if (action === OrderAction.PAY) {
    await ElMessageBox.confirm('确认向卖家付款吗？演示环境不会产生真实扣款。', '确认付款', {
      type: 'warning',
      confirmButtonText: '确认付款',
    })
    await submitAction(order, action)
    return
  }
  if (action === OrderAction.CONFIRM) {
    await ElMessageBox.confirm('确认已收到商品并完成交易吗？', '确认收货', {
      type: 'warning',
      confirmButtonText: '确认收货',
    })
    await submitAction(order, action)
    return
  }
  pendingOrder.value = order
  pendingAction.value = action
  reasonText.value = ''
  reasonTitle.value = action === OrderAction.REFUND ? '请填写退款原因' : '请填写取消原因'
  reasonVisible.value = true
}

async function submitAction(order, action, reason) {
  acting.value = true
  try {
    const payload = { action }
    if (reason) payload.reason = reason
    await updateOrderStatus(order.id, payload)
    ElMessage.success('订单已更新')
    reasonVisible.value = false
    await reload()
    if (detailVisible.value && detail.value?.id === order.id) {
      detail.value = await getOrderDetail(order.id)
    }
  } finally {
    acting.value = false
  }
}

async function submitReason() {
  if (!pendingOrder.value) return
  await submitAction(pendingOrder.value, pendingAction.value, reasonText.value.trim())
}

const reviewVisible = ref(false)
const reviewForm = reactive({ orderId: '', score: 5, content: '' })
function openReview(order) {
  reviewForm.orderId = order.id
  reviewForm.score = 5
  reviewForm.content = ''
  reviewVisible.value = true
}

async function submitReview() {
  if (!reviewForm.score) {
    ElMessage.warning('请先打分')
    return
  }
  acting.value = true
  try {
    await createReview({
      orderId: reviewForm.orderId,
      score: reviewForm.score,
      content: reviewForm.content.trim(),
    })
    ElMessage.success('评价已提交')
    reviewVisible.value = false
    await reload()
  } finally {
    acting.value = false
  }
}

watch(
  () => route.query,
  (query) => {
    const nextRole = query.role === OrderRole.SELLER ? OrderRole.SELLER : OrderRole.BUYER
    const nextStatus = query.status ? String(query.status) : 'ALL'
    if (nextRole !== role.value || nextStatus !== status.value) {
      role.value = nextRole
      status.value = nextStatus
      reload()
    }
  },
)

onMounted(reload)
</script>

<style scoped lang="scss">
.orders-page {
  padding: 20px 0 48px;
}

.orders-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 8px;

  h1 {
    margin: 0;
    font-size: 22px;
  }
}

.order-list {
  min-height: 240px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.order-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--ct-radius-lg);
  padding: 14px 16px;
}

.order-card__top {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--color-text-secondary);
  font-size: 13px;
  margin-bottom: 12px;
}

.order-card__body {
  display: flex;
  gap: 14px;

  img {
    width: 96px;
    height: 96px;
    object-fit: cover;
    border-radius: 8px;
    cursor: pointer;
    flex-shrink: 0;
    background: var(--color-primary-bg);
  }
}

.order-card__info {
  flex: 1;
  min-width: 0;

  h3 {
    margin: 0 0 8px;
    font-size: 16px;
    cursor: pointer;
  }

  p {
    margin: 0 0 4px;
    color: var(--color-text-secondary);
    font-size: 13px;
  }
}

.order-card__price {
  font-size: 18px;
  color: var(--ct-price);
  font-weight: 800;
  white-space: nowrap;
}

.order-card__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.orders-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
