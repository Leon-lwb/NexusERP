import { createWebHistory, createRouter, Router } from 'vue-router'
import { constantRoutes } from './constant-routes'
import { dynamicRoutes } from './dynamic-routes'

export type { RouteMeta, CustomRouteRecordRaw } from './constant-routes'

const router: Router = createRouter({
  history: createWebHistory(),
  routes: constantRoutes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    }
    return { top: 0 }
  },
})

export default router
export { constantRoutes, dynamicRoutes }
