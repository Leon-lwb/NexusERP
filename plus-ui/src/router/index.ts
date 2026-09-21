import { createWebHistory, createRouter, RouteRecordRaw } from 'vue-router';
/* Layout */
import Layout from '@/layout/index.vue';

/**
 * Note: 路由配置项
 *
 * hidden: true                     // 当设置 true 的时候该路由不会再侧边栏出现 如401，login等页面，或者如一些编辑页面/edit/1
 * alwaysShow: true                 // 当你一个路由下面的 children 声明的路由大于1个时，自动会变成嵌套的模式--如组件页面
 *                                  // 只有一个时，会将那个子路由当做根路由显示在侧边栏--如引导页面
 *                                  // 若你想不管路由下面的 children 声明的个数都显示你的根路由
 *                                  // 你可以设置 alwaysShow: true，这样它就会忽略之前定义的规则，一直显示根路由
 * redirect: noRedirect             // 当设置 noRedirect 的时候该路由在面包屑导航中不可被点击
 * name:'router-name'               // 设定路由的名字，一定要填写不然使用<keep-alive>时会出现各种问题
 * query: '{"id": 1, "name": "ry"}' // 访问路由的默认传递参数
 * roles: ['admin', 'common']       // 访问路由的角色权限
 * permissions: ['a:a:a', 'b:b:b']  // 访问路由的菜单权限
 * meta : {
    noCache: true                   // 如果设置为true，则不会被 <keep-alive> 缓存(默认 false)
    title: 'title'                  // 设置该路由在侧边栏和面包屑中展示的名字
    icon: 'svg-name'                // 设置该路由的图标，对应路径src/assets/icons/svg
    breadcrumb: false               // 如果设置为false，则不会在breadcrumb面包屑中显示
    activeMenu: '/system/user'      // 当路由设置了该属性，则会高亮相对应的侧边栏。
  }
 */

// 公共路由
export const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/redirect',
    component: Layout,
    hidden: true,
    children: [
      {
        path: '/redirect/:path(.*)',
        component: () => import('@/views/redirect/index.vue')
      }
    ]
  },
  {
    path: '/social-callback',
    hidden: true,
    component: () => import('@/layout/components/SocialCallback/index.vue')
  },
  {
    path: '/login',
    component: () => import('@/views/login.vue'),
    hidden: true
  },
  {
    path: '/register',
    component: () => import('@/views/register.vue'),
    hidden: true
  },
  {
    path: '/:pathMatch(.*)*',
    component: () => import('@/views/error/404.vue'),
    hidden: true
  },
  {
    path: '/401',
    component: () => import('@/views/error/401.vue'),
    hidden: true
  },
  {
    path: '',
    component: Layout,
    redirect: '/index',
    children: [
      {
        path: '/index',
        component: () => import('@/views/index.vue'),
        name: 'Index',
        meta: { title: '首页', icon: 'dashboard', affix: true }
      }
    ]
  },
  {
    path: '/user',
    component: Layout,
    hidden: true,
    redirect: 'noredirect',
    children: [
      {
        path: 'profile',
        component: () => import('@/views/system/user/profile/index.vue'),
        name: 'Profile',
        meta: { title: '个人中心', icon: 'user' }
      }
    ]
  }
];

