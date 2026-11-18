<template>
  <div>
    <div v-if="showSkeleton && !items.length" class="goods-grid" data-testid="goods-skeleton">
      <GoodsSkeleton v-for="index in skeletonCount" :key="index" />
    </div>

    <div v-else-if="!loading && !items.length" class="goods-empty">
      <slot name="empty">
        <el-empty :description="emptyText" :image-size="110" />
      </slot>
    </div>

    <div v-else-if="items.length" class="goods-grid" data-testid="goods-grid">
      <!-- motion.md §3 方案 A：@vueuse/motion 滚动出现，依次延迟 60ms（最多 8 个）；减少动态效果时不绑定 -->
      <template v-if="animate">
        <!-- 外包一层承载 v-motion 的内联 transform，避免覆盖卡片自身的 hover 上浮 -->
        <div
          v-for="(item, index) in items"
          :key="item.id"
          v-motion
          class="goods-grid__cell"
          :initial="{ opacity: 0, y: 24 }"
          :visible-once="{ opacity: 1, y: 0, transition: { duration: 560, delay: (index % 8) * 60, ease: [0.16, 1, 0.3, 1] } }"
        >
          <GoodsCard :goods="item" :show-reason="showReason" />
        </div>
      </template>
      <template v-else>
        <GoodsCard v-for="item in items" :key="item.id" :goods="item" :show-reason="showReason" />
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, toRef } from 'vue'
import GoodsCard from '@/components/GoodsCard.vue'
import GoodsSkeleton from '@/components/GoodsSkeleton.vue'
import { useReducedMotion } from '@/composables/useMotion'
import { useSkeletonDelay } from '@/composables/useSkeleton'

const props = defineProps({
  items: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  emptyText: { type: String, default: '暂时还没有商品' },
  skeletonCount: { type: Number, default: 8 },
  showReason: { type: Boolean, default: false },
})

const showSkeleton = useSkeletonDelay(toRef(props, 'loading'))
const reduced = useReducedMotion()
const animate = computed(() => reduced.value !== 'reduce')
</script>

<style scoped lang="scss">
.goods-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--ct-gutter);
}

.goods-grid__cell {
  display: flex;
  min-width: 0;

  > * {
    flex: 1;
    min-width: 0;
  }
}

.goods-empty {
  padding: 24px 0;
  border: 1px dashed var(--ct-border-strong);
  border-radius: var(--ct-radius-lg);
  background: var(--ct-surface-2);
}

@media (max-width: 1024px) {
  .goods-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .goods-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }
}
</style>
