<template>
  <div ref="rootRef" class="seg" role="tablist">
    <span class="seg__pill" :style="pillStyle" aria-hidden="true" />
    <button
      v-for="(item, index) in options"
      :key="String(item.value)"
      :ref="(el) => (btnRefs[index] = el)"
      type="button"
      role="tab"
      class="seg__item"
      :class="{ 'is-active': item.value === modelValue }"
      :aria-selected="item.value === modelValue"
      @click="select(item.value)"
    >
      {{ item.label }}
    </button>
  </div>
</template>

<script setup>
// 分段控件：白色滑块移动到选中项，宽度跟着变，360ms spring（motion.md §10）
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useResizeObserver } from '@vueuse/core'

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  options: { type: Array, default: () => [] }, // [{ label, value }]
})
const emit = defineEmits(['update:modelValue', 'change'])

const rootRef = ref()
const btnRefs = ref([])
const pill = ref({ left: 0, width: 0 })

const pillStyle = computed(() => ({
  width: `${pill.value.width}px`,
  transform: `translateX(${pill.value.left}px)`,
  opacity: pill.value.width ? 1 : 0,
}))

function measure() {
  const index = props.options.findIndex((item) => item.value === props.modelValue)
  const el = btnRefs.value[index]
  pill.value = el ? { left: el.offsetLeft, width: el.offsetWidth } : { left: 0, width: 0 }
}

function select(value) {
  if (value === props.modelValue) return
  emit('update:modelValue', value)
  emit('change', value)
}

watch(() => [props.modelValue, props.options], () => nextTick(measure), { deep: true })
onMounted(() => nextTick(measure))
useResizeObserver(rootRef, measure)
</script>

<style scoped lang="scss">
.seg {
  position: relative;
  display: inline-flex;
  padding: 4px;
  border-radius: var(--ct-radius-pill);
  background: var(--ct-surface-sunken);
}

.seg__item {
  position: relative;
  z-index: 1;
  height: 34px;
  padding: 0 16px;
  border: 0;
  border-radius: var(--ct-radius-pill);
  background: none;
  color: var(--ct-text-2);
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
  cursor: pointer;
  transition: color var(--ct-dur-base);

  &.is-active {
    color: var(--ct-text);
  }
}

.seg__pill {
  position: absolute;
  top: 4px;
  bottom: 4px;
  left: 0;
  border-radius: var(--ct-radius-pill);
  background: var(--ct-surface);
  box-shadow: var(--ct-shadow-2);
  transition:
    transform var(--ct-dur-slow) var(--ct-ease-spring),
    width var(--ct-dur-slow) var(--ct-ease-spring);
}
</style>
