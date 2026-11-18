<template>
  <!-- 设计答复 #8：后台整体使用 small 尺寸，用户端保持 38px -->
  <el-config-provider size="small">
    <div class="admin-layout">
      <aside class="admin-aside">
        <router-link to="/admin/dashboard" class="admin-logo">
          <span class="admin-logo__mark"><el-icon><Monitor /></el-icon></span>
          <span class="admin-logo__text">
            <b>校园二手</b>
            <small>ADMIN CONSOLE</small>
          </span>
        </router-link>

        <nav class="admin-nav" aria-label="后台导航">
          <template v-for="group in NAV_GROUPS" :key="group.label">
            <p class="admin-nav__group">{{ group.label }}</p>
            <router-link
              v-for="item in group.items"
              :key="item.path"
              :to="item.path"
              class="admin-nav__item"
              :class="{ 'is-active': activeMenu === item.path }"
            >
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.label }}</span>
              <em v-if="item.path === '/admin/audit' && pendingTotal" class="admin-nav__badge num">{{ pendingTotal }}</em>
            </router-link>
          </template>
        </nav>

        <div class="me-card">
          <el-avatar :size="36" :src="userStore.userInfo?.avatar">{{ displayName.slice(0, 1) }}</el-avatar>
          <div class="me-card__body">
            <b>{{ displayName }}</b>
            <span>管理员</span>
          </div>
          <el-tooltip content="返回前台" placement="top">
            <router-link to="/" class="me-card__btn" aria-label="返回前台"><el-icon><HomeFilled /></el-icon></router-link>
          </el-tooltip>
          <el-tooltip content="退出登录" placement="top">
            <button type="button" class="me-card__btn" aria-label="退出登录" @click="handleLogout"><el-icon><SwitchButton /></el-icon></button>
          </el-tooltip>
        </div>
      </aside>

      <div class="admin-body">
        <header class="admin-topbar">
          <div class="admin-topbar__title">
            <p class="admin-crumb">
              <router-link to="/admin/dashboard">管理后台</router-link>
              <el-icon><ArrowRight /></el-icon>
              <span>{{ currentNav?.label || '控制台' }}</span>
            </p>
            <h1>{{ pageTitle }}</h1>
          </div>
          <!-- 子页可以把筛选控件 Teleport 到这里（控制台的时间范围） -->
          <div id="admin-topbar-extra" class="admin-topbar__extra" />
          <ThemeToggle />
          <el-button round class="ct-btn-ghost" @click="$router.push('/')">
            <el-icon class="el-icon--left"><Back /></el-icon>返回前台
          </el-button>
        </header>
        <main class="admin-main">
          <router-view v-slot="{ Component, route: viewRoute }">
            <Transition name="page-admin" mode="out-in">
              <component :is="Component" :key="viewRoute.path" />
            </Transition>
          </router-view>
        </main>
      </div>
    </div>
  </el-config-provider>
</template>

<script setup>
import { computed, onMounted, provide, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAdminStats } from '@/api/admin'
import ThemeToggle from '@/components/ThemeToggle.vue'
import { useUserStore } from '@/stores/user'

const NAV_GROUPS = [
  {
    label: '概览',
    items: [
      { path: '/admin/dashboard', label: '控制台', icon: 'Odometer', title: '运营概览' },
      { path: '/admin/audit', label: '审核中心', icon: 'Checked', title: '审核中心' },
    ],
  },
  {
    label: '管理',
    items: [
      { path: '/admin/users', label: '用户管理', icon: 'User', title: '用户管理' },
      { path: '/admin/goods', label: '商品管理', icon: 'Goods', title: '商品管理' },
      { path: '/admin/reports', label: '举报处理', icon: 'WarningFilled', title: '举报处理' },
      { path: '/admin/announcements', label: '公告管理', icon: 'Bell', title: '公告管理' },
    ],
  },
]

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const pendingGoods = ref(0)
const pendingStudent = ref(0)
const pendingTotal = computed(() => (pendingGoods.value || 0) + (pendingStudent.value || 0))

const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/admin/audit')) return '/admin/audit'
  if (path.startsWith('/admin/users')) return '/admin/users'
  if (path.startsWith('/admin/goods')) return '/admin/goods'
  if (path.startsWith('/admin/reports')) return '/admin/reports'
  if (path.startsWith('/admin/announcements')) return '/admin/announcements'
  return '/admin/dashboard'
})
const currentNav = computed(() => NAV_GROUPS.flatMap((group) => group.items).find((item) => item.path === activeMenu.value))
const pageTitle = computed(() => currentNav.value?.title || route.meta?.title || '管理后台')
const displayName = computed(
  () => userStore.userInfo?.nickname || userStore.userInfo?.username || '管理员',
)

async function loadBadge() {
  try {
    const data = await getAdminStats({ days: 7 })
    pendingGoods.value = Number(data?.pendingGoodsAudit) || 0
    pendingStudent.value = Number(data?.pendingStudentVerify) || 0
  } catch {
    pendingGoods.value = 0
    pendingStudent.value = 0
  }
}

async function handleLogout() {
  await userStore.logout()
  router.push('/login')
}

