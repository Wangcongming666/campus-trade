<template>
  <span ref="elRef" class="num">{{ text }}</span>
</template>

<script setup>
// 数字滚动：进入视口后从 0 增长到目标值，1.2s easeOutQuart（motion.md §10）；减少动态效果时直接显示终值
import { computed, ref, watch } from 'vue'
import { useIntersectionObserver, usePreferredReducedMotion, useTransition } from '@vueuse/core'

const props = defineProps({
  value: { type: Number, default: null },
  decimals: { type: Number, default: 0 },
  prefix: { type: String, default: '' },
  placeholder: { type: String, default: '—' },
})

const elRef = ref()
const source = ref(0)
const seen = ref(false)
const reduced = usePreferredReducedMotion()
const output = useTransition(source, {
  duration: 1200,
  transition: [0.25, 1, 0.5, 1],
  disabled: computed(() => reduced.value === 'reduce'),
})

useIntersectionObserver(elRef, ([entry]) => {
  if (entry?.isIntersecting) seen.value = true
})

watch(
  () => [props.value, seen.value],
  () => {
    if (seen.value && props.value != null) source.value = Number(props.value) || 0
  },
  { immediate: true },
)

const text = computed(() => {
  if (props.value == null) return props.placeholder
  const num = Number(output.value)
  return `${props.prefix}${num.toLocaleString('zh-CN', {
    minimumFractionDigits: props.decimals,
    maximumFractionDigits: props.decimals,
  })}`
})
</script>
