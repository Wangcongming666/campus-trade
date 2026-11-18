<template>
  <div class="user-layout">
    <header class="app-header" :class="{ 'is-scrolled': scrolled }">
      <div class="app-header__inner">
        <router-link to="/" class="logo" aria-label="返回首页">
          <span class="logo__mark"><el-icon :size="20"><ShoppingBag /></el-icon></span>
          <span class="logo__text">
            校园二手
            <small>CAMPUS · TRADE</small>
          </span>
        </router-link>

        <nav class="nav" aria-label="主导航">
          <router-link to="/" class="nav__item" :class="{ 'is-active': navActive === 'home' }">发现</router-link>
          <el-dropdown trigger="hover" @command="onCategoryCommand">
            <span class="nav__item" :class="{ 'is-active': navActive === 'search' }">分类</span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="item in categories" :key="item.id" :command="item.id">
                  {{ item.name }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <router-link to="/chat" class="nav__item" :class="{ 'is-active': navActive === 'chat' }">消息</router-link>
          <router-link to="/user" class="nav__item" :class="{ 'is-active': navActive === 'user' }">我的</router-link>
        </nav>

        <!-- 设计答复 #7：纯胶囊搜索框，聚焦时 440 → 500，回车提交，右侧快捷键提示 -->
        <label class="search ct-search" data-testid="header-search">
          <el-icon class="search__icon"><Search /></el-icon>
          <input
            ref="searchRef"
            v-model="keyword"
            type="search"
            placeholder="搜索教材、数码、自行车…"
            aria-label="搜索闲置"
            @keydown.enter="goSearch"
            @keydown.esc="searchRef?.blur()"
          />
          <kbd class="search__kbd">/</kbd>
        </label>

        <el-button type="primary" round class="publish-btn" @click="onPublish">
          <el-icon class="el-icon--left"><Plus /></el-icon>
          发布闲置
        </el-button>

        <button type="button" class="ct-icon-btn" title="消息" aria-label="消息" @click="goChat">
          <el-icon :size="20"><ChatDotRound /></el-icon>
          <span v-if="unreadCount" class="ct-dot-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
        </button>

        <!-- 暗色切换：顶栏右侧、头像左边 -->
        <ThemeToggle />

        <template v-if="userStore.isLoggedIn">
          <el-dropdown trigger="click" @command="onAvatarCommand">
            <span class="avatar-trigger" :title="displayName">
              <el-avatar :size="38" :src="userStore.userInfo?.avatar">{{ avatarText }}</el-avatar>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled class="avatar-menu__name">{{ displayName }}</el-dropdown-item>
                <el-dropdown-item command="profile" divided>个人中心</el-dropdown-item>
                <el-dropdown-item command="orders">我的订单</el-dropdown-item>
                <el-dropdown-item v-if="userStore.isAdmin" command="admin" divided>管理后台</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <div v-else class="header-auth">
          <el-button text @click="$router.push('/login')">登录</el-button>
          <el-button type="primary" round @click="$router.push('/register')">注册</el-button>
        </div>
      </div>
    </header>

    <main class="user-main">
      <!-- motion.md §1：页面切换 -->
      <router-view v-slot="{ Component, route: viewRoute }">
        <Transition name="page" mode="out-in">
          <component :is="Component" :key="viewRoute.path" />
        </Transition>
      </router-view>
    </main>

    <footer class="app-footer">
      <div class="app-footer__inner">
        <span>© 2026 校园二手交易智能平台 · 毕业设计</span>
        <span>Vue 3 · Element Plus · Iris Design System</span>
      </div>
    </footer>
  </div>
</template>

<script setup>
import ThemeToggle from '@/components/ThemeToggle.vue'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useWindowScroll } from '@vueuse/core'
import { useRoute, useRouter } from 'vue-router'
import { getCategoryList } from '@/api/category'
import { useChatStore } from '@/stores/chat'
import { useUserStore } from '@/stores/user'
import { ripple } from '@/composables/useMotion'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const chatStore = useChatStore()

