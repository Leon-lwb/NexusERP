import { session } from './api.mjs';

/** 仅替换身份来源，组件和真实checkPermi仍使用同一响应式合成会话。 */
const user = {
  get userId() {
    return session.userId;
  },
  get tenantId() {
    return session.tenant;
  },
  get permissions() {
    return session.permissions;
  },
  roles: []
};

/** 不导入登录、Token、IM或真实后端配置。 */
export const useUserStore = () => user;
