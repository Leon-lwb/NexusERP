<template>
  <div class="navigation-harness">
    <nav aria-label="报表测试菜单">
      <RouterLink v-for="menu in navigationMenus" :key="menu.path" :to="'/report/' + menu.path" :data-menu="menu.path">
        {{ menu.title }}
      </RouterLink>
      <label><input v-model="theme.animationEnable" data-test="animation" type="checkbox" />随机动画</label>
    </nav>
    <RouterView />
  </div>
</template>
<script lang="ts">
import AppMain from '@/layout/components/AppMain.vue';
import { createCustomNameComponent } from '@/utils/createCustomNameComponent';

/** 使用正式菜单的组件加载器、布局过渡及缓存，接口仅连接合成数据。 */
export const navigationMenus = [
  { path: 'assets', title: '私有打印图片', name: 'ReportAssets', load: () => import('@/views/report/assets/index.vue') },
  { path: 'query', title: '受控查询', name: 'ReportQuery', load: () => import('@/views/report/query/index.vue') },
  { path: 'dashboard', title: '个人仪表盘', name: 'ReportDashboard', load: () => import('@/views/report/dashboard/index.vue') },
  { path: 'subscription', title: '报表订阅', name: 'ReportSubscription', load: () => import('@/views/report/subscription/index.vue') },
  { path: 'batch', title: '批量单据', name: 'ReportBatch', load: () => import('@/views/report/batch/index.vue') },
  { path: 'exports', title: '导出中心', name: 'ReportExports', load: () => import('@/views/report/exports/index.vue') }
];

/** 路由深度与业务布局一致，保留实际命名包装组件和按路径缓存。 */
export const navigationRoutes = [
  { path: '/', redirect: '/report/assets' },
  {
    path: '/report',
    component: AppMain,
    children: navigationMenus.map((menu) => ({
      path: menu.path,
      name: menu.name,
      component: createCustomNameComponent(menu.load, { name: menu.name })
    }))
  }
];
</script>
<script setup lang="ts">
import { watch } from 'vue';
import { useRoute } from 'vue-router';
import { useTagsViewStore } from '@/store/modules/tagsView';
import { useThemeStore } from '@/store/modules/theme';

const route = useRoute();
const tags = useTagsViewStore();
const theme = useThemeStore();
const cacheEnabled = new URLSearchParams(location.search).get('cache') !== 'off';
watch(
  () => route.fullPath,
  () => cacheEnabled && tags.addView(route),
  { immediate: true }
);
</script>
<style scoped>
.navigation-harness nav {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  padding: 16px;
}
</style>
