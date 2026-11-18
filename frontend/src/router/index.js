import { createRouter, createWebHistory } from 'vue-router'
import { Role } from '@/constants/enums'
import { useUserStore } from '@/stores/user'

const routes = [
  {
    path: '/',
    component: () => import('@/layouts/UserLayout.vue'),
    children: [
      // 游客可访问：首页、搜索、商品详情（规范第 6 节白名单）
      {
        path: '',
        name: 'Home',
        component: () => import('@/views/user/Home.vue'),
        meta: { title: '首页' },
      },
      {
        path: 'search',
        name: 'Search',
        component: () => import('@/views/user/Search.vue'),
        meta: { title: '搜索' },
      },
      {
        path: 'goods/:id',
        name: 'GoodsDetail',
        component: () => import('@/views/user/GoodsDetail.vue'),
        meta: { title: '商品详情' },
      },
      {
        path: 'publish',
        name: 'Publish',
        component: () => import('@/views/user/Publish.vue'),
        meta: { title: '发布商品', requiresAuth: true },
      },
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('@/views/user/Chat.vue'),
        meta: { title: '消息', requiresAuth: true },
      },
      {
        path: 'orders',
        name: 'Orders',
        component: () => import('@/views/user/Orders.vue'),
        meta: { title: '我的订单', requiresAuth: true },
      },
      {
        path: 'user',
        name: 'UserCenter',
        component: () => import('@/views/user/Profile.vue'),
        meta: { title: '个人中心', requiresAuth: true },
      },
      {
        path: 'users/:id',
        name: 'UserHome',
        component: () => import('@/views/user/UserHome.vue'),
        meta: { title: '用户主页' },
      },
      {
        path: 'announcements/:id',
        name: 'Announcement',
        component: () => import('@/views/user/Announcement.vue'),
        meta: { title: '公告详情' },
      },
      {
        path: 'orders/:id',
        name: 'OrderDetail',
        component: () => import('@/views/user/OrderDetail.vue'),
        meta: { title: '订单详情', requiresAuth: true },
      },
    ],
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { title: '注册' },
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      {
        path: '',
        redirect: '/admin/dashboard',
      },
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('@/views/admin/Dashboard.vue'),
        meta: { title: '管理后台', requiresAuth: true, requiresAdmin: true },
      },
      {
        path: 'audit',
        name: 'AdminAudit',
        component: () => import('@/views/admin/Audit.vue'),
        meta: { title: '商品审核', requiresAuth: true, requiresAdmin: true },
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/Users.vue'),
        meta: { title: '用户管理', requiresAuth: true, requiresAdmin: true },
      },
      {
        path: 'goods',
        name: 'AdminGoods',
        component: () => import('@/views/admin/Goods.vue'),
        meta: { title: '商品管理', requiresAuth: true, requiresAdmin: true },
      },
      {
        path: 'reports',
        name: 'AdminReports',
        component: () => import('@/views/admin/Reports.vue'),
        meta: { title: '举报处理', requiresAuth: true, requiresAdmin: true },
      },
      {
        path: 'announcements',
        name: 'AdminAnnouncements',
        component: () => import('@/views/admin/Announcements.vue'),
        meta: { title: '公告管理', requiresAuth: true, requiresAdmin: true },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  // motion.md §1：等离开动画（200ms）结束后再回到顶部
  scrollBehavior(to, from, saved) {
    if (to.path === from.path) return saved || false
    return new Promise((resolve) => {
      setTimeout(() => resolve(saved || { top: 0 }), 200)
    })
  },
})

router.beforeEach(async (to) => {
  const userStore = useUserStore()
  const title = to.meta.title ? `${to.meta.title} - 校园二手交易智能平台` : '校园二手交易智能平台'
  document.title = title

  const needAuth = to.matched.some((record) => record.meta.requiresAuth)
  const needAdmin = to.matched.some((record) => record.meta.requiresAdmin) || to.path.startsWith('/admin')

  if ((needAuth || needAdmin) && !userStore.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (userStore.token && !userStore.userInfo) {
    try {
      await userStore.fetchUserInfo()
    } catch {
      // 获取用户信息失败时由请求拦截器处理 401；此处不阻断公共页
      if (needAuth || needAdmin) {
        return { path: '/login', query: { redirect: to.fullPath } }
      }
    }
  }

  if (needAdmin && userStore.userInfo?.role !== Role.ADMIN) {
    return { path: '/' }
  }

  return true
})

export default router
