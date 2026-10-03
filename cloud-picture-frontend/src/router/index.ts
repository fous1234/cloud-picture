import { createRouter, createWebHistory } from 'vue-router'
import { getCurrentUser } from '../api/user'
import { setUnauthorizedHandler } from '../api/http'
import { clearSession, isAdmin, isLoggedIn, session, setUser } from '../stores/session'

declare module 'vue-router' {
  interface RouteMeta {
    /** 需要登录 */
    requiresAuth?: boolean
    /** 仅管理员 */
    requiresAdmin?: boolean
    /** 仅未登录可见（登录/注册） */
    guestOnly?: boolean
  }
}

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { guestOnly: true },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('../views/RegisterView.vue'),
    meta: { guestOnly: true },
  },
  {
    path: '/',
    component: () => import('../layouts/DefaultLayout.vue'),
    children: [
      { path: '', redirect: '/gallery' },
      {
        path: 'gallery',
        name: 'gallery',
        component: () => import('../views/GalleryView.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'share/:token',
        name: 'image-share',
        component: () => import('../views/SharedImageView.vue'),
      },
      {
        path: 'image/:id',
        name: 'image-detail',
        component: () => import('../views/ImageDetailView.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'my-images',
        name: 'my-images',
        component: () => import('../views/MyImagesView.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('../views/ProfileView.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'admin/images',
        name: 'admin-images',
        component: () => import('../views/AdminImagesView.vue'),
        meta: { requiresAuth: true, requiresAdmin: true },
      },
      {
        path: 'admin/users',
        name: 'admin-users',
        component: () => import('../views/AdminUsersView.vue'),
        meta: { requiresAuth: true, requiresAdmin: true },
      },
      { path: '403', name: 'forbidden', component: () => import('../views/ForbiddenView.vue') },
      {
        path: ':pathMatch(.*)*',
        name: 'not-found',
        component: () => import('../views/NotFoundView.vue'),
      },
    ],
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

setUnauthorizedHandler(() => {
  const current = router.currentRoute.value
  if (current.name === 'login') return
  router.replace({ name: 'login', query: { redirect: current.fullPath } })
})

router.beforeEach(async (to) => {
  if (!isLoggedIn()) {
    if (to.meta.requiresAuth) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }
    return true
  }

  // 刷新页面后只有 token 没有用户信息，先补一次 /api/user/me 才能判断角色
  if (!session.hydrated) {
    try {
      setUser(await getCurrentUser())
    } catch {
      // 401 已由 http 层清除会话并跳转登录；其余错误按未登录处理
      clearSession()
    }
    if (!isLoggedIn()) {
      if (to.meta.requiresAuth) {
        return { name: 'login', query: { redirect: to.fullPath } }
      }
      return true
    }
  }

  if (to.meta.guestOnly) return { name: 'gallery' }
  if (to.meta.requiresAdmin && !isAdmin()) return { name: 'forbidden' }
  return true
})