const keyword = ref(typeof route.query.keyword === 'string' ? route.query.keyword : '')

watch(
  () => route.query.keyword,
  (value) => {
    keyword.value = typeof value === 'string' ? value : ''
  },
)
const unreadCount = computed(() => chatStore.unreadTotal)
const categories = ref([
  { id: 'digital', name: '数码电子' },
  { id: 'books', name: '教材书籍' },
  { id: 'daily', name: '生活日用' },
  { id: 'sports', name: '运动户外' },
])

const displayName = computed(
  () => userStore.userInfo?.nickname || userStore.userInfo?.account || '同学',
)
const avatarText = computed(() => displayName.value.slice(0, 1))

function goSearch() {
  const value = keyword.value.trim()
  router.push({ path: '/search', query: value ? { keyword: value } : {} })
}

function onCategoryCommand(id) {
  // id 保持字符串，不做数字转换
  router.push({ path: '/search', query: { categoryId: String(id) } })
}

function onPublish(event) {
  ripple(event)
  router.push('/publish')
}

// 吸顶头部：滚动超过 8px 后加 shadow-2（motion.md §10）
const { y: scrollY } = useWindowScroll()
const scrolled = computed(() => scrollY.value > 8)

const navActive = computed(() => {
  const path = route.path
  if (path === '/') return 'home'
  if (path.startsWith('/search')) return 'search'
  if (path.startsWith('/chat')) return 'chat'
  if (path.startsWith('/user') && path === '/user') return 'user'
  if (path.startsWith('/orders')) return 'user'
  return ''
})

// 「/」快捷键聚焦搜索框（输入框内输入时不拦截）
const searchRef = ref()
function onGlobalKey(event) {
  if (event.key !== '/' || event.metaKey || event.ctrlKey || event.altKey) return
  const tag = (event.target?.tagName || '').toLowerCase()
  if (tag === 'input' || tag === 'textarea' || event.target?.isContentEditable) return
  event.preventDefault()
  searchRef.value?.focus()
}
window.addEventListener('keydown', onGlobalKey)
onBeforeUnmount(() => window.removeEventListener('keydown', onGlobalKey))

function goChat() {
  router.push('/chat')
}

async function onAvatarCommand(command) {
  if (command === 'profile') router.push('/user')
  else if (command === 'orders') router.push('/orders')
  else if (command === 'admin') router.push('/admin/dashboard')
  else if (command === 'logout') {
    await userStore.logout()
    router.push('/')
  }
}

onMounted(async () => {
  try {
    const data = await getCategoryList()
    const list = Array.isArray(data) ? data : data?.records || data?.list
    if (list?.length) {
      categories.value = list
        .filter((item) => item.parentId == null || item.parentId === '' || item.parentId === 0)
        .map((item) => ({
          id: String(item.id),
          name: item.name,
        }))
    }
  } catch {
    // 分类接口尚未联调时保留占位分类
  }
})
</script>