// 审核中心处理完一条后刷新侧栏待审数量
provide('refreshAdminBadge', loadBadge)

onMounted(loadBadge)

defineExpose({ loadBadge })
</script>

<style scoped lang="scss">
.admin-layout {
  display: grid;
  grid-template-columns: 248px minmax(0, 1fr);
  min-height: 100vh;
  background: var(--ct-bg-mesh) no-repeat top / 100% 900px, var(--ct-bg);
}

/* ---------- 侧栏 ---------- */
.admin-aside {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  height: 100vh;
  padding: 18px 14px;
  border-right: 1px solid var(--ct-border);
  background: var(--ct-glass);
  backdrop-filter: blur(20px) saturate(1.8);
  -webkit-backdrop-filter: blur(20px) saturate(1.8);
}

.admin-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 8px 18px;
  color: var(--ct-text);
}

.admin-logo__mark {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: var(--ct-gradient-brand);
  box-shadow: var(--ct-shadow-glow);
  color: #fff;
  font-size: 18px;
  transform: rotate(-6deg);
}

.admin-logo__text {
  display: flex;
  flex-direction: column;
  line-height: 1.15;

  b {
    font-size: 16px;
    font-weight: 800;
  }

  small {
    color: var(--ct-text-3);
    font-size: 10px;
    letter-spacing: 0.12em;
  }
}

.admin-nav {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
}

.admin-nav__group {
  margin: 14px 12px 6px;
  color: var(--ct-text-3);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.admin-nav__item {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 42px;
  padding: 0 12px;
  border-radius: 14px;
  color: var(--ct-text-2);
  font-size: 14px;
  font-weight: 600;
  transition:
    background var(--ct-dur-base),
    color var(--ct-dur-base),
    box-shadow var(--ct-dur-base);

  span {
    flex: 1;
  }

  .el-icon {
    font-size: 17px;
  }

  &:hover {
    background: var(--ct-surface-sunken);
    color: var(--ct-text);
  }

  /* 当前项：品牌渐变填充 */
  &.is-active {
    background: var(--ct-gradient-brand);
    box-shadow: var(--ct-shadow-glow);
    color: #fff;
  }
}

/* 设计答复 #4：未选中项珊瑚底白字；选中项白底珊瑚字 */
.admin-nav__badge {
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: var(--ct-accent);
  color: #fff;
  font-size: 11px;
  font-style: normal;
  font-weight: 700;
  line-height: 20px;
  text-align: center;
  animation: ct-badge-pop 0.4s var(--ct-ease-spring);

  .is-active & {
    background: #fff;
    color: var(--ct-accent);
  }
}

.me-card {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--ct-border);
  border-radius: 18px;
  background: var(--ct-surface);
  box-shadow: var(--ct-shadow-1);
}

.me-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  line-height: 1.3;

  b {
    overflow: hidden;
    font-size: 13px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    color: var(--ct-text-3);
    font-size: 11px;
  }
}

.me-card__btn {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: var(--ct-text-3);
  cursor: pointer;
  transition:
    background var(--ct-dur-fast),
    color var(--ct-dur-fast);

  &:hover {
    background: var(--ct-surface-sunken);
    color: var(--ct-primary);
  }
}

/* ---------- 主体 ---------- */
.admin-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.admin-topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 16px;
  height: 76px;
  padding: 0 32px;
  border-bottom: 1px solid var(--ct-glass-border);
  background: var(--ct-glass);
  backdrop-filter: blur(20px) saturate(1.8);
  -webkit-backdrop-filter: blur(20px) saturate(1.8);
}

.admin-topbar__title {
  flex: 1;
  min-width: 0;

  h1 {
    margin: 0;
    font-size: 22px;
    font-weight: 800;
    letter-spacing: -0.01em;
  }
}

.admin-crumb {
  display: flex;
  align-items: center;
  gap: 4px;
  margin: 0 0 2px;
  color: var(--ct-text-3);
  font-size: 12px;

  a {
    color: inherit;

    &:hover {
      color: var(--ct-primary);
    }
  }
}

.admin-topbar__extra {
  display: flex;
  align-items: center;
  gap: 12px;
}

.admin-main {
  flex: 1;
  padding: 28px 32px 40px;
}

/* 子页标题已由 topbar 显示，页内 h1 隐藏；只剩标题的 page-head 整行收起 */
.admin-main :deep(.page-head h1) {
  display: none;
}

.admin-main :deep(.page-head:not(:has(> :not(h1)))) {
  display: none;
}

.admin-main :deep(.page-head) {
  justify-content: flex-end;
}

@media (max-width: 1024px) {
  .admin-layout {
    grid-template-columns: 76px minmax(0, 1fr);
  }

  .admin-logo__text,
  .admin-nav__group,
  .admin-nav__item span,
  .me-card__body,
  .me-card .el-avatar {
    display: none;
  }

  .admin-nav__item {
    position: relative;
    justify-content: center;
  }

  .admin-nav__badge {
    position: absolute;
    top: 2px;
    right: 2px;
  }

  .me-card {
    flex-direction: column;
    padding: 8px 4px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .admin-nav__badge {
    animation: none;
  }
}
</style>
