import { onBeforeUnmount, ref, watch } from 'vue'

/**
 * motion.md §4：请求开始 150ms 后仍未返回才显示骨架；骨架一旦出现至少保留 300ms。
 * @param {import('vue').Ref<boolean>|(() => boolean)} loading
 */
export function useSkeletonDelay(loading, { delay = 150, minShow = 300 } = {}) {
  const show = ref(false)
  let showTimer = null
  let hideTimer = null
  let shownAt = 0

  function clear() {
    clearTimeout(showTimer)
    clearTimeout(hideTimer)
  }

  watch(
    loading,
    (value) => {
      clear()
      if (value) {
        if (show.value) return
        showTimer = setTimeout(() => {
          show.value = true
          shownAt = Date.now()
        }, delay)
      } else if (show.value) {
        const rest = Math.max(0, minShow - (Date.now() - shownAt))
        hideTimer = setTimeout(() => {
          show.value = false
        }, rest)
      }
    },
    { immediate: true },
  )

  onBeforeUnmount(clear)
  return show
}
