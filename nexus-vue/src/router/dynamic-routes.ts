import { RouteRecordRaw } from 'vue-router'
import Layout from '@/layout'
import type { CustomRouteRecordRaw } from './constant-routes'

// 动态路由，基于用户权限动态去加载
export const dynamicRoutes: CustomRouteRecordRaw[] = [
  {
    path: '/system/user-auth',
    component: Layout,
    hidden: true,
    permissions: ['system:user:edit'],
    children: [
      {
        path: 'role/:userId(\\d+)',
        component: () => import('@/views/system/user/authRole'),
        name: 'AuthRole',
        meta: { title: '分配角色', activeMenu: '/system/user' }
      }
    ]
  },
  {
    path: '/system/role-auth',
    component: Layout,
    hidden: true,
    permissions: ['system:role:edit'],
    children: [
      {
        path: 'user/:roleId(\\d+)',
        component: () => import('@/views/system/role/authUser'),
        name: 'AuthUser',
        meta: { title: '分配用户', activeMenu: '/system/role' }
      }
    ]
  },
  {
    path: '/system/dict-data',
    component: Layout,
    hidden: true,
    permissions: ['system:dict:list'],
    children: [
      {
        path: 'index/:dictId(\\d+)',
        component: () => import('@/views/system/dict/data'),
        name: 'Data',
        meta: { title: '字典数据', activeMenu: '/system/dict' }
      }
    ]
  },
  {
    path: '/system/autocodePart',
    component: Layout,
    hidden: true,
    permissions: ['system:autocode:part:list'],
    children: [
      {
        path: 'index/:ruleId(\\d+)',
        component: () => import('@/views/system/autocode/part'),
        name: 'AutoCode',
        meta: { title: '规则组成', activeMenu: '/system/autocode' }
      }
    ]
  },
  {
    path: '/monitor/job-log',
    component: Layout,
    hidden: true,
    permissions: ['monitor:job:list'],
    children: [
      {
        path: 'index/:jobId(\\d+)',
        component: () => import('@/views/monitor/job/log'),
        name: 'JobLog',
        meta: { title: '调度日志', activeMenu: '/monitor/job' }
      }
    ]
  },
  {
    path: '/tool/gen-edit',
    component: Layout,
    hidden: true,
    permissions: ['tool:gen:edit'],
    children: [
      {
        path: 'index/:tableId(\\d+)',
        component: () => import('@/views/tool/gen/editTable'),
        name: 'GenEdit',
        meta: { title: '修改生成配置', activeMenu: '/tool/gen' }
      }
    ]
  },
  {
    path: '/tool/gen-add-value',
    component: Layout,
    hidden: true,
    permissions: ['tool:gen:edit'],
    children: [
      {
        path: 'index/:tableId(\\d+)',
        component: () => import('@/views/tool/gen/genValue'),
        name: 'GenAddValue',
        meta: { title: '生成表数据', activeMenu: '/tool/gen' }
      }
    ]
  },
  {
    path: '/core/bd/bom/form',
    component: Layout,
    hidden: true,
    permissions: ['bd:bom:edit'],
    children: [
      {
        path: '',
        component: () => import('@/views/core/bd/bom/form'),
        name: 'BomForm',
        meta: { title: 'BOM表单', activeMenu: '/core/bd/bom' }
      }
    ]
  },
  {
    path: '/core/so/orderheader/form',
    component: Layout,
    hidden: true,
    permissions: ['so:orderheader:edit'],
    children: [
      {
        path: '',
        component: () => import('@/views/core/so/orderheader/form'),
        name: 'OrderForm',
        meta: { title: '销售表单', activeMenu: '/core/so/orderheader' }
      }
    ]
  },
  {
    path: '/core/prd/prdplan/form',
    component: Layout,
    hidden: true,
    permissions: ['prd:plan:edit'],
    children: [
      {
        path: '',
        component: () => import('@/views/core/prd/prdplan/form'),
        name: 'PlanForm',
        meta: { title: '生产计划表单', activeMenu: '/core/prd/prdplan' }
      }
    ]
  },
  {
    path: '/core/prd/requirement/form',
    component: Layout,
    hidden: true,
    permissions: ['prd:requirement:edit'],
    children: [
      {
        path: '',
        component: () => import('@/views/core/prd/requirement/form'),
        name: 'RequirementForm',
        meta: { title: '物料清单', activeMenu: '/core/prd/requirement' }
      }
    ]
  },
  {
    path: '/core/wm/storagearea',
    component: Layout,
    hidden: true,
    permissions: ['core:wm:storagearea:list'],
    children: [
      {
        path: 'index',
        component: () => import('@/views/core/wm/storagearea/index'),
        name: 'areaList',
        meta: { title: '库区设置', activeMenu: '/core/wm/storagearea' }
      }
    ]
  },
  {
    path: '/core/wm/storagelocation',
    component: Layout,
    hidden: true,
    permissions: ['core:wm:storagelocation:list'],
    children: [
      {
        path: 'index',
        component: () => import('@/views/core/wm/storagelocation/index'),
        name: 'locationList',
        meta: { title: '库位设置', activeMenu: '/core/wm/storagelocation' }
      }
    ]
  },
  {
    path: '/core/wm/arrivalnotice/form',
    component: Layout,
    hidden: true,
    permissions: ['core:wm:storagelocation:list'],
    children: [
      {
        path: '',
        component: () => import('@/views/core/wm/arrivalnotice/form'),
        name: 'ArrivalnoticeForm',
        meta: { title: '到货通知详情', activeMenu: '/core/wm/arrivalnotice' }
      }
    ]
  },
  {
    path: '/core/wm/receive/form',
    component: Layout,
    hidden: true,
    permissions: ['core:wm:receive:list'],
    children: [
      {
        path: '',
        component: () => import('@/views/core/wm/receive/form'),
        name: 'ReceiveForm',
        meta: { title: '采购入库详情', activeMenu: '/core/wm/receive' }
      }
    ]
  },
  {
    path: '/core/prd/prdwork/form',
    component: Layout,
    hidden: true,
    permissions: ['prd:work:edit'],
    children: [
      {
        path: '',
        component: () => import('@/views/core/prd/prdwork//form'),
        name: 'WorkForm',
        meta: { title: '生产表单', activeMenu: '/core/prd/prdwork' }
      }
    ]
  },
  {
    path: '/workflow/process',
    component: Layout,
    hidden: true,
    permissions: ['workflow:process:query'],
    children: [
      {
        path: 'start/:deployId([\\w|\\-]+)',
        component: () => import('@/views/workflow/work/start.vue'),
        name: 'WorkStart',
        meta: { title: '发起流程', activeMenu: '/workflow/process', icon: '' }
      },
      {
        path: 'detail/:procInsId([\\w|\\-]+)',
        component: () => import('@/views/workflow/work/detail.vue'),
        name: 'WorkDetail',
        meta: { title: '流程详情', activeMenu: '/work/own', icon: '' }
      }
    ]
  },
  {
    path: '/workflow/nform',
    component: Layout,
    hidden: true,
    permissions: ['workflow:nform:list'],
    children: [
      {
        path: 'test',
        component: () => import('@/views/workflow/nform/test.vue'),
        name: 'NformTest',
        meta: { title: '表单设计器测试', activeMenu: '/workflow/nform', icon: '' }
      }
    ]
  }
]
