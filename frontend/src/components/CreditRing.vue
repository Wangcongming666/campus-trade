<template>
  <div class="credit-ring" :style="{ width: `${size}px`, height: `${size}px` }" :title="`信誉分 ${display}`">
    <svg :width="size" :height="size" :viewBox="`0 0 ${size} ${size}`" aria-hidden="true">
      <defs>
        <linearGradient :id="gradId" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="#14c9a2" />
          <stop offset="100%" stop-color="#5b5cf0" />
        </linearGradient>
      </defs>
      <circle class="credit-ring__bg" :cx="size / 2" :cy="size / 2" :r="radius" :stroke-width="stroke" />
      <circle
        class="credit-ring__fg"
        :cx="size / 2"
        :cy="size / 2"
        :r="radius"
        :stroke-width="stroke"
        :stroke="`url(#${gradId})`"
        :style="{ '--len': circumference, '--off': offset }"
      />
    </svg>
    <b class="num" :style="{ fontSize: `${Math.round(size * 0.27)}px` }">
      {{ display }}
      <small v-if="label">{{ label }}</small>
    </b>
  </div>
</template>

<script setup>
// 信誉分圆环：stroke-dashoffset 从满到目标值，1.5s expo-out（motion.md §10）
import { computed } from 'vue'

const props = defineProps({
  value: { type: Number, default: null },
  max: { type: Number, default: 100 },
  size: { type: Number, default: 96 },
  stroke: { type: Number, default: 8 },
  label: { type: String, default: '' },
})

const gradId = `cr-${Math.random().toString(36).slice(2, 8)}`
const radius = computed(() => (props.size - props.stroke) / 2)
const circumference = computed(() => 2 * Math.PI * radius.value)
const ratio = computed(() => {
  if (props.value == null) return 0
  return Math.min(1, Math.max(0, Number(props.value) / props.max))
})
const offset = computed(() => circumference.value * (1 - ratio.value))
const display = computed(() => (props.value == null ? '—' : props.value))
</script>

<style scoped lang="scss">
.credit-ring {
  position: relative;
  flex: none;

  svg {
    transform: rotate(-90deg);
  }

  circle {
    fill: none;
  }

  b {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    font-weight: 800;
    line-height: 1;
  }

  small {
    margin-top: 4px;
    color: var(--ct-text-3);
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0;
  }
}

.credit-ring__bg {
  stroke: var(--ct-surface-sunken);
}

.credit-ring__fg {
  stroke-linecap: round;
  stroke-dasharray: var(--len);
  stroke-dashoffset: var(--len);
  animation: credit-fill 1.5s var(--ct-ease-out) 0.3s forwards;
}

@keyframes credit-fill {
  to { stroke-dashoffset: var(--off); }
}

@media (prefers-reduced-motion: reduce) {
  .credit-ring__fg {
    animation: none;
    stroke-dashoffset: var(--off);
  }
}
</style>
