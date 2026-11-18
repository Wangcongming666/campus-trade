<template>
  <div class="rec-carousel" @pointerenter="hovering = true" @pointerleave="hovering = false">
    <div
      ref="vpRef"
      class="rec-vp"
      @pointerdown="onDown"
      @pointermove="onMove"
      @pointerup="onUp"
      @pointercancel="onUp"
    >
      <div
        ref="trackRef"
        class="rec-track"
        :class="{ 'is-dragging': dragging }"
        :style="{ transform: `translateX(${offset}px)` }"
      >
        <div v-for="item in items" :key="item.id" class="rec-cell">
          <GoodsCard :goods="item" show-reason />
        </div>
      </div>
    </div>
    <div v-if="pages > 1" class="rec-dots" role="tablist" aria-label="推荐分页">
      <button
        v-for="n in pages"
        :key="`${n}-${n - 1 === page ? cycle : 0}`"
        type="button"
        class="rec-dot"
        :class="{ 'is-on': n - 1 === page, 'is-paused': !autoplaying }"
        :aria-label="`第 ${n} 页`"
        @click="go(n - 1)"
      />
    </div>
  </div>
</template>

<script setup>
// motion.md §8：一屏 4 张，translateX 700ms expo-out；4.5s 自动播放，悬停/不可见/减少动态效果时暂停；拖拽翻页
import { computed, nextTick, ref, watch } from 'vue'
import { useDocumentVisibility, useElementSize, useIntervalFn, usePreferredReducedMotion } from '@vueuse/core'
import GoodsCard from '@/components/GoodsCard.vue'
import { staggerIn } from '@/composables/useMotion'

const props = defineProps({
  items: { type: Array, default: () => [] },
})
defineExpose({ prev: () => go(page.value - 1), next: () => go(page.value + 1) })

const GAP = 20
const vpRef = ref()
const trackRef = ref()
const page = ref(0)
const cycle = ref(0)
const hovering = ref(false)
const dragging = ref(false)
const dragDx = ref(0)
let dragStart = 0
let dragTime = 0

const { width } = useElementSize(vpRef)
const perPage = computed(() => (width.value < 700 ? 2 : width.value < 980 ? 3 : 4))
const pages = computed(() => Math.max(1, Math.ceil(props.items.length / perPage.value)))
const offset = computed(() => -page.value * (width.value + GAP) + dragDx.value)

const reduced = usePreferredReducedMotion()
const visibility = useDocumentVisibility()
const autoplaying = computed(
  () => pages.value > 1 && !hovering.value && !dragging.value && reduced.value !== 'reduce' && visibility.value === 'visible',
)
const { pause, resume } = useIntervalFn(() => go(page.value + 1, true), 4500, { immediate: false })

watch(autoplaying, (on) => (on ? resume() : pause()), { immediate: true })
watch(pages, () => {
  if (page.value >= pages.value) page.value = 0
})

function go(index, auto = false) {
  const total = pages.value
  page.value = ((index % total) + total) % total
  cycle.value += 1
  if (!auto) {
    pause()
    if (autoplaying.value) resume()
  }
  nextTick(() => {
    const cells = [...(trackRef.value?.children || [])].slice(page.value * perPage.value, page.value * perPage.value + perPage.value)
    staggerIn(cells)
  })
}

function onDown(event) {
  if (event.pointerType === 'mouse') return
  dragging.value = true
  dragStart = event.clientX
  dragTime = performance.now()
}

function onMove(event) {
  if (!dragging.value) return
  dragDx.value = event.clientX - dragStart
}

function onUp() {
  if (!dragging.value) return
  const dx = dragDx.value
  const speed = Math.abs(dx) / Math.max(1, performance.now() - dragTime)
  dragging.value = false
  dragDx.value = 0
  if (Math.abs(dx) > 60 || speed > 0.4) go(page.value + (dx < 0 ? 1 : -1))
}
</script>

<style scoped lang="scss">
.rec-vp {
  margin: 0 -8px;
  padding: 8px 8px 16px;
  overflow: hidden;
  touch-action: pan-y;
}

.rec-track {
  display: flex;
  gap: 20px;
  transition: transform 0.7s var(--ct-ease-out);

  &.is-dragging {
    transition: none;
  }
}

.rec-cell {
  display: flex;
  flex: 0 0 calc((100% - 60px) / 4);
  min-width: 0;

  > * {
    flex: 1;
    min-width: 0;
  }
}

.rec-dots {
  display: flex;
  justify-content: center;
  gap: 6px;
  margin-top: 4px;
}

.rec-dot {
  position: relative;
  width: 8px;
  height: 8px;
  padding: 0;
  overflow: hidden;
  border: 0;
  border-radius: 99px;
  background: var(--ct-border-strong);
  cursor: pointer;
  transition:
    width var(--ct-dur-slow) var(--ct-ease-out),
    background var(--ct-dur-slow);

  &.is-on {
    width: 32px;
    background: var(--ct-primary-soft-2);

    &::after {
      content: '';
      position: absolute;
      inset: 0;
      background: var(--ct-primary);
      transform-origin: left;
      animation: rec-prog 4.5s linear forwards;
    }

    &.is-paused::after {
      animation: none;
    }
  }
}

@keyframes rec-prog {
  from { transform: scaleX(0); }
}

@media (max-width: 980px) {
  .rec-cell { flex-basis: calc((100% - 40px) / 3); }
}

@media (max-width: 700px) {
  .rec-cell { flex-basis: calc((100% - 20px) / 2); }
}
</style>
