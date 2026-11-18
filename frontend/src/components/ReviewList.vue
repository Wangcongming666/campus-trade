<template>
  <div class="review-list">
    <article v-for="item in items" :key="item.id" class="review">
      <el-avatar :size="32" :src="item.fromUser?.avatar">{{ (item.fromUser?.nickname || '同').slice(0, 1) }}</el-avatar>
      <div class="review__body">
        <div class="review__hd">
          <b>{{ item.fromUser?.nickname || '同学' }}</b>
          <span class="review__stars" :aria-label="`${item.score} 星`">
            <el-icon v-for="n in 5" :key="n" :class="{ 'is-off': n > (item.score || 0) }"><StarFilled /></el-icon>
          </span>
          <time>{{ formatRelativeTime(item.createTime) }}</time>
        </div>
        <p>{{ item.content || '默认好评' }}</p>
      </div>
    </article>
    <p v-if="!items.length" class="review-empty">{{ emptyText }}</p>
  </div>
</template>

<script setup>
import { formatRelativeTime } from '@/utils/format'

defineProps({
  items: { type: Array, default: () => [] },
  emptyText: { type: String, default: '还没有评价' },
})
</script>

<style scoped lang="scss">
.review {
  display: flex;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--ct-border);

  &:last-of-type {
    border-bottom: 0;
  }
}

.review__body {
  flex: 1;
  min-width: 0;

  p {
    margin: 4px 0 0;
    color: var(--ct-text-2);
    font-size: 13px;
  }
}

.review__hd {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;

  time {
    margin-left: auto;
    color: var(--ct-text-3);
    font-size: 12px;
  }
}

.review__stars {
  display: inline-flex;
  color: #ffb020;
  font-size: 12px;

  .is-off {
    color: var(--ct-border-strong);
  }
}

.review-empty {
  margin: 0;
  padding: 18px 0;
  color: var(--ct-text-3);
  font-size: 13px;
}
</style>
