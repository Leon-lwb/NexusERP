import { to } from 'await-to-js';
import { getToken, removeToken, setToken } from '@/utils/auth';
import { login as loginApi, logout as logoutApi, getInfo as getUserInfo } from '@/api/login';
import { imCache } from '@/utils/im/im-cache';
import { LoginData } from '@/api/types';
import defAva from '@/assets/images/profile.jpg';
import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken());
  const name = ref('');
  const nickname = ref('');
  const userId = ref<string | number>('');
  const tenantId = ref<string>('');
  const avatar = ref('');
  const roles = ref<Array<string>>([]); // 用户角色编码集合 → 判断路由权限
  const permissions = ref<Array<string>>([]); // 用户权限编码集合 → 判断按钮权限

  /**
   * 登录
   * @param userInfo
   * @returns
   */
  const login = async (userInfo: LoginData): Promise<void> => {
    const [err, res] = await to(loginApi(userInfo));
    if (res) {
      const data = res.data;
      setToken(data.access_token);
      token.value = data.access_token;
      return Promise.resolve();
    }
    return Promise.reject(err);
  };

  // 获取用户信息
  const getInfo = async (): Promise<void> => {
    const [err, res] = await to(getUserInfo());
    if (res) {
      const data = res.data;
      const user = data.user;
      const profile = user.avatar == '' || user.avatar == null ? defAva : user.avatar;

      if (data.roles && data.roles.length > 0) {
        // 验证返回的roles是否是一个非空数组
        roles.value = data.roles;
        permissions.value = data.permissions;
      } else {
        roles.value = ['ROLE_DEFAULT'];
      }
      name.value = user.userName;
      nickname.value = user.nickName;
      avatar.value = profile;
      userId.value = user.userId;
      tenantId.value = user.tenantId;
      return Promise.resolve();
    }
    return Promise.reject(err);
  };

  // 注销
  const logout = async (): Promise<void> => {
    try {
      await logoutApi();
    } catch (e) {
      // 会话过期时 logout 接口本身也会 401，先本地清理再忽略接口失败，避免重新登录死循环
      console.error('logout api failed', e);
    }
    // 清空 IM 聊天缓存（IndexedDB + 内存），防止换用户后泄露上一用户私聊记录
    try {
      await imCache.clearAllCache();
    } catch (e) {
      console.error('clear im cache failed', e);
    }
    // 复位其它 store 的内存态：动态路由、标签页/keep-alive 缓存、字典、通知、IM 会话。
    // 用动态 import 打破 store 之间的静态循环依赖（im → user，permission → plugins/auth → user），
    // 这些模块此时都已加载，动态 import 不会产生额外网络请求。
    try {
      const [{ usePermissionStore }, { useTagsViewStore }, { useDictStore }, { useNoticeStore }, { useImStore }] = await Promise.all([
        import('@/store/modules/permission'),
        import('@/store/modules/tagsView'),
        import('@/store/modules/dict'),
        import('@/store/modules/notice'),
        import('@/store/modules/im')
      ]);
      // vue-router 的动态路由不会随 Pinia 状态清空而消失，必须显式 removeRoute，
      // 否则同一标签页换账号后，上一用户的路由仍可通过 URL 直接访问。
      usePermissionStore().resetRoutes();
      useTagsViewStore().resetViews();
      useDictStore().cleanDict();
      useNoticeStore().clearNotice();
      useNoticeStore().setUnreadCount(0);
      useImStore().reset();
    } catch (e) {
      console.error('reset stores on logout failed', e);
    }
    token.value = '';
    name.value = '';
    nickname.value = '';
    avatar.value = '';
    userId.value = '';
    tenantId.value = '';
    roles.value = [];
    permissions.value = [];
    removeToken();
  };

  const setAvatar = (value: string) => {
    avatar.value = value;
  };

  return {
    userId,
    tenantId,
    token,
    nickname,
    avatar,
    roles,
    permissions,
    login,
    getInfo,
    logout,
    setAvatar
  };
});
