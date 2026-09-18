import { to as tos } from 'await-to-js';
import router from './router';
import NProgress from 'nprogress';
import 'nprogress/nprogress.css';
import { getToken } from '@/utils/auth';
import { isHttp, isPathMatch } from '@/utils/validate';
import { isRelogin } from '@/utils/request';
import { useUserStore } from '@/store/modules/user';
import { useSettingsStore } from '@/store/modules/settings';
import { usePermissionStore } from '@/store/modules/permission';
import { ElMessage } from 'element-plus/es';

NProgress.configure({ showSpinner: false });
const whiteList = ['/login', '/register', '/social-callback', '/register*', '/register/*'];

const isWhiteList = (path: string) => {
  return whiteList.some((pattern) => isPathMatch(pattern, path));
};

router.beforeEach(async (to, from, next) => {
  NProgress.start();
  if (getToken()) {
    to.meta.title && useSettingsStore().setTitle(to.meta.title as string);
    /* has token*/
    if (to.path === '/login') {
      next({ path: '/' });
      NProgress.done();
    } else if (isWhiteList(to.path)) {
      next();
    } else {
      if (useUserStore().roles.length === 0) {
        isRelogin.show = true;
        // 判断当前用户是否已拉取完user_info信息
        const [err] = await tos(useUserStore().getInfo());
        if (err) {
          await useUserStore().logout();
          ElMessage.error(err);
          next({ path: '/' });
        } else {
          isRelogin.show = false;
          try {
            // 根据roles权限生成可访问的路由表
            const accessRoutes = await usePermissionStore().generateRoutes();
            accessRoutes.forEach((route) => {
              if (!isHttp(route.path)) {
                router.addRoute(route); // 动态添加可访问路由表
              }
            });
            // @ts-expect-error hack方法 确保addRoutes已完成
            next({ path: to.path, replace: true, params: to.params, query: to.query, hash: to.hash, name: to.name as string }); // hack方法 确保addRoutes已完成
          } catch (e: any) {
            // 生成路由失败（网络抖动 / 后端 500 / 网关超时）时必须收尾：
            // NProgress.start() 已执行，而 done() 只在 afterEach 调用，不处理会让进度条永久卡住、
            // 用户停在旧页面且没有任何提示。这里中止本次导航（不跳登录页 —— 有 token 时
            // /login 会重定向回 /，与守卫形成死循环），用户刷新即可重试。
            NProgress.done();
            console.error('generateRoutes failed', e);
            ElMessage.error(e?.message || '菜单加载失败，请刷新页面重试');
            next(false);
          }
        }
      } else {
        next();
      }
    }
  } else {
    // 没有token
    if (isWhiteList(to.path)) {
      // 在免登录白名单，直接进入
      next();
    } else {
      const redirect = encodeURIComponent(to.fullPath || '/');
      next(`/login?redirect=${redirect}`); // 否则全部重定向到登录页
      NProgress.done();
    }
  }
});

router.afterEach(() => {
  NProgress.done();
});
