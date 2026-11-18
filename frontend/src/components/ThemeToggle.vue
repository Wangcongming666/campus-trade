<template>
  <button
    type="button"
    class="theme-toggle"
    :class="{ 'is-dark': isDark }"
    :aria-label="isDark ? '切换到浅色模式' : '切换到深色模式'"
    :title="isDark ? '切换到浅色模式' : '切换到深色模式'"
    data-testid="theme-toggle"
    @click="toggleTheme"
  >
    <span class="theme-toggle__icons" aria-hidden="true">
      <el-icon class="theme-toggle__sun"><Sunny /></el-icon>
      <el-icon class="theme-toggle__moon"><Moon /></el-icon>
    </span>
  </button>
</template>

<script setup>
import { isDark, toggleTheme } from '@/composables/useTheme'
</script>

<style scoped lang="scss">
.theme-toggle {
  position: relative;
  display: grid;
  flex: none;
  place-items: center;
  width: 40px;
  height: 40px;
  overflow: hidden;
  padding: 0;
  border: 1px solid var(--ct-border);
  border-radius: 50%;
  background: var(--ct-surface);
  color: var(--ct-text-2);
  cursor: pointer;
  transition:
    background var(--ct-dur-base),
    border-color var(--ct-dur-base),
    color var(--ct-dur-base),
    transform var(--ct-dur-fast) var(--ct-ease-spring);

  &:hover {
    border-color: var(--ct-primary);
    color: var(--ct-primary);
  }

  &:active {
    transform: scale(0.92);
  }
}

.theme-toggle__icons {
  position: relative;
  width: 18px;
  height: 18px;
  font-size: 18px;

  .el-icon {
    position: absolute;
    inset: 0;
    transition:
      transform 0.56s var(--ct-ease-spring),
      opacity 0.3s;
  }
}

/* 太阳向上转出，月亮从下方转入 */
.theme-toggle__moon {
  opacity: 0;
  transform: translateY(120%) rotate(-90deg);
}

.is-dark {
  .theme-toggle__sun {
    opacity: 0;
    transform: translateY(-120%) rotate(90deg);
  }

  .theme-toggle__moon {
    opacity: 1;
    transform: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .theme-toggle__icons .el-icon {
    transition: opacity 0.01ms;
  }
}
</style>
