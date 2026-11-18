import gsap from 'gsap'
import { usePreferredReducedMotion } from '@vueuse/core'

/** 是否开启了「减少动态效果」（同步判断，供事件回调使用） */
export function isReducedMotion() {
  return typeof window !== 'undefined' && Boolean(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches)
}

/** 响应式版本：'reduce' | 'no-preference' */
export function useReducedMotion() {
  return usePreferredReducedMotion()
}

// GSAP 全局降级：减少动态效果时把时间轴加速到瞬时完成
if (typeof window !== 'undefined') {
  gsap.matchMedia().add('(prefers-reduced-motion: reduce)', () => {
    gsap.globalTimeline.timeScale(100)
    return () => gsap.globalTimeline.timeScale(1)
  })
}

const CONFETTI_COLORS = ['#ff3d6e', '#ff6b4a', '#ffc53d', '#8b5cf6', '#14c9a2']

/** 从元素上沿撒花（motion.md §6），减少动态效果时不生成 */
export function confettiFrom(el, count = 36) {
  if (!el || isReducedMotion()) return
  const rect = el.getBoundingClientRect()
  for (let i = 0; i < count; i += 1) {
    const piece = document.createElement('span')
    piece.className = 'ct-confetti'
    piece.style.left = `${rect.left + rect.width / 2}px`
    piece.style.top = `${rect.top}px`
    piece.style.background = CONFETTI_COLORS[i % CONFETTI_COLORS.length]
    document.body.appendChild(piece)
    const angle = -Math.PI / 2 + (Math.random() - 0.5) * Math.PI * 1.1
    const velocity = 160 + Math.random() * 200
    gsap.to(piece, {
      x: Math.cos(angle) * velocity,
      y: Math.sin(angle) * velocity + 260,
      rotation: Math.random() * 720,
      opacity: 0,
      duration: 1.1 + Math.random() * 0.5,
      ease: 'power2.out',
      onComplete: () => piece.remove(),
    })
  }
}

/** 错误抖动 ±6px 共 3 次（400ms） */
export function shake(el) {
  if (!el || isReducedMotion()) return
  gsap.fromTo(el, { x: 0 }, { x: 6, duration: 0.067, repeat: 5, yoyo: true, ease: 'sine.inOut', clearProps: 'x' })
}

/** 按钮涟漪：从点击点扩散到 2.6 倍并淡出（600ms） */
export function ripple(event) {
  const host = event?.currentTarget
  if (!host || isReducedMotion()) return
  const rect = host.getBoundingClientRect()
  const size = Math.max(rect.width, rect.height)
  const dot = document.createElement('span')
  dot.className = 'ct-ripple'
  dot.style.width = `${size}px`
  dot.style.height = `${size}px`
  dot.style.left = `${event.clientX - rect.left - size / 2}px`
  dot.style.top = `${event.clientY - rect.top - size / 2}px`
  if (getComputedStyle(host).position === 'static') host.style.position = 'relative'
  host.style.overflow = 'hidden'
  host.appendChild(dot)
  setTimeout(() => dot.remove(), 620)
}

/** 一组元素依次淡入上浮（推荐轮播翻页） */
export function staggerIn(elements, { y = 16, duration = 0.36, stagger = 0.05, delay = 0.15 } = {}) {
  if (!elements?.length || isReducedMotion()) return
  gsap.fromTo(elements, { opacity: 0, y }, { opacity: 1, y: 0, duration, stagger, ease: 'expo.out', delay, clearProps: 'opacity,transform' })
}
