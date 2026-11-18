<template>
  <button
    type="button"
    class="like-btn"
    :class="[`like-btn--${variant}`, { 'is-on': liked, 'is-busy': busy }]"
    :aria-pressed="liked"
    :aria-label="liked ? '取消收藏' : '收藏'"
    data-testid="like-btn"
    @click.stop.prevent="toggle"
  >
    <span ref="hostRef" class="like-btn__host">
      <svg class="like-btn__icon" :class="{ 'is-beat': beat }" viewBox="0 0 24 24" aria-hidden="true">
        <path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z" />
      </svg>
    </span>
    <span v-if="variant === 'big'" class="like-btn__count num">{{ count ?? 0 }}</span>
  </button>
</template>

<script setup>
// motion.md §5：心形弹跳 + 光环 + 10 个粒子；乐观更新，失败回滚
import { ref, watch } from 'vue'
import { isReducedMotion } from '@/composables/useMotion'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  count: { type: Number, default: null },
  variant: { type: String, default: 'icon' }, // icon：卡片右上角圆形；big：详情页带计数
  /** async (next: boolean) => void，抛错时回滚 */
  onToggle: { type: Function, default: null },
})
const emit = defineEmits(['update:modelValue', 'update:count'])

const COLORS = ['#ff3d6e', '#ff6b4a', '#ffc53d', '#8b5cf6', '#14c9a2']
const liked = ref(props.modelValue)
const busy = ref(false)
const beat = ref(false)
const hostRef = ref()

watch(
  () => props.modelValue,
  (value) => {
    liked.value = value
  },
)

function burst() {
  const host = hostRef.value
  if (!host || isReducedMotion()) return
  const ring = document.createElement('span')
  ring.className = 'like-btn__ring'
  host.appendChild(ring)
  setTimeout(() => ring.remove(), 520)
  for (let i = 0; i < 10; i += 1) {
    const dot = document.createElement('span')
    const angle = (Math.PI * 2 * i) / 10
    const distance = 22 + Math.random() * 12
    dot.className = 'like-btn__particle'
    dot.style.background = COLORS[i % COLORS.length]
    dot.style.setProperty('--dx', `${Math.cos(angle) * distance}px`)
    dot.style.setProperty('--dy', `${Math.sin(angle) * distance}px`)
    host.appendChild(dot)
    setTimeout(() => dot.remove(), 650)
  }
}

async function toggle() {
  if (busy.value) return
  const next = !liked.value
  const prevCount = props.count
  liked.value = next
  emit('update:modelValue', next)
  if (props.count != null) emit('update:count', Math.max(0, props.count + (next ? 1 : -1)))
  if (next) {
    beat.value = false
    requestAnimationFrame(() => {
      beat.value = true
    })
    burst()
  }
  busy.value = true
  try {
    await props.onToggle?.(next)
  } catch {
    liked.value = !next
    emit('update:modelValue', !next)
    if (prevCount != null) emit('update:count', prevCount)
  } finally {
    busy.value = false
  }
}
</script>

<style scoped lang="scss">
.like-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 0;
  border: 0;
  font: inherit;
  cursor: pointer;
  transition:
    transform var(--ct-dur-fast) var(--ct-ease-out),
    color var(--ct-dur-fast),
    background-color var(--ct-dur-base);
}

.like-btn--icon {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: var(--ct-glass);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  color: var(--ct-text-2);

  &:hover {
    transform: scale(1.1);
    color: var(--ct-accent);
  }
}

.like-btn--big {
  height: 52px;
  padding: 0 18px;
  border-radius: var(--ct-radius-lg);
  background: var(--ct-surface-sunken);
  color: var(--ct-text-2);
  font-weight: 700;

  &:hover {
    color: var(--ct-accent);
  }

  &.is-on {
    background: var(--ct-accent-soft);
  }
}

.like-btn.is-on {
  color: var(--ct-accent);
}

.like-btn__host {
  position: relative;
  display: grid;
  place-items: center;
}

.like-btn__icon {
  width: 19px;
  height: 19px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
  transition: fill var(--ct-dur-fast);
}

/* 设计答复 #2：已收藏用 --ct-accent 填充 */
.is-on .like-btn__icon {
  fill: var(--ct-accent);
}

.like-btn__icon.is-beat {
  animation: like-beat 0.5s var(--ct-ease-spring);
}

:deep(.like-btn__ring) {
  position: absolute;
  inset: -8px;
  border: 2px solid var(--ct-accent);
  border-radius: 50%;
  pointer-events: none;
  animation: like-ring 0.5s var(--ct-ease-out) forwards;
}

:deep(.like-btn__particle) {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 6px;
  height: 6px;
  margin: -3px;
  border-radius: 50%;
  pointer-events: none;
  animation: like-particle 0.6s var(--ct-ease-out) forwards;
}

@keyframes like-beat {
  0% { transform: scale(1); }
  25% { transform: scale(0.7); }
  60% { transform: scale(1.35); }
  100% { transform: scale(1); }
}

@keyframes like-ring {
  from { transform: scale(0.4); opacity: 1; }
  to { transform: scale(1.6); opacity: 0; }
}

@keyframes like-particle {
  from { transform: translate(0, 0) scale(1); opacity: 1; }
  to { transform: translate(var(--dx), var(--dy)) scale(0); opacity: 0; }
}
</style>
