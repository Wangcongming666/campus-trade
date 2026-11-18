import { computed, ref, watch } from 'vue'
import { isReducedMotion } from '@/composables/useMotion'

/**
 * 主题：light | dark | auto（默认跟随系统）
 * - 手动选择存到 localStorage 的 ct-theme；选「跟随系统」时删除该键
 * - 生效方式：<html class="dark">，与 Element Plus 官方暗色方案一致（index.html 里有防闪烁脚本）
 */
const STORAGE_KEY = 'ct-theme'
const media = typeof window !== 'undefined' && window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null

function readMode() {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return value === 'light' || value === 'dark' ? value : 'auto'
  } catch {
    return 'auto'
  }
}

export const themeMode = ref(readMode())
const systemDark = ref(Boolean(media?.matches))
media?.addEventListener?.('change', (event) => {
  systemDark.value = event.matches
})

export const isDark = computed(() => (themeMode.value === 'auto' ? systemDark.value : themeMode.value === 'dark'))

function apply(dark) {
  const el = document.documentElement
  el.classList.toggle('dark', dark)
  el.style.colorScheme = dark ? 'dark' : 'light'
}

watch(isDark, apply, { immediate: typeof document !== 'undefined' })

export function setThemeMode(mode) {
  const next = mode === 'light' || mode === 'dark' ? mode : 'auto'
  themeMode.value = next
  try {
    if (next === 'auto') localStorage.removeItem(STORAGE_KEY)
    else localStorage.setItem(STORAGE_KEY, next)
  } catch {
    // 隐私模式下写不进去就只在本次会话生效
  }
}

/**
 * motion.md §9：从点击位置向外圆形扩散 560ms。
 * 不支持 View Transitions 或开启减少动态效果时直接切换。
 */
export function switchThemeWithTransition(mode, event) {
  const nextDark = mode === 'auto' ? systemDark.value : mode === 'dark'
  const change = () => {
    setThemeMode(mode)
    apply(nextDark)
  }
  if (nextDark === isDark.value || typeof document.startViewTransition !== 'function' || isReducedMotion()) {
    change()
    return
  }
  const rect = event?.currentTarget?.getBoundingClientRect?.()
  const x = rect ? rect.left + rect.width / 2 : event?.clientX ?? window.innerWidth / 2
  const y = rect ? rect.top + rect.height / 2 : event?.clientY ?? 0
  const r = Math.hypot(Math.max(x, window.innerWidth - x), Math.max(y, window.innerHeight - y))
  const transition = document.startViewTransition(change)
  transition.ready
    .then(() => {
      document.documentElement.animate(
        { clipPath: [`circle(0px at ${x}px ${y}px)`, `circle(${r}px at ${x}px ${y}px)`] },
        { duration: 560, easing: 'cubic-bezier(.16,1,.3,1)', pseudoElement: '::view-transition-new(root)' },
      )
    })
    .catch(() => {})
}

export function toggleTheme(event) {
  switchThemeWithTransition(isDark.value ? 'light' : 'dark', event)
}
