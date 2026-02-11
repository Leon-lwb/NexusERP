import router, { dynamicRoutes } from '@/router'
import auth from '@/plugins/auth'
import { isHttp } from '@/utils/validate'
import { getRouters } from '@/api/menu'
import Layout from '@/layout/index'
import ParentView from '@/components/ParentView'
import InnerLink from '@/layout/components/InnerLink'

const modules = import.meta.glob('./../../views/**/*.vue')

interface PermissionState {
  routes: any[]
  addRoutes: any[]
  defaultRoutes: any[]
  topbarRouters: any[]
  sidebarRouters: any[]
  loaded: boolean
}

const usePermissionStore = defineStore(
  'permission',
  {
    state: (): PermissionState => ({
      routes: [],
      addRoutes: [],
      defaultRoutes: [],
      topbarRouters: [],
      sidebarRouters: [],
      loaded: false
    }),
    actions: {
      setRoutes(routes: any[]) {
        this.addRoutes = routes
        this.routes = (router as any).options.routes.concat(routes)
      },
      setDefaultRoutes(routes: any[]) {
        this.defaultRoutes = (router as any).options.routes.concat(routes)
      },
      setTopbarRoutes(routes: any[]) {
        this.topbarRouters = routes
      },
      setSidebarRouters(routes: any[]) {
        this.sidebarRouters = routes
      },
      generateRoutes(roles: string[]) {
        return new Promise(resolve => {
          getRouters().then((res: any) => {
            const sdata = JSON.parse(JSON.stringify(res.data))
            const rdata = JSON.parse(JSON.stringify(res.data))
            const defaultData = JSON.parse(JSON.stringify(res.data))
            const sidebarRoutes = filterAsyncRouter(sdata)
            const rewriteRoutes = filterAsyncRouter(rdata, false, true)
            const defaultRoutes = filterAsyncRouter(defaultData)
            const asyncRoutes = filterDynamicRoutes(dynamicRoutes)
            asyncRoutes.forEach((route: any) => { router.addRoute(route) })
            rewriteRoutes.forEach((route: any) => {
              const parentName = route.name
              const hasParent = parentName && (router as any).hasRoute && (router as any).hasRoute(parentName)
              if (!hasParent) {
                router.addRoute(route)
              }
              if (route.children && route.children.length) {
                const parentPath = route.path || ''
                const parentName = route.name || ''
                
                // RuoYi官方方案：正确处理嵌套路由，避免name冲突
                route.children.forEach((child: any) => {
                  const originalName = child.name
                  const childPath = child.path || ''
                  
                  // RuoYi官方方案：组合父路径生成唯一名称，避免name冲突
                  const uniqueName = `${parentPath}_${originalName}`.replace(/\//g, '_').replace(/^_/, '')
                  
                  // 构建正确的嵌套路由 - 这是RuoYi的标准做法
                  const childRoute = {
                    ...child,
                    name: uniqueName, // 使用组合名称确保唯一性
                    path: childPath // 保持原始路径（可能是相对或绝对路径）
                  }
                  
                  // 使用addRoute将子路由添加到父路由下 - RuoYi标准做法
                  try {
                    router.addRoute(parentName, childRoute)
                  } catch (error) {
                    console.error('[generateRoutes] 添加子路由失败:', error)
                  }
                })
              }
            })
            const flatten = (routes: any[], base: string = ''): any[] => {
              const res: any[] = []
              routes.forEach((r: any) => {
                const p = (typeof r.path === 'string' && r.path.startsWith('/')) ? r.path : (base ? base.replace(/\/$/, '') + '/' + (r.path || '') : (r.path || ''))
                if (r.children && r.children.length) {
                  res.push(...flatten(r.children, p))
                } else {
                  res.push({ ...r, path: p })
                }
              })
              return res
            }
            const flattenedSidebar = flatten(sidebarRoutes)
            flattenedSidebar.forEach((r: any) => {
              if (!isHttp(r.path)) {
                const exists = router.getRoutes().some(x => x.path === r.path)
                if (!exists) {
                  router.addRoute(r)
                }
              }
            })
            this.setRoutes(rewriteRoutes)
            this.setSidebarRouters((router as any).options.routes.concat(sidebarRoutes))
            this.setDefaultRoutes(sidebarRoutes)
            this.setTopbarRoutes(defaultRoutes)
            this.loaded = true
            
            // 清理调试日志 - 保持控制台干净
            const allRoutes = router.getRoutes()
            
            resolve(rewriteRoutes)
          }).catch((error: any) => {
            console.error('路由数据获取失败:', error)
            resolve([])
          })
        })
      }
    }
  })

function filterAsyncRouter(asyncRouterMap: any[], lastRouter = false, type = false) {
  return asyncRouterMap.filter((route: any) => {
    if (type && route.children) {
      route.children = filterChildren(route.children, route)
    }
    if (route.component) {
      if (route.component === 'Layout') {
        route.component = Layout
      } else if (route.component === 'ParentView') {
        route.component = ParentView
      } else if (route.component === 'InnerLink') {
        route.component = InnerLink
      } else {
        route.component = loadView(route.component)
      }
    }
    if (route.children != null && route.children && route.children.length) {
      route.children = filterAsyncRouter(route.children, route, type)
    } else {
      delete route['children']
      delete route['redirect']
    }
    return true
  })
}

function filterChildren(childrenMap: any[], lastRouter = false) {
  var children: any[] = []
  childrenMap.forEach((el: any) => {
    el.path = lastRouter ? lastRouter.path + '/' + el.path : el.path
    if (el.children && el.children.length && el.component === 'ParentView') {
      children = children.concat(filterChildren(el.children, el))
    } else {
      children.push(el)
    }
  })
  return children
}

export function filterDynamicRoutes(routes: any[]) {
  const res: any[] = []
  routes.forEach((route: any) => {
    if (route.permissions) {
      if (auth.hasPermiOr(route.permissions)) {
        res.push(route)
      }
    } else if (route.roles) {
      if (auth.hasRoleOr(route.roles)) {
        res.push(route)
      }
    }
  })
  return res
}

export const loadView = (view: string) => {
  let res: any
  for (const path in modules) {
    const dir = path.split('views/')[1].split('.vue')[0]
    if (dir === view) {
      res = () => (modules as any)[path]()
      break
    }
  }
  if (!res) {
    console.error('[loadView] 未找到视图:', view)
  }
  return res
}

export default usePermissionStore
