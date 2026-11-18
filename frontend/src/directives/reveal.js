// motion.md §3 方案 B：滚动出现。进入视口 12% 触发一次，同组依次延迟 60ms（最多 8 个）。
// 减少动态效果时直接显示（CSS 里 [data-reveal] 也有兜底）。
let io = null

function reduced() {
  return typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
}

function observer() {
  if (io || typeof IntersectionObserver === 'undefined') return io
  io = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-in')
          io.unobserve(entry.target)
        }
      })
    },
    { threshold: 0.12, rootMargin: '0px 0px -40px 0px' },
  )
  return io
}

export default {
  mounted(el, { value = 0 }) {
    el.setAttribute('data-reveal', '')
    el.style.setProperty('--i', String((Number(value) || 0) % 8))
    const obs = observer()
    if (!obs || reduced()) {
      el.classList.add('is-in')
      return
    }
    obs.observe(el)
  },
  unmounted(el) {
    io?.unobserve(el)
  },
}