<style scoped lang="scss">
.user-layout {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 100%;

  /* 顶部 900px 网格渐变氛围背景，向下渐隐 */
  &::before {
    content: '';
    position: absolute;
    inset: 0 0 auto;
    height: 900px;
    background: var(--ct-bg-mesh);
    pointer-events: none;
    -webkit-mask-image: linear-gradient(#000 40%, transparent);
    mask-image: linear-gradient(#000 40%, transparent);
  }
}

.app-header {
  position: sticky;
  top: 0;
  z-index: var(--ct-z-header);
  height: var(--ct-header-h);
  border-bottom: 1px solid var(--ct-glass-border);
  background: var(--ct-glass);
  backdrop-filter: blur(20px) saturate(1.8);
  -webkit-backdrop-filter: blur(20px) saturate(1.8);
  transition: box-shadow var(--ct-dur-base), background-color var(--ct-dur-slow);

  &.is-scrolled {
    box-shadow: var(--ct-shadow-2);
  }
}

.app-header__inner {
  display: flex;
  align-items: center;
  gap: 20px;
  max-width: var(--ct-container);
  height: 100%;
  margin: 0 auto;
  padding: 0 var(--ct-container-pad);
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--ct-text);
  font-size: 18px;
  font-weight: 800;
  letter-spacing: -0.01em;
  white-space: nowrap;
}

.logo__mark {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: var(--ct-gradient-brand);
  box-shadow: var(--ct-shadow-glow);
  color: #fff;
  transform: rotate(-6deg);
  transition: transform var(--ct-dur-slow) var(--ct-ease-spring);
}

.logo:hover .logo__mark {
  transform: rotate(6deg) scale(1.06);
}

.logo__text {
  line-height: 1.2;

  small {
    display: block;
    color: var(--ct-text-3);
    font-size: 10px;
    font-weight: 600;
    letter-spacing: 0.08em;
  }
}

.nav {
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav__item {
  display: inline-flex;
  align-items: center;
  padding: 8px 14px;
  border-radius: var(--ct-radius-pill);
  color: var(--ct-text-2);
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  outline: none;
  transition: color var(--ct-dur-fast), background-color var(--ct-dur-fast);

  &:hover {
    background: var(--ct-surface-sunken);
    color: var(--ct-text);
  }

  &.is-active {
    background: var(--ct-primary-soft);
    color: var(--ct-primary);
  }
}

.search {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 10px;
  max-width: 440px;
  height: 44px;
  margin-left: auto;
  padding: 0 8px 0 16px;
  border: 1px solid transparent;
  border-radius: var(--ct-radius-pill);
  background: var(--ct-surface-sunken);
  color: var(--ct-text-3);
  cursor: text;
  transition: all var(--ct-dur-base) var(--ct-ease-out);

  &:focus-within {
    max-width: 500px;
    border-color: var(--ct-primary);
    background: var(--ct-surface);
    box-shadow: var(--ct-ring);
    color: var(--ct-primary);
  }

  input {
    flex: 1;
    min-width: 0;
    border: 0;
    outline: 0;
    background: none;
    color: var(--ct-text);
    font: inherit;

    &::placeholder {
      color: var(--ct-text-3);
    }

    &::-webkit-search-cancel-button {
      display: none;
    }
  }
}

.search__kbd {
  padding: 1px 6px;
  border: 1px solid var(--ct-border-strong);
  border-radius: 6px;
  background: var(--ct-surface);
  color: var(--ct-text-3);
  font-family: var(--ct-font-num);
  font-size: 11px;
  font-weight: 700;
}

.publish-btn {
  height: 44px;
  padding: 0 20px;
}

.avatar-trigger {
  display: inline-flex;
  border-radius: 50%;
  cursor: pointer;
  outline: none;
}

.header-auth {
  display: flex;
  align-items: center;
  gap: 4px;
}

.user-main {
  position: relative;
  z-index: 1;
  flex: 1;
  width: 100%;
  max-width: var(--ct-container);
  margin: 0 auto;
  padding: 0 var(--ct-container-pad);
}

.app-footer {
  position: relative;
  z-index: 1;
  margin-top: 80px;
  padding: 32px 0 40px;
  border-top: 1px solid var(--ct-border);
  color: var(--ct-text-3);
  font-size: 13px;
}

.app-footer__inner {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  max-width: var(--ct-container);
  margin: 0 auto;
  padding: 0 var(--ct-container-pad);
}

@media (max-width: 1024px) {
  .nav {
    display: none;
  }
}

@media (max-width: 768px) {
  .search,
  .logo__text small {
    display: none;
  }

  .app-header__inner {
    gap: 10px;
  }

  .publish-btn {
    margin-left: auto;
  }
}
</style>
