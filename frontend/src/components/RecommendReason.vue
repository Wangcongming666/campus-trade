<template>
  <!-- 推荐理由条（design-system §6：柠檬黄底 + 图标，文案直接用推荐接口返回的 reason） -->
  <div v-if="text" class="ct-reason" :class="`ct-reason--${kind.key}`" :title="text" data-testid="recommend-reason" :data-type="kind.key">
    <span class="ct-reason__ico" :aria-label="kind.label"><el-icon><component :is="kind.icon" /></el-icon></span>
    <span class="ct-reason__text">{{ text }}</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  reason: { type: String, default: '' },
  /** 后端枚举 CF / INTEREST / HOT；未知值按通用样式显示 */
  type: { type: String, default: '' },
})

// 三种来源只在左侧小图标上区分色调，底色统一柠檬黄，保持克制
const KINDS = {
  CF: { key: 'cf', icon: 'MagicStick', label: '相似推荐' },
  INTEREST: { key: 'interest', icon: 'Aim', label: '兴趣推荐' },
  HOT: { key: 'hot', icon: 'TrendCharts', label: '热门推荐' },
}

const text = computed(() => (props.reason || '').trim())
const kind = computed(() => KINDS[String(props.type || '').toUpperCase()] || { key: 'default', icon: 'MagicStick', label: '推荐理由' })
</script>