// 动态路由，基于用户权限动态去加载
export const dynamicRoutes: RouteRecordRaw[] = [
  {
    path: '/im',
    component: Layout,
    hidden: true,
    permissions: ['im:chat:list'],
    children: [
      {
        path: 'chat',
        component: () => import('@/views/im/chat/index.vue'),
        name: 'ImChat',
        meta: { title: '即时通讯', icon: 'message' }
      }
    ]
  },
  {
    path: '/workflow/process/start/:deployId',
    component: Layout,
    hidden: true,
    permissions: ['workflow:process:start'],
    children: [
      {
        path: '',
        component: () => import('@/views/workflow/work/start.vue'),
        name: 'WorkStart',
        meta: { title: '发起流程', activeMenu: '/workflow/process/initiate' }
      }
    ]
  },
  {
    path: '/workflow/process/detail/:procInsId',
    component: Layout,
    hidden: true,
    permissions: ['workflow:process:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/workflow/work/detail.vue'),
        name: 'WorkDetail',
        meta: { title: '流程详情', activeMenu: '/work/todo' }
      }
    ]
  },
  {
    path: '/tool/gen-edit/index/:tableId',
    component: Layout,
    hidden: true,
    permissions: ['tool:gen:edit'],
    children: [
      {
        path: '',
        component: () => import('@/views/tool/gen/editTable.vue'),
        name: 'GenEdit',
        meta: { title: '编辑生成配置', activeMenu: '/tool/gen' }
      }
    ]
  },
  {
    path: '/tool/gen-design/index',
    component: Layout,
    hidden: true,
    permissions: ['tool:gen:edit'],
    children: [
      {
        path: '',
        component: () => import('@/views/tool/gen/online/design/index.vue'),
        name: 'GenDesign',
        meta: { title: '表单设计', activeMenu: '/tool/gen' }
      }
    ]
  },
  {
    path: '/tool/gen-test/index',
    component: Layout,
    hidden: true,
    permissions: ['tool:gen:preview'],
    children: [
      {
        path: '',
        component: () => import('@/views/tool/gen/online/test/index.vue'),
        name: 'GenTest',
        meta: { title: '测试表单', activeMenu: '/tool/gen' }
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
        component: () => import('@/views/system/autocode/part.vue'),
        name: 'AutoCodePart',
        meta: { title: '规则组成', activeMenu: '/system/autocode' }
      }
    ]
  },
  {
    path: '/erp/so/orderheader/form',
    component: Layout,
    hidden: true,
    permissions: ['so:orderheader:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/so/orderheader/form.vue'),
        name: 'OrderHeaderForm',
        meta: { title: '销售订单表单', activeMenu: '/erp/so/orderheader' }
      }
    ]
  },
  {
    path: '/erp/wm/arrivalnotice/form',
    component: Layout,
    hidden: true,
    permissions: ['wm:arrivalnotice:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/wm/arrivalnotice/form.vue'),
        name: 'ArrivalNoticeForm',
        meta: { title: '到货通知单表单', activeMenu: '/erp/inbound/arrivalnotice' }
      }
    ]
  },
  {
    path: '/erp/wm/receive/form',
    component: Layout,
    hidden: true,
    permissions: ['wm:receive:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/wm/receive/form.vue'),
        name: 'ReceiveForm',
        meta: { title: '物料入库单表单', activeMenu: '/erp/inbound/receive' }
      }
    ]
  },
  {
    path: '/erp/wm/sales-issue/form',
    component: Layout,
    hidden: true,
    permissions: ['wm:salesIssue:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/wm/sales-issue/form.vue'),
        name: 'SalesIssueForm',
        meta: { title: '销售出库单表单', activeMenu: '/erp/outbound/salesIssue' }
      }
    ]
  },
  {
    path: '/erp/bd/bom/form',
    component: Layout,
    hidden: true,
    permissions: ['bd:bom:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/bd/bom/form.vue'),
        name: 'BomForm',
        meta: { title: 'BOM表单', activeMenu: '/erp/tech/bom' }
      }
    ]
  },
  {
    path: '/erp/bd/routing/form',
    component: Layout,
    hidden: true,
    permissions: ['bd:routing:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/bd/routing/form.vue'),
        name: 'RoutingForm',
        meta: { title: '工艺路线表单', activeMenu: '/erp/tech/routing' }
      }
    ]
  },
  {
    path: '/erp/prd/requirement/form',
    component: Layout,
    hidden: true,
    permissions: ['prd:requirement:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/prd/requirement/form.vue'),
        name: 'RequirementForm',
        meta: { title: '物料需求表单', activeMenu: '/erp/prd/requirement' }
      }
    ]
  },
  {
    path: '/erp/prd/prdplan/form',
    component: Layout,
    hidden: true,
    permissions: ['prd:plan:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/prd/prdplan/form.vue'),
        name: 'PrdPlanForm',
        meta: { title: '生产计划表单', activeMenu: '/erp/prd/plan' }
      }
    ]
  },
  {
    path: '/erp/prd/prdwork/form',
    component: Layout,
    hidden: true,
    permissions: ['prd:work:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/prd/prdwork/form.vue'),
        name: 'PrdWorkForm',
        meta: { title: '生产工单表单', activeMenu: '/erp/prdExec/work' }
      }
    ]
  },
  {
    path: '/erp/wm/storagearea',
    component: Layout,
    hidden: true,
    permissions: ['wm:storagearea:list'],
    children: [
      {
        path: 'index',
        component: () => import('@/views/erp/wm/storagearea/index.vue'),
        name: 'Storagearea',
        meta: { title: '库区', activeMenu: '/erp/wm/warehouse' }
      }
    ]
  },
  {
    path: '/erp/wm/storagelocation',
    component: Layout,
    hidden: true,
    permissions: ['wm:storagelocation:list'],
    children: [
      {
        path: 'index',
        component: () => import('@/views/erp/wm/storagelocation/index.vue'),
        name: 'Storagelocation',
        meta: { title: '库位', activeMenu: '/erp/wm/storagearea' }
      }
    ]
  },
  {
    path: '/erp/wm/warehouse/3d/:warehouseId',
    component: Layout,
    hidden: true,
    permissions: ['wm:warehouse:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/wm/warehouse/3d.vue'),
        name: 'Warehouse3d',
        meta: { title: '3D仓库可视化', activeMenu: '/erp/wm/warehouse' }
      }
    ]
  },
  {
    path: '/erp/wm/transfer/form',
    component: Layout,
    hidden: true,
    permissions: ['wm:transfer:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/wm/transfer/form.vue'),
        name: 'TransferForm',
        meta: { title: '调拨单表单', activeMenu: '/erp/inventory/transfer' }
      }
    ]
  },
  {
    path: '/erp/wm/return/form',
    component: Layout,
    hidden: true,
    permissions: ['wm:materialReturn:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/wm/return/form.vue'),
        name: 'MaterialReturnForm',
        meta: { title: '退料单表单', activeMenu: '/erp/inventory/materialReturn' }
      }
    ]
  },
  {
    path: '/erp/wm/handover/form',
    component: Layout,
    hidden: true,
    permissions: ['wm:materialHandover:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/wm/handover/form.vue'),
        name: 'HandoverForm',
        meta: { title: '上料交接单表单', activeMenu: '/erp/inventory/materialHandover' }
      }
    ]
  },
  {
    path: '/erp/wm/stocktaking/form',
    component: Layout,
    hidden: true,
    permissions: ['wm:stocktaking:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/wm/stocktaking/form.vue'),
        name: 'StockTakingForm',
        meta: { title: '盘点计划表单', activeMenu: '/erp/inventory/stocktaking' }
      }
    ]
  },
  {
    path: '/erp/wm/barcode',
    component: Layout,
    hidden: true,
    permissions: ['wm:barcode:list'],
    children: [
      {
        path: 'index',
        component: () => import('@/views/erp/wm/barcode/index.vue'),
        name: 'Barcode',
        meta: { title: '条码管理', activeMenu: '/erp/barcode/index' }
      }
    ]
  },
  {
    path: '/erp/wm/barcode/config',
    component: Layout,
    hidden: true,
    permissions: ['wm:barcodeConfig:list'],
    children: [
      {
        path: 'index',
        component: () => import('@/views/erp/wm/barcode/config/index.vue'),
        name: 'BarcodeConfig',
        meta: { title: '条码配置', activeMenu: '/erp/barcode/config' }
      }
    ]
  },
  {
    path: '/erp/wm/miscissue/form',
    component: Layout,
    hidden: true,
    permissions: ['wm:miscissue:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/wm/miscissue/form.vue'),
        name: 'MiscIssueForm',
        meta: { title: '杂项出库表单', activeMenu: '/erp/outbound/miscissue' }
      }
    ]
  },
  {
    path: '/erp/wm/productionissue/form',
    component: Layout,
    hidden: true,
    permissions: ['wm:productionissue:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/wm/productionissue/form.vue'),
        name: 'ProductionIssueForm',
        meta: { title: '生产领料表单', activeMenu: '/erp/outbound/productionissue' }
      }
    ]
  },
  {
    path: '/erp/wm/miscrecpt/form',
    component: Layout,
    hidden: true,
    permissions: ['wm:miscrecpt:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/wm/miscrecpt/form.vue'),
        name: 'MiscRecptForm',
        meta: { title: '杂项入库表单', activeMenu: '/erp/inbound/miscrecpt' }
      }
    ]
  },
  {
    path: '/erp/so/quotation/form',
    component: Layout,
    hidden: true,
    permissions: ['so:quotation:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/so/quotation/form.vue'),
        name: 'QuotationForm',
        meta: { title: '报价单表单', activeMenu: '/erp/so/quotation' }
      }
    ]
  },
  {
    path: '/erp/so/return/form',
    component: Layout,
    hidden: true,
    permissions: ['so:return:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/so/return/form.vue'),
        name: 'ReturnForm',
        meta: { title: '退货单表单', activeMenu: '/erp/soBiz/return' }
      }
    ]
  },
  {
    path: '/erp/so/price-list/form',
    component: Layout,
    hidden: true,
    permissions: ['so:priceList:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/so/price-list/form.vue'),
        name: 'SoPriceListForm',
        meta: { title: '价格表表单', activeMenu: '/erp/so/price-list', noCache: true }
      }
    ]
  },
  {
    // 销售合同详情（含正文上传/签署/验签）：不出现在侧边栏，从合同列表「详情」进入；activeMenu 保持列表菜单高亮
    path: '/erp/so/contract/detail',
    component: Layout,
    hidden: true,
    permissions: ['so:contract:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/so/contract/detail.vue'),
        name: 'SoContractDetail',
        meta: { title: '销售合同详情', activeMenu: '/erp/soBiz/contract', noCache: true }
      }
    ]
  },
  {
    // 采购合同详情（含正文上传/签署/验签）：不出现在侧边栏，从合同列表「详情」进入；activeMenu 保持列表菜单高亮
    path: '/erp/pu/contract/detail',
    component: Layout,
    hidden: true,
    permissions: ['pu:contract:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/pu/contract/detail.vue'),
        name: 'PuContractDetail',
        meta: { title: '采购合同详情', activeMenu: '/erp/puBiz/contract', noCache: true }
      }
    ]
  },
  {
    path: '/erp/pu/requisition/form',
    component: Layout,
    hidden: true,
    permissions: ['pu:requisition:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/pu/requisition/form.vue'),
        name: 'PurchaseRequisitionForm',
        meta: { title: '采购申请表单', activeMenu: '/erp/pu/requisition' }
      }
    ]
  },
  {
    path: '/erp/pu/order/form',
    component: Layout,
    hidden: true,
    permissions: ['pu:order:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/pu/order/form.vue'),
        name: 'PurchaseOrderForm',
        meta: { title: '采购订单表单', activeMenu: '/erp/puExec/order' }
      }
    ]
  },
  {
    path: '/erp/pu/return/form',
    component: Layout,
    hidden: true,
    permissions: ['pu:return:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/pu/return/form.vue'),
        name: 'PurchaseReturnForm',
        meta: { title: '采购退货表单', activeMenu: '/erp/puExec/return' }
      }
    ]
  },
  {
    path: '/erp/pu/vendor-evaluation/form',
    component: Layout,
    hidden: true,
    permissions: ['pu:vendorEvaluation:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/pu/vendor-evaluation/form.vue'),
        name: 'VendorEvaluationForm',
        meta: { title: '供应商评估表单', activeMenu: '/erp/puBiz/vendorEvaluation' }
      }
    ]
  },
  {
    path: '/erp/wm/valuation/form',
    component: Layout,
    hidden: true,
    permissions: ['wm:valuation:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/wm/valuation/form.vue'),
        name: 'ValuationForm',
        meta: { title: '库存估值详情', activeMenu: '/erp/wmRule/valuation' }
      }
    ]
  },
  {
    path: '/erp/wm/sn/form',
    component: Layout,
    hidden: true,
    permissions: ['wm:sn:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/wm/sn/form.vue'),
        name: 'WmSnForm',
        meta: { title: '序列号表单', activeMenu: '/erp/barcode/sn', noCache: true }
      }
    ]
  },
  {
    path: '/erp/pu/vendor-evaluation/report',
    component: Layout,
    hidden: true,
    permissions: ['pu:vendorEvaluation:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/pu/vendor-evaluation/report.vue'),
        name: 'VendorEvaluationReport',
        meta: { title: '供应商评估报表', activeMenu: '/erp/puBiz/vendorEvaluation' }
      }
    ]
  },
  {
    path: '/erp/prd/work-order-resource',
    component: Layout,
    hidden: true,
    permissions: ['prd:workOrderResource:list'],
    children: [
      {
        path: 'index',
        component: () => import('@/views/erp/prd/work-order-resource/index.vue'),
        name: 'WorkOrderResource',
        meta: { title: '工单资源分配', activeMenu: '/erp/prdExec/workOrderResource' }
      }
    ]
  },
  {
    path: '/erp/prdSub/subcontract/form',
    component: Layout,
    hidden: true,
    permissions: ['prd:subcontract:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/prd/subcontract/form.vue'),
        name: 'PrdSubcontractForm',
        meta: { title: '委外订单表单', activeMenu: '/erp/prdSub/subcontract', noCache: true }
      }
    ]
  },
  {
    path: '/erp/wm/batch/form',
    component: Layout,
    hidden: true,
    permissions: ['wm:batch:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/erp/wm/batch/form.vue'),
        name: 'WmBatchForm',
        meta: { title: '批次表单', activeMenu: '/erp/barcode/batch', noCache: true },
        props: (route) => ({ batchId: route.query.batchId, isView: route.query.isView })
      }
    ]
  },
  // MES 制造执行系统
  {
    path: '/mes/pro/workorder/form',
    component: Layout,
    hidden: true,
    permissions: ['mes:pro:work:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/mes/pro/workorder/form.vue'),
        name: 'MesWorkOrderForm',
        meta: { title: 'MES工单表单', activeMenu: '/mes/wo/workorder' }
      }
    ]
  },
  {
    path: '/mes/pro/workorder/detail/:mesWoId',
    component: Layout,
    hidden: true,
    permissions: ['mes:pro:work:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/mes/pro/workorder/detail.vue'),
        name: 'MesWorkOrderDetail',
        meta: { title: 'MES工单详情', activeMenu: '/mes/wo/workorder' }
      }
    ]
  },
  {
    path: '/mes/pro/dispatch/detail/:dispatchId',
    component: Layout,
    hidden: true,
    permissions: ['mes:pro:dispatch:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/mes/pro/dispatch/detail.vue'),
        name: 'MesDispatchDetail',
        meta: { title: '派工详情', activeMenu: '/mes/pro/dispatch' }
      }
    ]
  },
  {
    path: '/mes/pro/flow-card/detail/:cardId',
    component: Layout,
    hidden: true,
    permissions: ['mes:pro:flowcard:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/mes/pro/flow-card/detail.vue'),
        name: 'MesFlowCardDetail',
        meta: { title: '流转卡详情', activeMenu: '/mes/wo/flow-card' }
      }
    ]
  },
  // IoT 物联网平台
  {
    path: '/iot/product/index',
    component: Layout,
    hidden: true,
    permissions: ['iot:product:list'],
    children: [
      {
        path: '',
        component: () => import('@/views/iot/product/index.vue'),
        name: 'IotProduct',
        meta: { title: '产品管理', activeMenu: '/iot/asset/product' }
      }
    ]
  },
  {
    path: '/iot/device/index',
    component: Layout,
    hidden: true,
    permissions: ['iot:device:list'],
    children: [
      {
        path: '',
        component: () => import('@/views/iot/device/index.vue'),
        name: 'IotDevice',
        meta: { title: '设备管理', activeMenu: '/iot/asset/device' }
      }
    ]
  },
  {
    path: '/iot/device/realtime/:deviceId',
    component: Layout,
    hidden: true,
    permissions: ['iot:device:query'],
    children: [
      {
        path: '',
        component: () => import('@/views/iot/device/realtime.vue'),
        name: 'IotDeviceRealtime',
        meta: { title: '设备实时数据', activeMenu: '/iot/asset/device' }
      }
    ]
  }
];

/**
 * 创建路由
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.VITE_APP_CONTEXT_PATH),
  routes: constantRoutes,
  // 刷新时，滚动条位置还原
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition;
    }
    return { top: 0 };
  }
});

export default router;
