import { createApp } from 'vue';
import { createRouter, createMemoryHistory } from 'vue-router';
import ElementPlus from 'element-plus';
import * as icons from '@element-plus/icons-vue';
import 'element-plus/dist/index.css';
import 'element-plus/theme-chalk/dark/css-vars.css';
import App from './App.vue';
import { session } from './api.mjs';

/** 合成页面仍执行真实权限语义，不能使用放行所有按钮的空指令。 */
const app = createApp(App);
/** 内存路由用于断言业务抽屉始终保留原页面。 */
const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/report/designer', component: () => import('@/views/report/designer/index.vue') },
    { path: '/:pathMatch(.*)*', component: { render: () => null } }
  ]
});
(window as any).__reportNavigation = [];
router.afterEach((route) => (window as any).__reportNavigation.push(route.fullPath));
app.use(router);
Object.entries(icons).forEach(([name, component]) => app.component(name, component));
app.use(ElementPlus).directive('hasPermi', {
  mounted(element: HTMLElement, binding: { value: string[] }) {
    if (!binding.value.some((permission) => session.permissions.includes(permission))) element.remove();
  }
});
app.config.errorHandler = (error) => {
  (window as any).__reportBrowserFailures.push('Vue异常：' + String(error));
};
app.mount('#app');
